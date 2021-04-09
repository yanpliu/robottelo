@Library("satqe_pipeline_lib") _

import groovy.json.*

withCredentials([
        usernamePassword(credentialsId: 'ansible-tower-jenkins-user', passwordVariable: 'USERPASS', usernameVariable: 'USERNAME'),
        string(credentialsId: 'reportportal-robottelo-token', variable: 'rp_token')
]) {

    /* ALLURE_NO_ANALYTICS=1 disables google analytics reporting from pytest-reportportal:
       https://github.com/reportportal/agent-python-pytest#integration-with-ga
    */

    def robottelo_vars = [
            containerEnvVar(key: 'BROKER_AnsibleTower__base_url', value: "${params.tower_url}"),
            containerEnvVar(key: 'BROKER_AnsibleTower__username', value: "${USERNAME}"),
            containerEnvVar(key: 'BROKER_AnsibleTower__password', value: "${USERPASS}"),
            containerEnvVar(key: 'RP_UUID', value: "${rp_token}"),
            containerEnvVar(key: 'ALLURE_NO_ANALYTICS', value: "1"),
            containerEnvVar(
                    key: 'ROBOTTELO_Robottelo__webdriver_desired_capabilities__tags',
                    value: "[automation-${params.sat_version}-${params.importance}-rhel7]"
            )
    ]
    def sat_version = params.sat_version
    def snap_version = params.snap_version

    def rp_url = pipelineVars.reportPortalServer
    def rp_project = pipelineVars.reportPortalProject
    // use this format once robottelo rerun-failed plugin has this sorted
    // def rp_launch = "Importance_${params.importance}"
    def rp_launch = "OCP-Jenkins-CI"
    def rp_pytest_options = "--reportportal -o rp_endpoint=${rp_url} -o rp_project=${rp_project} -o rp_hierarchy_dirs=false " +
        "-o  rp_log_batch_size=100 --rp-launch=${rp_launch}"
    def rerun_of = params.rerun_of
    def launch_uuid = ''
    def wrapper_test_uuid = ''

    openShiftUtils.withNode(image: pipelineVars.ciRobotteloImage, envVars: robottelo_vars) {
        try {
            stage('Check Out Satellite Instances') {
                inventory = brokerUtils.checkout(
                    'deploy-sat-jenkins': [
                        'sat_version': params.sat_version,
                        'snap_version': params.snap_version,
                        'count': params.appliance_count
                    ],
                )
            }

            stage('Set Build Description') {
                // TODO: Add rhel version parsing too
                // https://projects.engineering.redhat.com/browse/SATQE-12327
                // uses the name field from inventory, ex. mshriver-sat-jenkins-6.9.0-9.0-fb77ad96
                first_host_name_parts = inventory[0].name.split('-')
                // example: [mshriver, sat, jenkins, 6.9.0, 9.0, fb77ad96]
                sat_version = params.sat_version ?: first_host_name_parts[3]
                snap_version = params.snap_version ?: first_host_name_parts[4]
                currentBuild.description = sat_version + " snap: " + snap_version

            }
            stage('Create report portal launch and parent test') {
                /* figure out whether we're running a re-run searching for not-running launches with name
                "importance_<importance>" and tag/attribute: "<X.Y.Z-S>
                */
                def jsonSlurper = new JsonSlurper()
                def filter_params =
                    "?page.page=1&page.size=1&page.sort=name%2Cnumber%2CDESC&" +
                    "filter.ne.status=IN_PROGRESS&filter.has.attributeValue=" +
                    "${sat_version}-${snap_version}&filter.eq.name=${rp_launch}"

                if(!rerun_of){
                    def parent_req = new URL("${rp_url}/api/v1/${rp_project}/launch${filter_params}").openConnection()
                    parent_req.setRequestProperty("Authorization", "bearer ${rp_token}")
                    parent_req.setRequestMethod('GET')
                    parent_req.setDoOutput(true)
                    def parent_rc = parent_req.getResponseCode()
                    // FIXME: add a proper http response code check
                    if(parent_rc == 200){
                        // parse the API response JSON payload
                        def parent_response = jsonSlurper.parseText(parent_req.getInputStream().getText())
                        if(parent_response['content'].size() > 0){
                            println("related launch found - ${launch_uuid}, setting it as rerun target")
                            // one or more previous launches detected, assuming this is a re-run
                            rerun_of = parent_response['content'][0]['uuid']
                        }
                        else{
                            println("no related launches found, assuming no re-run")
                        }
                    }
                    else{
                        error("Error occurred while trying to fetch a list of launches: " +
                            "response code: ${parent_rc} " + "with a message: ${parent_req.getInputStream().getText()}"
                        )
                    }
                }
                def launch_req = new URL("${rp_url}/api/v2/${rp_project}/launch").openConnection()
                    launch_req.setRequestMethod('POST')
                    launch_req.setDoOutput(true)
                    launch_req.setRequestProperty("Authorization", "bearer ${rp_token}")
                    launch_req.setRequestProperty("Content-Type", "application/json")
                    def new_launch_payload = [
                        "description": "${env.JOB_NAME}",
                        "mode": "DEFAULT",
                        "name": "${rp_launch}",
                        "rerun": "${rerun_of as Boolean}",
                        "startTime": "${new Date().getTime()}",
                        "attributes": [
                            [
                                "key": "sat_version",
                                "value": "${sat_version}-${snap_version}"
                            ],
                            [
                                "key": "y_stream",
                                "value": "${sat_version}".tokenize('.').take(2).join('.')
                            ],
                            [
                                "key": "importance",
                                "value": "${params.importance}"
                            ],
                            [
                                "key": "instance_count",
                                "value": "${params.appliance_count}"
                            ]
                        ]
                    ]
                if(rerun_of){
                    println("add rerunOf parameter to payload: ${launch_uuid}")
                    new_launch_payload["rerunOf"] = rerun_of
                    currentBuild.description += " rerun"
                }
                println("new launch payload:")
                println(JsonOutput.prettyPrint(JsonOutput.toJson(new_launch_payload)))
                launch_req.getOutputStream().write(JsonOutput.toJson(new_launch_payload).getBytes("UTF-8"));
                def launch_rc = launch_req.getResponseCode()
                // FIXME: add a proper http response code check
                if(launch_rc == 201){
                    // parse the API response JSON payload
                    launch_uuid = jsonSlurper.parseText(launch_req.getInputStream().getText())['id']
                    println("New launch started: ${launch_uuid}")
                }
                else{
                    error("Error occurred while trying to start a launch - start launch response " +
                    "{launch_rc} with a message: ${launch_req.getInputStream().getText()}"
                    )
                }

                // create a parent test item as a workaround of https://github.com/reportportal/reportportal/issues/1249
                def test_req = new URL("${rp_url}/api/v2/${rp_project}/item").openConnection()
                test_req.setRequestMethod('POST')
                test_req.setDoOutput(true)
                test_req.setRequestProperty("Authorization", "bearer ${rp_token}")
                test_req.setRequestProperty("Content-Type", "application/json")
                def new_test_payload = [
                    "hasStats": "true",
                    "launchUuid": "${launch_uuid}",
                    "name": "robottelo",
                    "startTime": "${new Date().getTime()}",
                    "type": "SUITE"
                ]
                println("test payload:")
                println(JsonOutput.prettyPrint(JsonOutput.toJson(new_test_payload)))
                test_req.getOutputStream().write(JsonOutput.toJson(new_test_payload).getBytes("UTF-8"));
                def test_rc = test_req.getResponseCode()
                // FIXME: add a proper http response code check
                if(test_rc == 201){
                    // parse the API response JSON payload
                    def test_response = jsonSlurper.parseText(test_req.getInputStream().getText())
                    wrapper_test_uuid = test_response['id']
                    println("New wrapper test started: ${wrapper_test_uuid}")

                }
                else{
                    error(
                    "Error occurred while trying to start a parent test item - " +
                    "response code: ${test_rc}; with a message: ${test_req.getInputStream().getText()}"
                    )
                }
                // Finally, append the acquired UUIDs to pytest options
                rp_pytest_options += " --rp-launch-id=${launch_uuid} --rp-parent-item-id=${wrapper_test_uuid}"
            }

            stage('Set robottelo.properites') {
                sh """
                    crudini --set /opt/app-root/src/robottelo/robottelo.properties report_portal report_portal ${rp_url}
                    crudini --set /opt/app-root/src/robottelo/robottelo.properties report_portal project ${rp_project}
                """
            }

            stage('Execute Automation Test Suite') {
                // -n argument should be params.appliance_count when robottelo 8303 is merged
                if(params.use_ibutsu){
                    ibutsu_options = pipelineVars.ibutsuBaseOptions
                } else { ibutsu_options = " "}
                return_code = robotteloUtils.execute(script: """
                    py.test -v -rEfs --tb=line \
                    --durations=20 --durations-min=600.0 \
                    --importance ${params.importance} \
                    -n ${params.appliance_count} \
                    --dist loadscope \
                    --junit-xml=sat-${params.importance}-results.xml \
                    -o junit_suite_name=sat-${params.importance} \
                    ${ibutsu_options} \
                    ${rp_pytest_options} \
                    ${params.pytest_options}
                """)

                junit "sat-${params.importance}-results.xml"
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
                     currentBuild.result = 'ABORTED'
                }
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
            if(launch_uuid){
                stage('Finish the Report Portal Launch') {
                    def finish_launch_req = new URL("${rp_url}/api/v2/${rp_project}/launch/${launch_uuid}/finish").openConnection()
                    println("${rp_url}/api/v2/${rp_project}/launch/${launch_uuid}/finish")
                    finish_launch_req.setRequestMethod('PUT')
                    finish_launch_req.setDoOutput(true)
                    finish_launch_req.setRequestProperty("Authorization", "bearer ${rp_token}")
                    finish_launch_req.setRequestProperty("Content-Type", "application/json")
                    def finish_launch_payload = [
                        "status": "PASSED",
                        "endTime": "${new Date().getTime()}"
                    ]
                    println('Finish launch payload:')
                    println(JsonOutput.prettyPrint(JsonOutput.toJson(finish_launch_payload)))
                    finish_launch_req.getOutputStream().write(JsonOutput.toJson(finish_launch_payload).getBytes("UTF-8"));
                    def finish_rc = finish_launch_req.getResponseCode()
                    println("Finish launch request got response: ${finish_rc}")
                }
            }
            stage('Check In Satellite Instances') {
                brokerUtils.checkin_all()
            }
        }
    }
}
