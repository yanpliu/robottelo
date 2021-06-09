@Library("satqe_pipeline_lib") _

import groovy.json.*

def testfm_vars = [
    containerEnvVar(key: 'BROKER_AnsibleTower__base_url', value: "${params.tower_url}"),
]

openShiftUtils.withNode(image: pipelineVars.ciTestFmImage, envVars: testfm_vars) {
    try {
        stage('Check out Satellite/Capsule') {
            satellite_inventory = brokerUtils.checkout(
                'deploy-sat-jenkins': [
                    'deploy_sat_version': params.sat_version,
                    'deploy_snap_version': params.snap_version,
                    'count': 1
                ],
            )
            target_inventory = satellite_inventory
            if (params.component == 'Capsule') {
                capsule_inventory = brokerUtils.checkout(
                    'deploy-sat-capsule': [
                        'deploy_sat_version': params.sat_version,
                        'deploy_snap_version': params.snap_version,
                        'count': 1
                    ],
                )
                capsule_inventory.remove(capsule_inventory.indexOf(satellite_inventory[0]))
                target_inventory = capsule_inventory
                sh 'cp ${TESTFM_DIR}/conf/subscription.yaml ${WORKSPACE}/subscription.yaml'
                subscription_detail = readYaml file: "${WORKSPACE}/subscription.yaml"

                sat_cap_integration =
                    sh(
                        returnStdout: true,
                        script: """
                            broker execute --workflow 'satellite-capsule-integration' \
                            --output-format raw --artifacts last --additional-arg True \
                            --capsule_hostname ${capsule_inventory[0].hostname} \
                            --capsule_name ${capsule_inventory[0].name} \
                            --satellite_hostname ${satellite_inventory[0].hostname} \
                            --satellite_name ${satellite_inventory[0].name} \
                            --sat_cap_version ${params.sat_version.tokenize('.').take(2).join('.')} \
                            --rhn_username ${subscription_detail.SUBSCRIPTION.RHN_USERNAME} \
                            --rhn_password \${TESTFM_SUBSCRIPTION__RHN_PASSWORD} \
                            --rhn_pool '${subscription_detail.SUBSCRIPTION.RHN_POOLID}' \
                        """
                    )
            }
        }

        stage('Set Build Description') {
            first_host_name_parts = satellite_inventory[0].name.split('-')
            sat_version = (params.sat_version.tokenize('.').size() > 2) ? params.sat_version : first_host_name_parts[3]
            snap_version = params.snap_version ?: first_host_name_parts[4]
            currentBuild.description = params.component + ' ' + sat_version + ' Snap ' + snap_version
        }

        stage('Execute Automation Test Suite') {
            command = """
                pytest -v \
                --disable-warnings \
                --junit-xml=foreman-results.xml \
                --ansible-host-pattern server \
                --ansible-user root \
                --ansible-inventory testfm/inventory \
                ${params.pytest_options}
            """
            testfmUtils.execute(inventory: target_inventory, script: command)
            junit 'foreman-results.xml'
        }
        email_body = "${env.JOB_NAME} Build ${BUILD_NUMBER} is completed, check results ${BUILD_URL}"
        email_subject = "Foreman-Maintain Automation Report for ${currentBuild.description}"
    }
    catch (exc) {
        echo "Catch Error: \n${exc}"
        email_body = "Jenkins Console Log: ${BUILD_URL}. Error that was caught:<br><br> ${exc}"
        email_subject = "${env.JOB_NAME} Build ${BUILD_NUMBER} has Failed. Please Investigate"
        currentBuild.result = 'FAILURE'
    }
    finally {
        stage('Check In Satellite Instances') {
            brokerUtils.checkin_all()
        }
        emailUtils.sendEmail(
            'to_nicks': ['gtalreja'],
            'reply_nicks': ['gtalreja'],
            'subject': email_subject,
            'body': email_body
        )
    }
}
