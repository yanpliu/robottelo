// CallableObject implementing call method to be able to perform implicit call "Object()"
def call(Map parameters = [:]) {

    def sat_version = parameters.get('sat_version')
    def snap_version = parameters.get('snap_version')
    def at_vars = parameters.get('at_vars')

    openShiftUtils.withNode(image: pipelineVars.ciRobotteloImage, envVars: at_vars) {

        stage('Check Out Satellite Instances') {
            inventory = brokerUtils.checkout(
                'deploy-sat-jenkins':[ 'sat_version': sat_version, 'snap_version': snap_version, 'count': 1 ],
            )
        }

        stage('Snap Template Sanity Check') {
            sh """
                cd /opt/app-root/src/robottelo
                git log -3
                crudini --set robottelo.properties server hostname ${inventory[0].hostname}

                py.test -v -m 'tier1' \
                    --junit-xml=sat-jenkins-sanitycheck.xml -o junit_suite_name=sat-jenkins-sanitycheck \
                    tests/foreman/cli/test_ping.py \
                    tests/foreman/endtoend/test_cli_endtoend.py \
                    tests/foreman/endtoend/test_api_endtoend.py

                cp robottelo*.log sat-jenkins-sanitycheck.xml ${WORKSPACE}
            """
            archiveArtifacts artifacts: 'robottelo*.log, sat-jenkins-sanitycheck.xml'
            junit 'sat-jenkins-sanitycheck.xml'
        }

        stage('Check In Satellite Instances') {
            brokerUtils.checkin_all()
        }

    }

}

return this;