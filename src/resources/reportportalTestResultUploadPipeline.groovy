@Library("satqe_pipeline_lib") _

import groovy.json.*

openShiftUtils.withNode(image: "$pipelineVars.ciRPToolsImage") {

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
        }

        stage('Set Artifact Filename') {
            file_name = sh (
                    returnStdout: true,
                    script: """
                    find ${WORKSPACE} -name *results.xml -printf "%f\n"
                """
            )
            println("XML artifacts to be processed: ${file_name}")
        }

        stage('Test Result Upload') {
            // Set the ID for the polarion test run
            sh """
               cd \${RP_TOOLS_DIR}
               scripts/reportportal_cli/rp_cli.py \
               --xunit_feed "${WORKSPACE}/*results.xml" \
	       --rp_uuid \${RP_API_TOKEN} \
               --config scripts/reportportal_cli/rp_conf.yaml \
               --launch_name \"${params.rp_launch_name}\" \
               --launch_description \"${params.rp_launch_description}\" \
               --launch_attrs \"${params.rp_launch_attrs}\" \
            """ + (params.rp_rerun_of ? "--rerun_of ${params.rp_rerun_of}" : "")
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
