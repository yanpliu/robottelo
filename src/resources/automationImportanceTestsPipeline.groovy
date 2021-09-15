@Library("satqe_pipeline_lib") _

import groovy.json.*
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils

def robottelo_vars = [
    containerEnvVar(key: 'BROKER_AnsibleTower__base_url', value: "${params.tower_url}"),
    containerEnvVar(
        key: 'ROBOTTELO_Robottelo__webdriver_desired_capabilities__tags',
        value: "[automation-${params.sat_version}-${params.importance}-rhel7]"
    )
]
def sat_version = params.sat_version
def snap_version = params.snap_version

// use this format once robottelo rerun-failed plugin has this sorted
// def rp_launch = "Importance_${params.importance}"
def rp_launch = params.rp_launch
def rerun_of = params.rerun_of
def wrapper_test_uuid = ''
def junit_xml_file = "sat-${params.importance}-results.xml"

def workflow = params.workflow
def test_run_type = ''
if (params.importance == 'Fips') {
    test_run_type = 'Fips'
}

openShiftUtils.withNode(
    image: "$pipelineVars.ciRobotteloImage:${pipelineVars.robotteloImageTags.find{sat_version.startsWith(it.key)}.value}",
    envVars: robottelo_vars
) {
    try {
        stage('Check Out Satellite Instances') {
            inventory = brokerUtils.checkout(
                (workflow): [
                    'deploy_sat_version': params.sat_version,
                    'deploy_snap_version': params.snap_version,
                    'deploy_template_name': params.template_name,
                    // https://bugzilla.redhat.com/show_bug.cgi?id=1976051
                    // Remove target_memory and pool skip args when PUMA BZ resolved
                    'target_memory': '35GiB',
                    'skip_vm_pooling': 'True',
                    'count': params.xdist_workers
                ]
            )
        }

        stage('Set Build Description and Satellite Env Vars') {
            // TODO: Add rhel version parsing too
            // https://projects.engineering.redhat.com/browse/SATQE-12327
            // uses the template name field from inventory, ex. template: tpl-sat-jenkins-6.9.2-1.0-rhel-7.9
            first_host_name_parts = inventory[0]._broker_args.template.split('-')
            // example: [tpl, sat, jenkins, 6.9.2, 1.0, rhel, 7.9]
            sat_version = (params.sat_version.tokenize('.').size() > 2) ? params.sat_version : first_host_name_parts[3]
            snap_version = params.snap_version ?: first_host_name_parts[4]
            currentBuild.description = sat_version + " snap: " + snap_version

            env.ROBOTTELO_server__version__release = "'${sat_version}'"
            env.ROBOTTELO_server__version__snap = "'${snap_version}'"
            env.ROBOTTELO_robottelo__satellite_version = "'${sat_version.tokenize('.').take(2).join('.')}'"
        }

        stage('Execute Automation Test Suite') {
            // -n argument should be params.xdist_workers when robottelo 8303 is merged
            if(params.use_ibutsu){
                ibutsu_options = pipelineVars.ibutsuBaseOptions
            } else { ibutsu_options = " "}
            return_code = robotteloUtils.execute(script: """
                py.test -v -rEfs --tb=short \
                --durations=20 --durations-min=600.0 \
                -n ${params.xdist_workers} \
                --junit-xml=${junit_xml_file} \
                -o junit_suite_name=sat-${params.importance} \
                ${ibutsu_options} \
                ${params.pytest_options}
            """)

            results_summary = junit junit_xml_file

            // Add a sidebar link with the ibutsu URL
            log_lines = currentBuild.getRawBuild().getLog(50)
            ibutsu_line = log_lines.find { it ==~ '.*Results can be viewed on.*(http.*ibutsu.*)'}
            if (ibutsu_line){
                ibutsu_link = ibutsu_line.substring(ibutsu_line.indexOf('http'))
                properties([
                    sidebarLinks([[displayName: 'Ibutsu Test Run', iconFileName: '', urlName: ibutsu_link]])
                ])
            } else {
                println('No ibutsu run link found, no sidebar link to add')
                ibutsu_link = "missing"
            }

            println("Pytest Exit code is ${return_code}")
            if(return_code.toInteger() > 2) {
                throw new Exception("pytest return code indicates an internal error or session failure")
            }
        }


        stage('Trigger Polarion Test Run Upload') {
            println("Calling Polarion Result Upload")
            build job: "polarion-testrun-upload",
                    parameters: [
                            [$class: 'StringParameterValue', name: 'snap_version', value: snap_version],
                            [$class: 'StringParameterValue', name: 'sat_version', value: sat_version],
                            [$class: 'StringParameterValue', name: 'results_job_name', value: env.JOB_BASE_NAME],
                            [$class: 'StringParameterValue', name: 'test_run_type', value: test_run_type],
                            [$class: 'StringParameterValue', name: 'results_build_number', value: currentBuild.number.toString()],
                    ],
                    wait: false

        }
	stage('Trigger Report Portal Launch Upload') {
            if(params.use_reportportal) {
                attr_ystream = "${sat_version}".tokenize('.').take(2).join('.')
                // figure out the rerun_of and append it in the job params if not null 
                if (!rerun_of) {
                    rerun_of = reportPortalUtils.get_rerun_of(rp_launch, "${sat_version}-${snap_version},${params.importance}")
                }
                rp_job_params = [
	            [$class: 'StringParameterValue', name: 'results_job_name', value: env.JOB_BASE_NAME],
                    [$class: 'StringParameterValue', name: 'rp_launch_description', value: currentBuild.description],
                    [$class: 'StringParameterValue', name: 'results_build_number', value: currentBuild.number.toString()],
                    [$class: 'StringParameterValue', name: 'rp_launch_name', value: rp_launch],
                    [$class: 'StringParameterValue', name: 'rp_rerun_of', value: rerun_of],
	            [$class: 'StringParameterValue',
		        name: 'rp_launch_attrs',
		        value: "sat_version=${sat_version}-${snap_version} y_stream=${attr_ystream} importance=${params.importance} instance_count=${params.xdist_workers}"
	            ]
                ]
                println("Calling Report Portal Result Upload")
                build job: "reportportal-launch-upload",
                    parameters: rp_job_params,
                    wait: false
            }
            else {
                println('Skipping the Report Portal logging')
                Utils.markStageSkippedForConditional(STAGE_NAME)
            }
        }

        stage('Send Result Email') {
            if(currentBuild.result == 'SUCCESS' || currentBuild.result == 'UNSTABLE') {
                // report portal does not make it easy to get the launch ID to compose a URL
                // TODO: Hook into the rp launch tooling and get the launch URL to include in this email
                email_body = emailUtils.emailBody(
                    results_summary: results_summary,
                    importance: params.importance,
                    sat_version: sat_version,
                    ibutsu_link: ibutsu_link
                )
                emailUtils.sendEmail(
                    'to_nicks': ['satqe-list'],
                    'reply_nicks': ['sat-qe-jenkins'],
                    'subject': "${currentBuild.description}: ${params.importance} Automation Results Available",
                    'body': email_body.stripIndent()
                )
            }
            else {
                println("The build is failed due to product or environment-related issues, hence skipping the Email stage")
                Utils.markStageSkippedForConditional(STAGE_NAME)
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
        stage('Check In Satellite Instances') {
            brokerUtils.checkin_all()
        }
    }
}
