@Library("satqe_pipeline_lib") _

import groovy.json.*


openShiftUtils.withNode(image: pipelineVars.ciCleanScriptImage) {

    try {
        stage('Cleanup Azure Resources') {

            sh """
                cd \${CLEANER_DIR}
                python cleanup.py -d azure --all
                python cleanup.py azure --all
                cp cleanup.log ${WORKSPACE}
            """

            archiveArtifacts artifacts: 'cleanup.log'
        }

        stage('Cleanup GCE Resources') {

            sh """
                cd \${CLEANER_DIR}
                python cleanup.py -d gce --all
                python cleanup.py gce --all
                cp cleanup.log ${WORKSPACE}
            """

            archiveArtifacts artifacts: 'cleanup.log'
        }
    }

    catch (exc) {
        print "Pipeline failed with ${exc}"
        email_to = ['sat-qe-jenkins']
        subject = "${env.JOB_NAME} Build ${BUILD_NUMBER} has Failed. Please Investigate"
        body = "Jenkins Console Log: ${BUILD_URL}. Error that was caught:<br><br> ${exc}"
        currentBuild.result = 'FAILURE'
    }

    finally {
        if(currentBuild.result == 'FAILURE') {
            emailUtils.sendEmail(
                'to_nicks': email_to,
                'reply_nicks': email_to,
                'subject': subject,
                'body': body
            )
        }
    }
}
