// CallableObject implementing call method to be able to perform implicit call "Object()"
def call(Map parameters = [:]) {

    def sat_version = parameters.get('sat_version')
    def snap_version = parameters.get('snap_version')
    def at_vars = parameters.get('at_vars')
    def errorCaught = false

    openShiftUtils.withNode(image: pipelineVars.ciRobotteloImage, envVars: at_vars) {
        try {
            stage('Check Out Satellite Instances') {
                inventory = brokerUtils.checkout(
                    'deploy-sat-jenkins':[ 'sat_version': sat_version, 'snap_version': snap_version, 'count': 1 ],
                )
            }

            stage('Snap Template Sanity Check') {
                label = 'sat-jenkins-sanitycheck'
                robotteloUtils.execute(inventory: inventory, script: """
                    py.test -v \
                    -m 'build_sanity' \
                    --junit-xml=${label}-results.xml \
                    -o junit_suite_name=${label} \
                    tests/foreman/
                """)
                junit "${label}-results.xml"
            }

        }
        catch (error) {
            echo error.getMessage()
            errorCaught = true
        }
        finally {
            if(inventory) {
                stage('Check In Satellite Instances') {
                    brokerUtils.checkin_all()
                }
            }
        }
    }

    return !errorCaught
}

return this;