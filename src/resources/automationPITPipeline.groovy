/*
approve:
    staticMethod org.codehaus.groovy.runtime.DefaultGroovyMethods getText java.net.URL
    staticMethod java.time.Instant now
    method org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper getRawBuild
    method hudson.model.Run getArtifacts
    method hudson.model.Run$Artifact getFileName
*/

@Library("satqe_pipeline_lib") _

import groovy.json.*
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils

/* taken from
 https://gitlab.cee.redhat.com/PIT/interop-resources/blob/master/jenkins_pipelines/triggeringServicePipeline.groovy
*/
def getXunitUrls() {
    List xunitUrls = []
    List artifacts = currentBuild.rawBuild.getArtifacts()
    for (String artifact in artifacts) {
        if (artifact.getFileName().endsWith('.xml')) {
            xunitUrls.add(env.BUILD_URL + "artifact/${artifact}")
        }
    }
    return xunitUrls
}

def robottelo_vars = [
    containerEnvVar(key: 'BROKER_AnsibleTower__base_url', value: "${params.tower_url}"),
    containerEnvVar(key: 'ALLURE_NO_ANALYTICS', value: "1")
]

def headers = ''
def message = ''
def repo_file = []
def test_status = "failed"
def topic = "VirtualTopic.qe.ci.product-scenario.test.complete"
def startTime
def stopTime
def testRuntime
// PIT-specific vars
def compose_id
def correlation_id
def rhel_nvr
def rhel_ver
def rhel_os_repo
def sat_ver
def scenario
def qcow_url

try{
    stage('Parse UMB Message') {
        println("CI Event Received, parsing message")

        // Read CI message headers and body from build environment
        headers = readJSON text: params.get("MESSAGE_HEADERS")
        message = readJSON text: params.get("CI_MESSAGE")

        correlation_id = headers['correlation-id']
        for (product in message['artifact']['products']) {
            if (product['name'].toLowerCase() == 'satellite') {
                sat_ver = product['version']
                scenario = product['subproduct']
            }
            if (product['name'].toLowerCase() == 'rhel') {
                rhel_nvr = product['nvr'] // nvr = name-version-release string
                rhel_ver = product['version']
                os_repos = "[]"
                for (repo in product['repos']) {
                  // extract base repo name for the qcow2 path
                  if (repo['name'].contains('base')) {
                rhel_os_repo = repo['base_url']
                  }
                  // used for formatting of the custom repos file
                  repo_file.push(
                [
                  'name': repo['name'],
                  'description': repo['name'],
                  'file': 'os_repo.repo',
                  'baseurl': repo["base_url"]
                ]
                  )
                }
            }
        }
        println("vars parsed from the UMB message:")
        println("rhel_ver: ${rhel_ver}")
        println("rhel_nvr: ${rhel_nvr}")
        println("rhel_os_repo: ${rhel_os_repo}")
        println("sat_ver: ${sat_ver}")
        println("scenario: ${scenario}")

        // Check for any value not set
        if (rhel_ver && rhel_nvr && rhel_os_repo && sat_ver && scenario) {
            print 'All Work flow values have been set'
        }
        else {
            error("One or more variables were empty")
        }

        // PIT parse QCOW image url
        def rhel_images_repo = rhel_os_repo.replaceFirst('/os[/]?$', '/images')
        index = new URL(rhel_images_repo).text
        def (foo, qcow_file) = (index =~ />([-_\w\\\.]+\.qcow2)</)[0]
        qcow_url = rhel_images_repo + '/' + qcow_file
    }

    stage('Set Build Description and env vars') {
        currentBuild.description = "PIT automation ${scenario}-sat${sat_ver}-${rhel_nvr}"
        env.ROBOTTELO_Robottelo__webdriver_desired_capabilities__tags = "[pit-${rhel_nvr}-${sat_ver}-${scenario}]"
    }

    // spin up the appropariate robottelo image - we don't support 6.8 and older
    def robottelo_tag = sat_ver
    if(sat_ver == '6.8'){
         robottelo_tag = '6.9'
    }
    openShiftUtils.withNode(
        image: "$pipelineVars.ciRobotteloImage:${pipelineVars.robotteloImageTags.find{robottelo_tag.startsWith(it.key)}.value}",
        envVars: robottelo_vars){
        stage('Create Satlab RHEL Template') {
            build job: "PIT-create-template",
                parameters: [
                    string(name: 'qcow_url', value: "${qcow_url}"),
                    string(name: 'rhel_nvr', value: "${rhel_nvr}"),
                    string(name: 'rhel_os_repo', value: "${rhel_os_repo}")
                ]
        }
        /* This stage is already treated as a test and an eventual failure needs to be reported via UMB message
           in the finally block
        */
        stage('Install satellite') {
            // install satellite version given by the `sat_ver` variable
            // TBD - waiting for a workflow name and a format of its result
            startTime = new Date().getTime()
            json_output = JsonOutput.toJson(repo_file)

            sh 'cp "${ROBOTTELO_DIR}/conf/subscription.yaml" subscription.yaml'
            subscription_detail = readYaml file: 'subscription.yaml'
            env.rhn_username = subscription_detail.SUBSCRIPTION.RHN_USERNAME
            env.rhn_pool = subscription_detail.SUBSCRIPTION.RHN_POOLID
            def rhn_password = sh(returnStdout: true, script: 'echo ${ROBOTTELO_subscription__rhn_password}')
            sh (
                returnStdout: true,
                script:
                 """
                 echo '${json_output}' > os_repos.json
                 """
            )
            sh (
                script: 'mkdir screenshots'
            )
            archiveArtifacts artifacts: 'os_repos.json'
            if(scenario == "server") {
                wf_name = "deploy-satellite-rhel-cmp-template"
                wf_args = [
                    'rhel_compose_id': "${rhel_nvr}",
                    'sat_xy_version': "${sat_ver}",
                    'rhel_compose_repositories': 'os_repos.json',
                    'cdn_rhn_username': "${env.rhn_username}",
                    'cdn_rhn_password': "${env.rhn_password}",
                    'cdn_rhsm_pool_id': "${rhn_pool}",
                    'count': "${params.appliance_count}"
               ]
           }
           else {
               wf_name = "deploy-sat-jenkins"
               wf_args = [
                   'deploy_sat_version': "${sat_ver}",
                   'count': "${params.appliance_count}"
               ]
           }
           println("Using broker to execute ${wf_name} with args: ${wf_args}")
           brokerUtils.checkout(
               (wf_name): (wf_args)
           )
        }
        if(scenario == "server") {
            println('executing pytest for SERVER scenario')
            env.ROBOTTELO_server__deploy_arguments = '{}'
            // TODO: use def env.ROBOTTELO_server__deploy_arguments.with
            env.ROBOTTELO_server__deploy_arguments__rhel_compose_id = rhel_nvr
            env.ROBOTTELO_server__deploy_arguments__sat_xy_version = sat_ver
            // TODO: ensure that broker finds os_repos.json file
            env.ROBOTTELO_server__deploy_arguments__rhel_compose_repositories = 'os_repos.json'
            env.ROBOTTELO_server__deploy_arguments__cdn_rhn_username = env.rhn_username
            env.ROBOTTELO_server__deploy_arguments__cdn_rhn_password = env.rhn_password
            env.ROBOTTELO_server__deploy_arguments__cdn_rhsm_pool_id = rhn_pool
            pit_scenario_selector = "-m 'pit_server and not destructive' -k 'not fips'"
        }
        else if(scenario == "client"){
            println('executing pytest for CLIENT scenario')
            env.ROBOTTELO_content_host__deploy_workflow = 'deploy-rhel-cmp-template'
            // extract rhel major version and set it as a default rhel
            def rhel_ver_major = rhel_ver.tokenize('.')[0]
            env.ROBOTTELO_content_host__default_rhel_version = rhel_ver_major
            env.ROBOTTELO_content_host__rhel_versions = [rhel_ver_major]
            env."ROBOTTELO_content_host__hardware__rhel${rhel_ver_major}__compose" = rhel_nvr
            env."ROBOTTELO_content_host__hardware__rhel${rhel_ver_major}__release" = rhel_ver
            pit_scenario_selector = "-m pit_client -k 'rhel${rhel_ver_major} and not fips'"
        }
        // run robottelo PIT tests
        stage('robottelo interop tests'){
            println('executing pytest session')
            return_code = robotteloUtils.execute(script: """
                py.test -v -rEfs --tb=short \
                -n ${params.appliance_count} \
                --dist loadscope \
                --junit-xml=sat-pit-results.xml \
                -o junit_suite_name=sat-pit \
                -o rp_uuid=${env.ROBOTTELO_report_portal__api_key} \
                ${pipelineVars.ibutsuBaseOptions} \
                ${pit_scenario_selector} \
                ${params.pytest_options}
            """)
            junit "sat-pit-results.xml"
            if(return_code == 0){
                test_status = "passed"
            }
            else if(return_code == 1){
                test_status = "failed"
            }
            else{
                throw new Exception("Pytest session exited with code ${return_code.toString()}")
            }
        }
    }
}
catch(error){
    test_status = "failed"
    println("ERROR OCCURRED: ${error}")
    topic = "VirtualTopic.qe.ci.product-scenario.test.error"
}
finally {
    stage('Build CI message') {
        println("Building product-scenario.test.complete ci message..")

        if (startTime){
            stopTime = new Date().getTime()
            testRuntime = ((stopTime - startTime) / 1000).toInteger()
        }
        // override default message topic (development usage)
        if (message['contact']['email'] != "pit-qe@redhat.com") {
            messageTopic += ".${message['contact']['email'].toString().split('@')[0]}"
        }
        // build message
        message['contact']['name'] = "SatelliteQE"
        message['contact']['team'] = "SatelliteQE"
        message['contact']['email'] = "sat-qe-jenkins@redhat.com"
        message['contact']['url'] = env.JENKINS_URL

        message['run'] = [:]
        message['run']['url'] = env.BUILD_URL
        message['run']['log'] = env.BUILD_URL + "console"

        // build list of xunit urls
        List xunitUrls = getXunitUrls()

        message['test'] = [:]
        message['test']['category'] = "interoperability"
        message['test']['namespace'] = "interop"
        message['test']['type'] = "layered-product"
        message['test']['result'] = test_status
        if (testRuntime){
            message['test']['runtime'] = testRuntime
        }
        message['test']['xunit_urls'] = xunitUrls
        message['generated_at'] = java.time.Instant.now().toString()

        println("${message}")
        println("CI message product-scenario.test.complete finished building!")
    }
    stage('Publish results to interop jenkins job') {
        /* publish message per instructions documented at:
        https://docs.engineering.redhat.com/display/PIT/RHEL+LP+Onboarding+-+Triggering+Service#
        RHELLPOnboarding-TriggeringService-SendCIMessage
        using this code as template:
        https://gitlab.cee.redhat.com/PIT/interop-resources/blob/master/jenkins_pipelines/
        triggeringServicePipeline.groovy
        use correlation_id in the message header
        */
        // send message using plugin
        sendCIMessage providerName: "Red Hat UMB",
            overrides: [topic: topic],
            messageType: "Custom",
            messageProperties: "",
            messageContent: message.toString()
    }

    stage('Build Notification') {
        subject = "PIT automation ${scenario}-sat${sat_ver}-${rhel_nvr}: ${test_status}"
        body = "The PIT build finished with status: ${test_status}.<br>${env.BUILD_URL}"
        email_to = ['sat-qe-jenkins']
        emailUtils.sendEmail(
            'to_nicks': email_to,
            'reply_nicks': email_to,
            'subject': subject,
            'body': body
        )
    }
    stage('Check In Satellite Instances') {
        brokerUtils.checkin_all()
    }
}
