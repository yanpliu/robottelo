// CallableObject implementing call method to be able to perform implicit call "Object()"
def call(Map parameters = [:]) {

    def sat_version = parameters.get('sat_version')
    def snap_version = parameters.get('snap_version')
    def node_vars = parameters.get('node_vars')
    def errorCaught = false

    openShiftUtils.withNode(image: pipelineVars.ciRobotteloImage, envVars: node_vars) {
        try {
            stage('Check Out Satellite Instances') {
                brokerUtils.checkout(
                    'deploy-sat-jenkins': [
                        'deploy_sat_version': sat_version,
                        'deploy_snap_version': snap_version,
                        'count': 1
                    ],
                )
            }

            stage('Snap Template Sanity Check') {
                label = 'sat-jenkins-sanitycheck'
                return_code = robotteloUtils.execute(script: """
                    py.test -v \
                    -m 'build_sanity or stubbed' \
                    --include-stubbed \
                    --junit-xml=${label}-results.xml \
                    -o junit_suite_name=${label} \
                    tests/foreman/
                """)
                junit "${label}-results.xml"
            }

            stage('Trigger Polarion Test Run Upload') {
                println("Pytest Exit code is ${return_code}")
                if(return_code.toInteger() <= 2) {
                    println("Calling Polarion Result Upload")
                    build job: "polarion-testrun-upload",
                            parameters: [
                                    [$class: 'StringParameterValue', name: 'snap_version', value: snap_version],
                                    [$class: 'StringParameterValue', name: 'sat_version', value: sat_version],
                                    [$class: 'StringParameterValue', name: 'job_name', value: env.JOB_BASE_NAME],
                                    [$class: 'StringParameterValue', name: 'build_number', value: currentBuild.number.toString()],
                            ],
                            wait: false
                } else {
                    println("Pytest exited with Internal Error, which will result in invalid XML. Skipping Upload")
                }
            }
        }
        catch (error) {
            echo error.getMessage()
            errorCaught = true
        }
        finally {
            stage('Check In Satellite Instances') {
                brokerUtils.checkin_all()
            }
        }
    }
    if(errorCaught || return_code.toInteger() != 0) {
        return false
    } else {
        return true
    }
}

return this;