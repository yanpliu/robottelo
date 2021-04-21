@Library("satqe_pipeline_lib") _

import groovy.json.*

node('master') {
    // see more at https://www.jenkins.io/doc/pipeline/examples/#load-from-file
    // with out explicit scm checkout the files are present only on master node
    snapTemplateSanityCheck = load("${WORKSPACE}@script/src/resources/snapTemplateSanityCheck.groovy")
}

def at_vars = [
    containerEnvVar(key: 'BROKER_AnsibleTower__base_url', value: "${params.tower_url}"),
]
try {
    openShiftUtils.withNode(image: pipelineVars.ciBrokerImage, envVars: at_vars) {

        stage('Parse UMB Message') {
            println("CI Event Received, parsing message")

            // Read CI message from build environment
            def message = readJSON text: params.get("CI_MESSAGE")

            println("CI Message: " + JsonOutput.prettyPrint(JsonOutput.toJson(message)));

            // Write CI message to file to be archived
            //writeJSON file: "ci-message.json", json: message

            sat_version = message.get('satellite_version', '')
            capsule_version = sat_version
            snap_version = message.get('snap_version', '')
            rhel_major_version = message.get('rhel_major_version', '')
            satellite_activation_key = message.get('satellite_activation_key', '')
            capsule_activation_key = message.get('capsule_activation_key', '')

            println("sat_version is " + sat_version)
            println("snap_version is " + snap_version)
            println("rhel_major_version is " + rhel_major_version)
            println("capsule_version is " + capsule_version)
            println("satellite_activation_key " + satellite_activation_key)
            println("capsule_activation_key is " + capsule_activation_key)

            // Set description like '6.9.0 snap: 2.0 on RHEL 7'
            currentBuild.description = sat_version + " snap: " + snap_version + " on RHEL " + rhel_major_version

            // Check for any value not set
            if (sat_version && snap_version && capsule_version && rhel_major_version && satellite_activation_key && capsule_activation_key) {
                print "All Work flow values have been set"
            } else {
                error("One or more variables were empty")
            }

        }

        stage('Create Snap Templates ') {

            // Call all three AT snap template workflows in parallel
            parallel(
                    "create-sat-jenkins-template": {
                        output_sat_jenkins =
                                sh(
                                        returnStdout: true,
                                        script:
                                                """
                                broker execute --workflow 'create-sat-jenkins-template' \
                                --output-format raw --artifacts last --additional-arg True \
                                --activation_key ${satellite_activation_key}\
                                --rhel_major_version ${rhel_major_version} \
                                --sat_version ${sat_version} \
                                --snap_version ${snap_version}
                            """
                                )
                    },
                    "create-capsule-template": {
                        output_capsule =
                                sh(
                                        returnStdout: true,
                                        script:
                                                """
                            broker execute --workflow 'create-sat-capsule-template' \
                            --output-format raw --artifacts last --additional-arg True \
                            --activation_key ${capsule_activation_key}\
                            --rhel_major_version ${rhel_major_version} \
                            --sat_version ${sat_version} \
                            --snap_version ${snap_version}
                        """
                                )
                    },
            )
        }

        stage('Parse Output and Set Template Names') {

            // Print raw output, small JSON so not pretty printing
            println("create-sat-jenkins-template output: " + output_sat_jenkins)
            println("create-capsule-template output: " + output_capsule)

            // Output to JSON files
            // Single quotes in the broker console output, replace with double quote
            sat_jenkins_template = null
            capsule_template = null

            if (output_sat_jenkins.contains("data_out")) {
                output_sat_json = readJSON text: output_sat_jenkins.replace("'", "\"")
                //writeJSON file: "sat-jenkins-temp-creation.json", json: output_sat_json['data_out']
                sat_jenkins_template = output_sat_json.get('data_out', '').get('template', '')
                println("Sat Jenkins Template Name is " + sat_jenkins_template)
            } else {
                println("ERROR: broker call for sat-jenkins workflow must have failed, nothing was returned")
            }

            if (output_capsule.contains("data_out")) {
                output_capsule_json = readJSON text: output_capsule.replace("'", "\"")
                //writeJSON file: "capsule-temp-creation.json", json: output_capsule_json['data_out']
                capsule_template = output_capsule_json.get('data_out', '').get('template', '')
                println("Sat Capsule Template Name is " + capsule_template)
            } else {
                println("ERROR: broker call for capsule workflow must have failed, nothing was returned")
            }

        }

        sanityPassed = snapTemplateSanityCheck(
                'sat_version': sat_version,
                'snap_version': snap_version,
                'at_vars': at_vars,
        )

        stage('Archive Artifacts') {
            //archiveArtifacts artifacts: '*.json'
            // Check for any value not set
            template_exists = sat_jenkins_template && capsule_template
            if (template_exists) {
                print "All template names have been created"
                email_to = ['sat-qe-jenkins', 'satellite-qe-tower-users']
                subject = "Templates for ${sat_version} SNAP ${snap_version} are available"
                body = "Following snap ${snap_version} templates have been created:" +
                        " <br><br> sat_jenkins_template: ${sat_jenkins_template} " +
                        "<br><br> capsule_template: ${capsule_template}"
                if (!sanityPassed) {
                    body += "<br><br>However, template sanity check has failed. Please investigate!"
                }
            } else {
                email_to = ['sat-qe-jenkins', 'satellite-lab-list']
                subject = "${env.JOB_NAME} Build ${BUILD_NUMBER} has Failed. Please Investigate"
                body = "Jenkins Console Log: ${BUILD_URL}"
                println("One or more template names were empty")
                currentBuild.result = 'UNSTABLE'
            }
        }

        stage('Trigger Automation Test') {
            if (template_exists){
                build job: "${sat_version.tokenize('.').take(2).join('.')}-automation-trigger",
                        parameters: [
                                [$class: 'StringParameterValue', name: 'snap_version', value: snap_version],
                                [$class: 'StringParameterValue', name: 'sat_version', value: sat_version],
                        ],
                        wait: false
            } else {
                println("Template creation failed, skipping triggering automation job")
            }
        }
    }
} catch (exc) {
    print "Pipeline failed with ${exc}"
    email_to = ['sat-qe-jenkins', 'satellite-lab-list']
    subject = "${env.JOB_NAME} Build ${BUILD_NUMBER} has Failed. Please Investigate"
    body = "Jenkins Console Log: ${BUILD_URL}. Error that was caught:" +
            "<br><br> ${exc}"
    currentBuild.result = 'FAILURE'
} finally {
    stage('Build Notification') {
            emailUtils.sendEmail(
                    'to_nicks': email_to,
                    'reply_nicks': email_to,
                    'subject': subject,
                    'body': body
            )
    }
}
