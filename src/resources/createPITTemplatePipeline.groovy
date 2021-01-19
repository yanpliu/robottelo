@Library("satqe_pipeline_lib") _

import groovy.json.*

def at_vars = [
    containerEnvVar(key: 'BROKER_AnsibleTower__base_url', value: "${params.tower_url}"),
]

openShiftUtils.withNode(image: pipelineVars.ciBrokerImage, envVars: at_vars) {
    stage('Parse UMB Message') {
        println("CI Event Received, parsing message")

        // Read CI message from build environment
        def message = readJSON text: params.get("CI_MESSAGE")

        println("CI Message: " + JsonOutput.prettyPrint(JsonOutput.toJson(message)));

        // Write CI message to file to be archived
        //writeJSON file: "ci-message.json", json: message

    // PIT-specific vars
    def rhel_nvr
    def rhel_ver
    def rhel_os_repo
        def sat_ver
    def scenario
    for (product in message['artifact']['products']) {
        if (product['name'].toLowerCase() == 'satellite') {
                sat_ver = product['version']
                scenario = product['subproduct']
        }
        if (product['name'].toLowerCase() == 'rhel') {
                rhel_nvr = product['nvr'] // nvr = name-version-release string
                rhel_ver = product['version']
            for (repo in product['repos']) {
            if repo['name'].contains('base') {
                    rhel_os_repo = repo['base_url']
            }
                }
            }
    }

        println("rhel_ver is " + rhel_ver)
        println("rhel_nvr is " + rhel_nvr)
        println("rhel_os_repo is " + rhel_os_repo)
        println("sat_ver is " + sat_ver)
        println("scenario is " + scenario)

        // Check for any value not set
        if (rhel_ver && rhel_nvr && rhel_os_repo && sat_ver && scenario) {
            print "All Work flow values have been set"
        } else {
            error("One or more variables were empty")
        }

    // PIT parse QCOW image url
    def rhel_images_repo = rhel_os_repo.replaceFirst('/os[/]?$', '/images')
    index = new URL(rhel_images_repo).text
    def (_, qcow_file) = (index =~ />([-_\w\\\.]+\.qcow2)</)[0]
    def qcow_url = rhel_images_repo + '/' + qcow_file
    }

stage('Set Build Description') {
    currentBuild.description = "Create PIT template: ${rhel_nvr}"
}

    stage('Create Satlab VM Template ') {
        output_template_jenkins =
            sh (
        returnStdout: true,
        script:
            """
                broker execute --workflow 'create-pit-template' \
                --output-format raw --artifacts last --additional-arg True \
                --qcow_url ${qcow_url}
            --name ${rhel_nvr}
            """
    )
    }

    stage('Parse Output and Set Template Names'){
        // Print raw output, small JSON so not pretty printing
        println("create-rhel-template output: " + output_template_jenkins)

        // Output to JSON files
        // Single quotes in the broker console output, replace with double quote
        sat_jenkins_template = null

        if (output_template_jenkins.contains("data_out")) {
            output_template_json = readJSON text: output_template_jenkins.replace("'","\"")
            //writeJSON file: "sat-jenkins-temp-creation.json", json: output_template_json['data_out']
            sat_jenkins_template = output_template_json.get('data_out', '').get('template', '')
            println("Sat Jenkins Template Name is " + sat_jenkins_template)
        } else {
            println("ERROR: broker call for sat-jenkins workflow must have failed, nothing was returned")
        }
    }

    stage('Archive Artifacts'){
        //archiveArtifacts artifacts: '*.json'
        // Check for any value not set
        if (sat_jenkins_template) {
            print "All template names have been created"
            subject = "[PIT] Satlab OS template for ${rhel_nvr} was successfully created "
            body = "Template info:" +
                   "<br><br> sat_jenkins_template: ${sat_jenkins_template} " +
                   "<br><br> VM template name: ${rhel_nvr} " +
                   "<br><br> RHEL repository: ${rhel_os_repo} " +
                   "<br><br> source qcow2 image url: ${qcow_url}"
        } else {
            subject = "${env.JOB_NAME} Build ${BUILD_NUMBER} has Failed. Please Investigate"
            body = "Jenkins Console Log: ${BUILD_URL}"
            println("One or more template names were empty")
            currentBuild.result = 'UNSTABLE'
        }
    }

    stage('Build Notification') {
        email_to = ['sat-qe-jenkins']
        emailUtils.sendEmail(
            'to_nicks': email_to,
            'reply_nicks': email_to,
            'subject': subject,
            'body': body
        )
    }
}
