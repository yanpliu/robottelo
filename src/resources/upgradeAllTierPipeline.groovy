@Library("satqe_pipeline_lib") _

import groovy.json.*
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils

def to_version = params.sat_version.tokenize('.').take(2).join('.')
def from_version = ("${params.stream}" == 'z_stream')? to_version : upgradeUtils.previous_version(to_version)
def rp_launch = 'Upgrades'
def rp_pytest_options = ''
def launch_uuid = ''
def wrapper_test_uuid = ''
def test_run_type = 'upgrade'
def at_vars = [
    containerEnvVar(key: 'BROKER_AnsibleTower__base_url', value: "${params.tower_url}"),
    containerEnvVar(key: 'ROBOTTELO_ROBOTTELO__SATELLITE_VERSION', value: "'${to_version}'"),
    containerEnvVar(key: 'ROBOTTELO_SERVER__INVENTORY_FILTER', value: 'name<satellite-upgrade'),
    containerEnvVar(key: 'ROBOTTELO_SERVER__VERSION__RELEASE', value: "'${to_version}'"),
    containerEnvVar(key: 'ROBOTTELO_SERVER__VERSION__SNAP', value: "'${params.snap_version}'"),
    containerEnvVar(key: 'UPGRADE_ROBOTTELO__SATELLITE_VERSION', value: "'${to_version}'"),
    containerEnvVar(key: 'UPGRADE_UPGRADE__FROM_VERSION', value: "'${from_version}'"),
    containerEnvVar(key: 'UPGRADE_UPGRADE__TO_VERSION', value: "'${to_version}'"),
    containerEnvVar(key: 'UPGRADE_UPGRADE__OS', value: params.os),
    containerEnvVar(key: 'UPGRADE_UPGRADE__DISTRIBUTION', value: params.distribution),
    containerEnvVar(key: 'UPGRADE_UPGRADE__FOREMAN_MAINTAIN_SATELLITE_UPGRADE', value: "${params.foreman_maintain_satellite_upgrade}"),
    containerEnvVar(key: 'UPGRADE_UPGRADE__DOWNSTREAM_FM_UPGRADE', value: "${params.downstream_fm_upgrade}"),
    containerEnvVar(key: 'UPGRADE_UPGRADE__FOREMAN_MAINTAIN_CAPSULE_UPGRADE', value: "${params.foreman_maintain_capsule_upgrade}"),
]

openShiftUtils.withNode(
    image: "$pipelineVars.ciUpgradeRobotteloImage:${pipelineVars.robotteloImageTags.find{to_version.startsWith(it.key)}.value}",
    envVars: at_vars
) {
    try {
        stage('Check out satellite and capsule of GA version') {
            satellite_inventory = brokerUtils.checkout(
                'deploy-satellite-upgrade': [
                    'deploy_sat_version': from_version,
                    'deploy_scenario': 'satellite-upgrade',
                    'deploy_rhel_version': params.os[-1],
                    'count': params.xdist_workers,
                ],
            )
            all_inventory = brokerUtils.checkout(
                'deploy-capsule-upgrade': [
                    'deploy_sat_version': from_version,
                    'deploy_scenario': 'capsule-upgrade',
                    'deploy_rhel_version': params.os[-1],
                    'count': params.xdist_workers,
                ],
            )
            // Filter Capsules from all inventory
            capsule_inventory = []
            all_inventory.each { if (it._broker_args.host_type=='capsule') capsule_inventory.add(it) }

            // Subscribe the machine to RHN for extra packages
            sh 'cp ${UPGRADE_DIR}/conf/subscription.yaml ${WORKSPACE}/subscription.yaml'
            subscriptions = readYaml file: "${WORKSPACE}/subscription.yaml"

            // Integrate Satellite and Capsule
            upgradeUtils.parallel_run_func(
                upgradeUtils.&integrate_satellite_capsule,
                stepName: 'Satellite and Capsule',
                satellite_inventory: satellite_inventory,
                capsule_inventory: capsule_inventory,
                version: from_version, subscriptions: subscriptions
            )
            // Build Description
            calculated_build_name = from_version + " to " + "${params.sat_version}" + " snap: " + "${params.snap_version}"
            currentBuild.displayName = "${params.build_label}" ?: calculated_build_name
        }

        stage('Setup ssh-agent') {
            sh """
                echo \"\${USER_NAME:-default}:x:\$(id -u):0:\${USER_NAME:-default} user:\${HOME}:/sbin/nologin\" >> /etc/passwd
                echo \"\$(ssh-agent -s)\" >> ~/.bashrc
                source ~/.bashrc
                ssh-add - <<< \$SATLAB_PRIVATE_KEY
            """
        }

        stage('Setup products for upgrade') {
            upgradeUtils.parallel_run_func(
                upgradeUtils.&setup_products,
                stepName: 'Satellite and Capsule',
                product: 'capsule',
                os_ver: params.os,
                satellite_inventory: satellite_inventory,
                capsule_inventory: capsule_inventory
            )
        }

        stage('Satellite upgrade') {
            upgradeUtils.parallel_run_func(
                upgradeUtils.&upgrade_products,
                stepName: 'Satellite',
                product: 'satellite',
                upgrade_type: params.upgrade_type,
                satellite_inventory: satellite_inventory,
                capsule_inventory: capsule_inventory
            )
        }

        stage('Capsule upgrade') {
            upgradeUtils.parallel_run_func(
                upgradeUtils.&upgrade_products,
                stepName: 'Capsule',
                product: 'capsule',
                upgrade_type: params.upgrade_type,
                satellite_inventory: satellite_inventory,
                capsule_inventory: capsule_inventory
            )
        }

        stage('Create report portal launch and parent test') {
            if (params.use_reportportal) {
                (launch_uuid, wrapper_test_uuid) = reportPortalUtils.create_launch(
                    launch_name: rp_launch,
                    launch_attributes: [
                        [
                            key: 'from_version',
                            value: "${from_version}"
                        ],
                        [
                            key: 'to_version',
                            value: "${to_version}"
                        ],
                        [
                            key: 'os',
                            value: "${params.os}"
                        ],
                        [
                            key: 'instance_count',
                            value: "${params.xdist_workers}"
                        ]
                    ]
                )
                // append the acquired UUIDs to pytest options
                rp_pytest_options = "--reportportal -o rp_endpoint=${pipelineVars.reportPortalServer} " +
                    "-o rp_project=${pipelineVars.reportPortalProject} -o rp_hierarchy_dirs=false " +
                    "-o rp_log_batch_size=100 --rp-launch=${rp_launch} --rp-launch-id=${launch_uuid} " +
                    "--rp-parent-item-id=${wrapper_test_uuid}"
            }
            else {
                println("Skipping 'Create report portal launch and parent test' stage")
                Utils.markStageSkippedForConditional(STAGE_NAME)
            }
        }

        stage('Run All-Tier tests') {
            return_code = robotteloUtils.execute(script: """
                pytest -v \
                --disable-warnings \
                -m upgrade \
                --junit-xml=upgrade-all-tiers-results.xml \
                -o junit_suite_name=all-tiers-upgrade-sequential \
                -n ${params.xdist_workers} \
                ${rp_pytest_options} \
                tests/foreman/
            """)
            results_summary = junit 'upgrade-all-tiers-results.xml'
            currentBuild.result = 'SUCCESS'
        }

        stage('Trigger polarion test run upload') {
            println("Pytest Exit code is ${return_code}")
            if (return_code.toInteger() <= 2) {
                println('Calling Polarion Result Upload')
                build job: "polarion-testrun-upload",
                    parameters: [
                        [$class: 'StringParameterValue', name: 'snap_version', value: params.snap_version],
                        [$class: 'StringParameterValue', name: 'sat_version', value: params.sat_version],
                        [$class: 'StringParameterValue', name: 'rhel_version', value: params.os[-1]],
                        [$class: 'StringParameterValue', name: 'results_job_name', value: env.JOB_BASE_NAME],
                        [$class: 'StringParameterValue', name: 'test_run_type', value: test_run_type],
                        [$class: 'StringParameterValue', name: 'results_build_number', value: currentBuild.number.toString()],
                    ],
                    wait: false
            } else {
                println('Pytest exited with Internal Error, which will result in invalid XML. Skipping Upload')
                currentBuild.result = 'ABORTED'
            }
        }
        stage('Send Result Email') {
            if(currentBuild.result == 'SUCCESS' || currentBuild.result == 'UNSTABLE') {
                email_body = emailUtils.emailBody(
                    results_summary: results_summary,
                    sat_version: params.sat_version,
                    description: calculated_build_name
                )
                emailUtils.sendEmail(
                    'to_nicks': ['satqe-list'],
                    'reply_nicks': ['sat-qe-jenkins'],
                    'subject': "${currentBuild.displayName}: Upgrade All-tier Automation Results Available",
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
        currentBuild.result = 'FAILURE'
    }
    finally {
        sh '''
            if [ -f "${UPGRADE_DIR}/upgrade_highlights" ]; then
                cp "${UPGRADE_DIR}/upgrade_highlights" upgrade_highlights
                cp "${UPGRADE_DIR}/full_upgrade" full_upgrade
            fi
        '''
        if(currentBuild.result == 'FAILURE') {
            emailUtils.sendEmail(
                'to_nicks': ['sat-qe-jenkins'],
                'reply_nicks': ['sat-qe-jenkins'],
                'subject': "${currentBuild.result}: Upgrade All-tier tests ${currentBuild.displayName}",
                'body': '${FILE, path="upgrade_highlights"}\n' +
                        "The build ${currentBuild.displayName} has been ${currentBuild.result}. \n\n Refer ${env.BUILD_URL} for more details.",
                'mimeType': 'text/plain',
                'attachmentsPattern': 'full_upgrade'
            )
        }

        stage('Finish the report portal launch') {
            if (launch_uuid){
                reportPortalUtils.finish_launch(launch_uuid: launch_uuid)
            }
            else {
                println("No launch_uuid: Skipping 'Finish the Report Portal Launch' stage")
                Utils.markStageSkippedForConditional(STAGE_NAME)
            }
        }

        stage('Check-in upgrade instances') {
            brokerUtils.checkin_all()
        }
    }
}
