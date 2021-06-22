@Library("satqe_pipeline_lib") _

def node_vars = [
    containerEnvVar(key: 'MANIFEST_SERVER_HOSTNAME', value: "sesame.lab.eng.rdu2.redhat.com"),
    containerEnvVar(key: 'SM_URL', value: "https://subscription.rhsm.redhat.com"),
    containerEnvVar(key: 'CONSUMER', value: "052277c1-82f6-4adb-a7f4-d56a35e0d8c7"),
    containerEnvVar(key: 'RHN_USERNAME', value: "rhsatqe"),
]

try {
    openShiftUtils.withNode(image: pipelineVars.ciRobotteloImage, envVars: node_vars) {
        stage('Fabric Setup'){
            sh """
                echo \"\$(ssh-agent -s)\" >> ~/.bashrc
                source ~/.bashrc
                ssh-add - <<< \$SATLAB_PRIVATE_KEY
                echo \"from automation_tools.manifest import relink_manifest\" > ~/at.py
            """
        }
        stage('Manifest Download'){
            sh """
                cd \${ROBOTTELO_DIR}
                source ~/.bashrc
                fab -f ~/at.py -D -u root -H \$MANIFEST_SERVER_HOSTNAME relink_manifest:url=\$SM_URL,consumer=\$CONSUMER,user=\$RHN_USERNAME,password=\$ROBOTTELO_subscription__rhn_password,exp_subs_file=conf/robottelo-manifest-content.conf
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
            emailUtils.sendEmail(
                'to_nicks': ['sat-qe-jenkins'],
                'reply_nicks': ['sat-qe-jenkins'],
                'subject': "${env.JOB_NAME} Build ${BUILD_NUMBER} has Failed. Please Investigate",
                'body': "Jenkins Console Log:<br>${BUILD_URL}console<br><br>Error that was caught:<br>${err}"
            )
        }
    }
}
