@Library("satqe_pipeline_lib") _

import groovy.json.*

openShiftUtils.withNode(image: "$pipelineVars.ciRobotteloImage:${pipelineVars.robotteloImageTags.find{sat_version.startsWith(it.key)}.value}") {

    try {
        stage('Set Build Description') {
            currentBuild.description = "Trigger: " + params.results_job_name +
                    " Run: " + params.results_build_number +
                    " Build: " + params.sat_version + "-" + params.snap_version + (params.test_run_type ? " $params.test_run_type" : "")
        }

        stage('Copy Test Run Artifacts') {
            copyArtifacts(
                    projectName: "${params.results_job_name}",
                    selector: specific("${params.results_build_number}"),
                    filter : '*results.xml',
                    target: "${WORKSPACE}"
            )
            sh """
                cp ${WORKSPACE}/*results.xml \${ROBOTTELO_DIR}
            """
        }

        stage('Set Artifact Filename') {
            file_name = sh (
                    returnStdout: true,
                    script: """
                    find \${ROBOTTELO_DIR} -name *results.xml -printf "%f\n"
                """
            )
            println("Filename is ${file_name}")
        }

        stage('Test Result Upload') {
            // Set the ID for the polarion test run
            test_run_id = "Satellite ${sat_version}-${snap_version} rhel${rhel_version}" + (test_run_type ? " $test_run_type" : "")
            sh """
                cd \${ROBOTTELO_DIR}
                pip install Betelgeuse==1.8.0
                scripts/polarion-test-run-upload.sh \
                    -s ${params.polarion_url} \
                    -P ${pipelineVars.polarionProject} \
                    -u ${pipelineVars.polarionUser} \
                    -i '${test_run_id}' \
                    -r ${file_name}
            """
        }
    }
    catch (exc) {
        print "Pipeline failed with ${exc}"
        email_to = ['sat-qe-jenkins', 'satellite-lab-list']
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
