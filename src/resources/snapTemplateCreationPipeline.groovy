@Library("satqe_pipeline_lib") _

import groovy.json.*

withCredentials([usernamePassword(credentialsId: 'ansible-tower-jenkins-user', passwordVariable: 'USERPASS', usernameVariable: 'USERNAME')]) {
    def at_vars = [
        containerEnvVar(key: 'DYNACONF_AnsibleTower__base_url', value: "${params.tower_url}"),
        containerEnvVar(key: 'DYNACONF_AnsibleTower__username', value: "${USERNAME}"),
        containerEnvVar(key: 'DYNACONF_AnsibleTower__password', value: "${USERPASS}")
    ]

    openShiftUtils.withNode(image: pipelineVars.ciBrokerImage, envVars: at_vars) {

        stage('Parse UMB Message') {
            println("CI Event Received, parsing message")

            // Read CI message from build environment
            def message = readJSON text: params.get("CI_MESSAGE")

            println("CI Message: " + JsonOutput.prettyPrint(JsonOutput.toJson(message)));

            // Write CI message to file to be archived
            writeJSON file: "ci-message.json", json: message

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

            // Check for any value not set
            if (sat_version && snap_version && capsule_version && rhel_major_version && satellite_activation_key && capsule_activation_key) {
                print "All Work flow values have been set"
            } else {
                error("One or more variables were empty")
            }

        }

        stage('Create Snap Templates ') {

            // Call all three AT snap template workflows in parallel
            parallel (
                "create-sat-jenkins-template" : {
                    output_sat_jenkins =
                        sh (
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
                "create-sat-lite-template" : {
                    output_sat_lite =
                        sh (
                            returnStdout: true,
                            script:
                                """
                                    broker execute --workflow 'create-sat-lite-template' \
                                    --output-format raw --artifacts last --additional-arg True \
                                    --activation_key ${satellite_activation_key}\
                                    --rhel_major_version ${rhel_major_version} \
                                    --sat_version ${sat_version} \
                                    --snap_version ${snap_version}
                                """
                        )
                },
                "create-capsule-template" : {
                    output_capsule =
                        sh (
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

        stage('Parse Output and Set Template Names'){
                
            // Print raw output, small JSON so not pretty printing
            println("create-sat-jenkins-template output: " + output_sat_jenkins)
            println("create-sat-lite-template output: " + output_sat_lite)
            println("create-capsule-template output: " + output_capsule)

            // Output to JSON files
            // Single quotes in the broker console output, replace with double quote
            sat_jenkins_template = null
            sat_lite_template = null
            capsule_template = null
            
            if (output_sat_jenkins.contains("data_out")) {
                output_sat_json = readJSON text: output_sat_jenkins.replace("'","\"")
                writeJSON file: "sat-jenkins-temp-creation.json", json: output_sat_json['data_out']
                sat_jenkins_template = output_sat_json.get('data_out', '').get('template', '')
                println("Sat Jenkins Template Name is " + sat_jenkins_template)
            } else {
                println("ERROR: broker call for sat-jenkins workflow must have failed, nothing was returned")
            }
            
            
            if (output_sat_lite.contains("data_out")) {
                output_lite_json = readJSON text: output_sat_lite.replace("'","\"")
                writeJSON file: "sat-lite-temp-creation.json", json: output_lite_json['data_out']
                sat_lite_template = output_lite_json.get('data_out', '').get('template', '')
                println("Sat Lite Template Name is " + sat_lite_template)
            } else {
                println("ERROR: broker call for sat-lite workflow must have failed, nothing was returned")
            }
            
            
            if (output_capsule.contains("data_out")) {
                output_capsule_json = readJSON text: output_capsule.replace("'","\"")
                writeJSON file: "capsule-temp-creation.json", json: output_capsule_json['data_out']
                capsule_template = output_capsule_json.get('data_out', '').get('template', '')
                println("Sat Capsule Template Name is " + capsule_template)
            } else {
                println("ERROR: broker call for capsule workflow must have failed, nothing was returned")
            }
       
        }

        stage('Archive Artifacts'){
            archiveArtifacts artifacts: '*.json'
            
            // Check for any value not set
            if (sat_jenkins_template && sat_lite_template && capsule_template) {
                print "All template names have been created"
            } else {
                error("One or more template names were empty")
            }   
        }
    }
}
