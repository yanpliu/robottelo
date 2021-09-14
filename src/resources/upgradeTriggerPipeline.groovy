@Library("satqe_pipeline_lib") _

import groovy.json.*

try {
    node('master') {

        stage('Parse CI Message from Parameters') {

            // Read CI message from build environment
            def message = readJSON text: params.get("CI_MESSAGE")

            // Write CI message to file to be archived
            writeJSON file: "ci-message.json", json: message

            sat_version = message.get('satellite_version', '')
            snap_version = message.get('snap_version', '')
            rhel_major_version = message.get('rhel_major_version', '')

            // Check for any value not set
            if (sat_version && snap_version && rhel_major_version) {
                print 'All Work flow values have been set'
            } else {
                error('The pre-requisites variables to trigger the upgrade build were empty')
            }

            println('sat_version is ' + sat_version)
            println('snap_version is ' + snap_version)
            println('rhel_major_version is ' + rhel_major_version)
        }

        stage('Environment setup and Job Validation') {
            // Set description like '6.9.0 snap: 2.0 on RHEL 7'
            currentBuild.description = sat_version + ' snap: ' + snap_version + ' on RHEL ' + rhel_major_version

            xy_sat_version = sat_version.tokenize('.').take(2).join('.')
            stream = (xy_sat_version == pipelineVars.upgrade_versions.last()) ? 'y_stream' : 'z_stream'

        }


        stage('Trigger Upgrade Phase Pipeline') {
            build job: "sat-${xy_sat_version}-${stream}-upgrade-phase-rhel${rhel_major_version}",
                parameters: [
                    [$class: 'StringParameterValue', name: 'snap_version', value: snap_version],
                    [$class: 'StringParameterValue', name: 'sat_version', value: sat_version],
                    [$class: 'StringParameterValue', name: 'tower_url', value: params.tower_url],
                ],
                wait: false
        }

        stage('Trigger Upgrade Scenarios Pipeline') {
            build job: "sat-${xy_sat_version}-${stream}-upgrade-scenarios-rhel${rhel_major_version}",
                parameters: [
                    [$class: 'StringParameterValue', name: 'snap_version', value: snap_version],
                    [$class: 'StringParameterValue', name: 'sat_version', value: sat_version],
                    [$class: 'StringParameterValue', name: 'tower_url', value: params.tower_url],
                ],
                wait: false
        }

        stage('Trigger Upgrade Existence Tests Pipeline') {
            build job: "sat-${xy_sat_version}-${stream}-upgrade-existence-tests-rhel${rhel_major_version}",
                parameters: [
                    [$class: 'StringParameterValue', name: 'snap_version', value: snap_version],
                    [$class: 'StringParameterValue', name: 'sat_version', value: sat_version],
                    [$class: 'StringParameterValue', name: 'tower_url', value: params.tower_url],
                ],
                wait: false
        }

        stage('Trigger Upgrade All Tier Pipeline') {
            build job: "sat-${xy_sat_version}-${stream}-upgrade-all-tier-rhel${rhel_major_version}",
                parameters: [
                    [$class: 'StringParameterValue', name: 'snap_version', value: snap_version],
                    [$class: 'StringParameterValue', name: 'sat_version', value: sat_version],
                    [$class: 'StringParameterValue', name: 'tower_url', value: params.tower_url],
                ],
                wait: true
        }
    }
} catch (exc) {
    print "Pipeline failed with ${exc}"
    email_to = ['sat-qe-jenkins', 'satellite-lab-list']
    subject = "${env.JOB_NAME} Build ${BUILD_NUMBER} has Failed. Please Investigate"
    body = "Jenkins Console Log: ${BUILD_URL}. Error that was caught: <br><br> ${exc}"
    currentBuild.result = 'FAILURE'
} finally {
    if(currentBuild.result == 'FAILURE') {
        emailUtils.sendEmail(
            'to_nicks': email_to,
            'reply_nicks': email_to,
            'subject': subject,
            'body': body
        )
    }
}
