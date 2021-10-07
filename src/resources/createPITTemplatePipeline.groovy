@Library("satqe_pipeline_lib") _

import groovy.json.*

def at_vars = [
    containerEnvVar(key: 'BROKER_AnsibleTower__base_url', value: "${params.tower_url}"),
]

openShiftUtils.withNode(image: pipelineVars.ciBrokerImage, envVars: at_vars) {
    stage('Set Build Description') {
        currentBuild.description = "Create PIT template: ${params.rhel_nvr}"
    }

    stage('Create Satlab VM Template ') {
        output_template_jenkins =
            sh (
        returnStdout: true,
        script:
            """
            broker execute --workflow 'create-rhel-cmp-template-rhv' \
                --output-format raw --artifacts last --additional-arg True \
                --qcow_url ${params.qcow_url} \
                --rhel_compose_id ${params.rhel_nvr}
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
            subject = "[PIT] Satlab OS template for ${params.rhel_nvr} was successfully created "
            body = "Template info:" +
                   "<br><br> sat_jenkins_template: ${sat_jenkins_template} " +
                   "<br><br> VM template name: ${params.rhel_nvr} " +
                   "<br><br> RHEL repository: ${params.rhel_os_repo} " +
                   "<br><br> source qcow2 image url: ${params.qcow_url}"
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
