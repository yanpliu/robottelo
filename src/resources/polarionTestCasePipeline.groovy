@Library("satqe_pipeline_lib") _

import groovy.json.*

withCredentials([usernamePassword(credentialsId: 'polarion-user', passwordVariable: 'USERPASS', usernameVariable: 'USERNAME')]) {
    def node_vars = [
        containerEnvVar(key: 'POLARION_USERNAME', value: "${USERNAME}"),
        containerEnvVar(key: 'POLARION_PASSWORD', value: "${USERPASS}")
    ]

    try {
        openShiftUtils.withNode(image: pipelineVars.ciRobotteloImage, envVars: node_vars) {
            stage('Polarion Test Case Upload'){
                sh """
                    cd \${ROBOTTELO_DIR}
                    git log -1
                    pip install Betelgeuse==1.8.0
                    scripts/polarion-test-case-upload.sh \
                        -s ${params.polarion_url} \
                        -P ${pipelineVars.polarionProject}
                """
            }
        }
    } catch (exc) {
        err = exc
        echo "Caught Error:\n${err}"
        currentBuild.result = 'FAILURE'
    } finally {
        stage('Build Failure Notification') {
            if (currentBuild.result == 'FAILURE') {
                email_to = ['sat-qe-jenkins']
                subject = "${env.JOB_NAME} Build ${BUILD_NUMBER} has Failed. Please Investigate"
                body = "Jenkins Console Log:<br>${BUILD_URL}console<br><br>Error that was caught:<br>${err}"
                emailUtils.sendEmail(
                    'to_nicks': email_to,
                    'reply_nicks': email_to,
                    'subject': subject,
                    'body': body
                )
            }
        }
    }
}
