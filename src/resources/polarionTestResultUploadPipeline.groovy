@Library("satqe_pipeline_lib") _

import groovy.json.*

openShiftUtils.withNode(image: pipelineVars.ciRobotteloImage) {

    stage('Set Build Description') {
        currentBuild.description = "Trigger: " + params.job_name +
                " Run: " + params.build_number +
                " Build: " + params.sat_version + "-" + params.snap_version

    }

    stage('Copy Test Run Artifacts') {
        copyArtifacts(
                projectName: "${params.job_name}",
                selector: specific("${params.build_number}"),
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
        test_run_id = "Satellite ${sat_version}-${snap_version} rhel${rhel_version}"
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