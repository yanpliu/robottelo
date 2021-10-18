@Library("satqe_pipeline_lib") _

import groovy.json.*
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils

node('master') {
    // see more at https://www.jenkins.io/doc/pipeline/examples/#load-from-file
    // with out explicit scm checkout the files are present only on master node
    snapTemplateSanityCheck = load("${WORKSPACE}@script/src/resources/snapTemplateSanityCheck.groovy")
}

def broker_vars = [
    containerEnvVar(key: 'BROKER_AnsibleTower__base_url', value: "${params.tower_url}"),
]

def templateNames = [
    // map of workflow-name: template-name
    // defines what WFs we execute in parallel
    // RHV
    'create-sat-jenkins-template': null,
    'create-sat-capsule-template': null,
    // OSP WF fails on template recreation https://issues.redhat.com/browse/SATQE-16678
    'create-sat-jenkins-template-osp': null,
    'create-sat-capsule-template-osp': null,
]

try {
    openShiftUtils.withNode(image: pipelineVars.ciBrokerImage, envVars: broker_vars) {

        stage('Parse UMB Message') {
            println('CI Event Received, parsing message')

            // Read CI message from build environment
            def message = readJSON text: params.get("CI_MESSAGE")

            println('CI Message: ' + JsonOutput.prettyPrint(JsonOutput.toJson(message)));

            // Write CI message to file to be archived
            //writeJSON file: "ci-message.json", json: message

            sat_version = message.get('satellite_version', '')
            capsule_version = sat_version
            snap_version = message.get('snap_version', '')
            rhel_major_version = message.get('rhel_major_version', '')
            satellite_activation_key = message.get('satellite_activation_key', '')
            capsule_activation_key = message.get('capsule_activation_key', '')

            println('sat_version is ' + sat_version)
            println('snap_version is ' + snap_version)
            println('rhel_major_version is ' + rhel_major_version)
            println('capsule_version is ' + capsule_version)
            println('satellite_activation_key ' + satellite_activation_key)
            println('capsule_activation_key is ' + capsule_activation_key)

            // Set description like '6.9.0 snap: 2.0 on RHEL 7'
            currentBuild.description = sat_version + ' snap: ' + snap_version + ' on RHEL ' + rhel_major_version

            // Check for any value not set
            if (sat_version && snap_version && capsule_version && rhel_major_version && satellite_activation_key && capsule_activation_key) {
                println('All Work flow values have been set')
            } else {
                error('One or more variables were empty')
            }

        }

        stage('Create Snap Templates') {

            // Call all four(RHV:2, OSP:2) AT snap template workflows in parallel
            parallel(
                templateNames.collectEntries{workflow, templateName ->
                    [   // map of string: closure
                        (workflow): ({
                            def workflowOutput = sh(
                                returnStdout: true,
                                script: """
                                    broker execute --workflow '${workflow}' \
                                    --output-format raw --artifacts last --additional-arg True \
                                    --activation_key ${workflow.contains('capsule') ? capsule_activation_key : satellite_activation_key} \
                                    --rhel_major_version ${rhel_major_version} \
                                    --sat_version ${sat_version} \
                                    --snap_version ${snap_version} \
                                    ${workflow.endsWith('-osp') ? '--tower-inventory satlab-osp-01-inventory' : ''}
                                """
                            )
                            // Print raw output, small JSON so not pretty printing
                            println("${workflow} output: ${workflowOutput}")
                            // Read JSON broker console output and replace single quotes by double quotes
                            templateNames[workflow] = readJSON(text: workflowOutput.replace('\'', '"')).data_out?.template
                            println("[workflow] ${workflow} created template: ${templateNames[workflow]}")
                        })
                    ]
                }
            )
        }

        robottelo_vars = broker_vars + [
            containerEnvVar(
                key: 'ROBOTTELO_robottelo__satellite_version',
                value: "'${sat_version.tokenize('.').take(2).join('.')}'"
            ),
            containerEnvVar(
                key: 'ROBOTTELO_server__version__release',
                value: "'${sat_version}'"
            ),
            containerEnvVar(
                key: 'ROBOTTELO_server__version__snap',
                value: "'${snap_version}'"
            ),
        ]
        sanityPassed = snapTemplateSanityCheck(
            'sat_version': sat_version,
            'snap_version': snap_version,
            'node_vars': robottelo_vars,
        )

        stage('Template Names Check') {
            // Check for any value not set
            templates_exist = templateNames.every{workflow, templateName -> templateName}
            if (templates_exist) {
                println('All template names have been created')
                email_to = ['sat-qe-jenkins', 'satellite-qe-tower-users']
                subject = "Templates for ${sat_version} SNAP ${snap_version} are available"
                body = "Following snap ${snap_version} templates have been created:<br>" +
                    templateNames.collect{workflow, templateName -> "<br>\n • $workflow → $templateName"}.join('')
                if (!sanityPassed) {
                    body += '<br><br>However, template sanity check has failed. Please investigate!'
                }
            } else {
                throw new Exception('One or more template names are empty')
            }
        }

        stage('Trigger Automation Test') {
            if (params.trigger_automation == 'Yes') {
                if (templates_exist) {
                    build job: "${sat_version.tokenize('.').take(2).join('.')}-rhel${rhel_major_version}-automation-trigger",
                        parameters: [
                            [$class: 'StringParameterValue', name: 'snap_version', value: snap_version],
                            [$class: 'StringParameterValue', name: 'sat_version', value: sat_version],
                            [$class: 'StringParameterValue', name: 'os', value: "rhel${rhel_major_version}"],
                        ],
                        wait: false
                    build job: "manifest-downloader", wait: false
                } else {
                    println('Template creation failed, skipping triggering automation job')
                    Utils.markStageSkippedForConditional(STAGE_NAME)
                }
            } else {
                println('Skipping triggering automation job, check automation_trigger')
                Utils.markStageSkippedForConditional(STAGE_NAME)
            }
        }
    }
} catch (exc) {
    println("[ERROR] Pipeline failed with ${exc}")
    email_to = ['sat-qe-jenkins', 'satellite-lab-list']
    subject = "${env.JOB_NAME} Build ${BUILD_NUMBER} has Failed. Please Investigate"
    body = "Jenkins Console Log: ${BUILD_URL}. Error that was caught: <br><br> ${exc}"
    currentBuild.result = 'FAILURE'
} finally {
    stage('Build Notification') {
        emailUtils.sendEmail(
            'to_nicks': email_to,
            'reply_nicks': email_to,
            'subject': subject,
            'body': body
        )
    }
}
