@Library("satqe_pipeline_lib") _

import groovy.json.*

try {
    openShiftUtils.withNode(image: pipelineVars.ciRobotteloImage) {
        stage('Polarion Test Case Upload'){
            sh """
                cd \${ROBOTTELO_DIR}
                git log -1
                pip install Betelgeuse==1.8.0
                scripts/polarion-test-case-upload.sh \
                    -s ${params.polarion_url} \
                    -P ${pipelineVars.polarionProject} \
                    -u ${pipelineVars.polarionUser}
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
