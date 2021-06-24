import groovy.json.*

def create_launch(Map parameters = [:]) {
    /**
     * Create ReportPortal launch
     *
     * Usage:
     * (launch_uuid, wrapper_test_uuid) = reportPortalUtils.create_launch(
     *      launch_name: 'OCP-Jenkins-CI',
     *      rerun_of: "",
     *      filter_attributes: "6.9.1-3.0,Critical",
     *      launch_attributes: [
     *          [
     *              key: "sat_version",  (this attribute is mandatory)
     *              value: "6.9.1-3.0"
     *          ],
     *          [
     *              key: "y_stream",
     *              value: "6.9"
     *          ],
     *          [
     *              key: "importance",
     *              value: "Critical"
     *          ],
     *          [
     *              key: "instance_count",
     *              value: "5"
     *          ]
     *      ]
     * )
     */

    withCredentials([string(credentialsId: 'reportportal-robottelo-token', variable: 'rp_token')]) {

        def launch_name = parameters.get('launch_name', 'OCP-Jenkins-CI')
        def launch_attributes = parameters.get('launch_attributes', [])
        def rerun_of = parameters.get('rerun_of')
        def filter_attributes = parameters.get('filter_attributes')
        /* figure out whether we're running a re-run searching for not-running launches with name
        "importance_<importance>" and tag/attribute: "<X.Y.Z-S>
        */
        def jsonSlurper = new JsonSlurper()
        def filter_params =
            "?page.page=1&page.size=1&page.sort=name%2Cnumber%2CDESC&" +
            "filter.ne.status=IN_PROGRESS&filter.has.attributeValue=${filter_attributes}&filter.eq.name=${launch_name}"

        if(!rerun_of){
            def parent_req = new URL("${pipelineVars.reportPortalServer}/api/v1/${pipelineVars.reportPortalProject}/launch${filter_params}").openConnection()
            parent_req.setRequestProperty("Authorization", "bearer ${rp_token}")
            parent_req.setRequestMethod('GET')
            parent_req.setDoOutput(true)
            def parent_rc = parent_req.getResponseCode()
            if(parent_rc == 200){
                // parse the API response JSON payload
                def parent_response = jsonSlurper.parseText(parent_req.getInputStream().getText())
                if(parent_response['content'].size() > 0){
                    // one or more previous launches detected, assuming this is a re-run
                    rerun_of = parent_response['content'][0]['uuid']
                    println("related launch found, set as re-run of ${rerun_of}")
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
        def launch_req = new URL("${pipelineVars.reportPortalServer}/api/v2/${pipelineVars.reportPortalProject}/launch").openConnection()
            launch_req.setRequestMethod('POST')
            launch_req.setDoOutput(true)
            launch_req.setRequestProperty("Authorization", "bearer ${rp_token}")
            launch_req.setRequestProperty("Content-Type", "application/json")
            def new_launch_payload = [
                "description": env.JOB_NAME,
                "mode": "DEFAULT",
                "name": launch_name,
                "rerun": "${rerun_of as Boolean}",
                "startTime": "${new Date().getTime()}",
                "attributes": launch_attributes
            ]
        if(rerun_of){
            println("add rerunOf parameter to payload: ${rerun_of}")
            new_launch_payload["rerunOf"] = rerun_of
            currentBuild.description += " rerun"
        }
        println("new launch payload:")
        println(JsonOutput.prettyPrint(JsonOutput.toJson(new_launch_payload)))
        launch_req.getOutputStream().write(JsonOutput.toJson(new_launch_payload).getBytes("UTF-8"));
        def launch_rc = launch_req.getResponseCode()
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
        def test_req = new URL("${pipelineVars.reportPortalServer}/api/v2/${pipelineVars.reportPortalProject}/item").openConnection()
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

    } // withCredentials

    return [launch_uuid, wrapper_test_uuid]

}


def finish_launch(Map parameters = [:]) {
    /**
     * Set ReportPortal launch as finished
     *
     * Usage:
     * responseCode = reportPortalUtils.finish_launch(
     *      launch_uuid: <launch_uuid>,
     *      launch_status: <launch_status>,  (implicit default is PASSED)
     * )
     */

    def launch_uuid = parameters.get('launch_uuid')
    def launch_status = parameters.get('launch_status', 'PASSED')
    def finish_rc

    withCredentials([string(credentialsId: 'reportportal-robottelo-token', variable: 'rp_token')]) {

        def finish_launch_req = new URL("${pipelineVars.reportPortalServer}/api/v2/${pipelineVars.reportPortalProject}/launch/${launch_uuid}/finish").openConnection()
        println("${pipelineVars.reportPortalServer}/api/v2/${pipelineVars.reportPortalProject}/launch/${launch_uuid}/finish")
        finish_launch_req.setRequestMethod('PUT')
        finish_launch_req.setDoOutput(true)
        finish_launch_req.setRequestProperty("Authorization", "bearer ${rp_token}")
        finish_launch_req.setRequestProperty("Content-Type", "application/json")
        def finish_launch_payload = [
            "status": launch_status,
            "endTime": "${new Date().getTime()}"
        ]
        println('Finish launch payload:')
        println(JsonOutput.prettyPrint(JsonOutput.toJson(finish_launch_payload)))
        finish_launch_req.getOutputStream().write(JsonOutput.toJson(finish_launch_payload).getBytes("UTF-8"));
        finish_rc = finish_launch_req.getResponseCode()

    } // withCredentials

    println("Finish launch request got response: ${finish_rc}")
    return finish_rc

}
