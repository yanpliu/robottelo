@Library("satqe_pipeline_lib") _

import groovy.json.*

def at_vars = [
    containerEnvVar(key: 'BROKER_AnsibleTower__base_url', value: "${params.tower_url}"),
]

openShiftUtils.withNode(image: pipelineVars.ciBrokerImage, envVars: at_vars) {

//        stage('Parse UMB Message') {
//             Place holder for RHEL GA UMB Parsing
//
//        }

    stage('Set Build Description') {
        currentBuild.description = "RHEL: " + "${params.rhel_version}"
    }

    stage('Create RHEL GA Template') {

        // Call RHEL GA Template Workflow
        output_rhel_ga =
            sh (
                returnStdout: true,
                script:
                    """
                        broker execute --workflow 'create-rhel-ga-template' \
                        --output-format raw --artifacts last --additional-arg True \
                        --qcow_url ${params.qcow_url}\
                        --rhel_version ${params.rhel_version}
                    """
            )
    }

    stage('Parse Output and Set Template Names'){

        // Print raw output, small JSON so not pretty printing
        println("create-rhel-ga-template output: " + output_rhel_ga)

        // Output to JSON files
        // Single quotes in the broker console output, replace with double quote
        rhel_ga_template = null

        if (output_rhel_ga.contains("data_out")) {
            output_rhel_ga_json = readJSON text: output_rhel_ga.replace("'","\"")
            //writeJSON file: "rhel-ga-temp-creation.json", json: output_rhel_ga_json['data_out']
            rhel_ga_template = output_rhel_ga_json.get('data_out', '').get('template', '')
            println("RHEL GA Template Name is " + rhel_ga_template)
        } else {
            println("ERROR: broker call for RHEL GA workflow must have failed, nothing was returned")
        }


    }

    stage('Archive Artifacts'){
        //archiveArtifacts artifacts: '*.json'
        // Templates should not be used until they have been updated to the latest rpms
        // Template availability will be sent to associates after the RHEL update WF executes
        email_to = ['sat-qe-jenkins', 'satellite-lab-list']
        // Check for any value not set
        if (rhel_ga_template) {
            print "RHEL GA Template has been created"
            subject = "RHEL ${rhel_version} GA Template has been created"
        } else {
            subject = "${env.JOB_NAME} Build ${BUILD_NUMBER} has Failed. Please Investigate"
            println("One or more template names were empty")
            currentBuild.result = 'UNSTABLE'
        }
    }

    stage('Build Notification') {
        emailUtils.sendEmail(
            'to_nicks': email_to,
            'reply_nicks': email_to,
            'subject': subject,
            'body':"${BUILD_URL}"
        )
    }

    stage('Trigger RHEL GA Template Update') {
        if (rhel_ga_template) {
            build job: 'rhel-ga-template-update',
                    parameters: [
                            [$class: 'StringParameterValue', name: 'rhel_version', value: params.rhel_version],
                            [$class: 'StringParameterValue', name: 'tower_url', value: params.tower_url],
                    ],
                    wait: false
        } else {
            println("Template failed creation, skipping triggering update job")
        }
    }
}
