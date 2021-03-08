@Library("satqe_pipeline_lib") _

import groovy.json.*


openShiftUtils.withNode(image: pipelineVars.ciCleanScriptImage) {

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
