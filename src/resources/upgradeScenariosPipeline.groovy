@Library("satqe_pipeline_lib") _

import groovy.json.*
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils

def to_version = params.sat_version.tokenize('.').take(2).join('.')
def from_version = ("${params.stream}" == 'z_stream')? to_version : upgradeUtils.previous_version(to_version)
def upgrade_base_version = params.specific_upgrade_base_version?specific_upgrade_base_version:from_version
def resource_type =  ("$to_version" == '6.10')?'UpgradeTemplate':'Default'

def at_vars = [
    containerEnvVar(key: 'BROKER_AnsibleTower__base_url', value: "${params.tower_url}"),
    containerEnvVar(key: 'BROKER_AnsibleTower__username', value: pipelineVars.towerUpgradesUser),
    containerEnvVar(key: 'ROBOTTELO_ROBOTTELO__SATELLITE_VERSION', value: "'${to_version}'"),
    containerEnvVar(key: 'ROBOTTELO_SERVER__XDIST_BEHAVIOR', value: 'balance'),
    containerEnvVar(key: 'ROBOTTELO_SERVER__INVENTORY_FILTER', value: 'name<satellite-upgrade'),
    containerEnvVar(key: 'ROBOTTELO_SERVER__VERSION__RELEASE', value: "'${to_version}'"),
    containerEnvVar(key: 'ROBOTTELO_SERVER__VERSION__SNAP', value: "'${params.snap_version}'"),
    containerEnvVar(key: 'UPGRADE_ROBOTTELO__SATELLITE_VERSION', value: "'${to_version}'"),
    containerEnvVar(key: 'UPGRADE_UPGRADE__FROM_VERSION', value: "'${from_version}'"),
    containerEnvVar(key: 'UPGRADE_UPGRADE__TO_VERSION', value: "'${to_version}'"),
    containerEnvVar(key: 'UPGRADE_UPGRADE__OS', value: params.os),
    containerEnvVar(key: 'UPGRADE_UPGRADE__DISTRIBUTION', value: params.distribution),
    containerEnvVar(key: 'UPGRADE_UPGRADE__DOWNSTREAM_FM_UPGRADE', value: "${params.downstream_fm_upgrade}"),
    containerEnvVar(key: 'UPGRADE_UPGRADE__FOREMAN_MAINTAIN_SATELLITE_UPGRADE', value: "${params.foreman_maintain_satellite_upgrade}"),
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
                        'deploy_sat_version' : upgrade_base_version,
                        'deploy_scenario'    : 'satellite-upgrade',
                        'deploy_rhel_version': params.os[-1],
                        'count'              : params.xdist_workers,
                        'target_cores'       : pipelineVars.upgrade_resources[resource_type]['target_cores'],
                        'target_memory'      : pipelineVars.upgrade_resources[resource_type]['target_memory'],

                ],
            )
            all_inventory = brokerUtils.checkout(
                'deploy-capsule-upgrade': [
                        'deploy_sat_version' : upgrade_base_version,
                        'deploy_scenario'    : 'capsule-upgrade',
                        'deploy_rhel_version': params.os[-1],
                        'count'              : params.xdist_workers,
                ],
            )
            // Filter Capsule inventory from all inventory
            capsule_inventory = []
            all_inventory.each {
                if (it._broker_args.host_type == 'capsule') capsule_inventory.add(it)
            }

            // Subscriptions
            sh 'cp ${ROBOTTELO_DIR}/conf/subscription.yaml ${WORKSPACE}/subscription.yaml'
            subscriptions = readYaml file: "${WORKSPACE}/subscription.yaml"

            // Integrate satellite and capsule
            upgradeUtils.parallel_run_func(
                upgradeUtils.&integrate_satellite_capsule,
                stepName: "Satellite and Capsule",
                satellite_inventory: satellite_inventory,
                capsule_inventory: capsule_inventory,
                version: from_version, subscriptions: subscriptions
            )
            // Buiild Description
            calculated_build_name = from_version + " to " + "${params.sat_version}" + " snap: " + "${params.snap_version}"
            currentBuild.displayName = "${params.build_label}" ?: calculated_build_name
            // Set the RHEL version environment variable
            env.ROBOTTELO_server__version__rhel_release = "'${inventory[0].os_distribution_version}'"
        }

        stage("Setup ssh-agent"){
            sh """
                echo \"\${USER_NAME:-default}:x:\$(id -u):0:\${USER_NAME:-default} user:\${HOME}:/sbin/nologin\" >> /etc/passwd
                echo \"\$(ssh-agent -s)\" >> ~/.bashrc
                source ~/.bashrc
                ssh-add - <<< \$SATLAB_PRIVATE_KEY
            """
        }

        stage("Setup products for upgrade") {
            upgradeUtils.parallel_run_func(
                upgradeUtils.&setup_products,
                stepName: "Satellite and Capsule",
                product: 'capsule',
                os_ver: params.os,
                satellite_inventory: satellite_inventory,
                capsule_inventory: capsule_inventory
            )
        }

        stage("Run pre-upgrade scenarios") {
            if (params.stream == 'y_stream') {
                sh """
                    source ~/.bashrc
                    cd \${ROBOTTELO_DIR}
                    pip uninstall -y nailgun
                    pip install git+https://github.com/SatelliteQE/nailgun.git@"${from_version}".z#egg=nailgun
                    export ROBOTTELO_ROBOTTELO__SATELLITE_VERSION="${from_version}"
                """
            }

            return_code = robotteloUtils.execute(script: """
                py.test -s -v -rEfs --tb=line -m pre_upgrade \
                --continue-on-collection-errors \
                -n ${params.xdist_workers} \
                --dist loadscope \
                --junit-xml=test_scenarios-pre-results.xml \
                -o junit_suite_name=test_scenarios-pre \
                tests/upgrades
            """)
            sh "cp \${ROBOTTELO_DIR}/upgrade_workers.json upgrade_workers.json"

            archiveArtifacts artifacts: 'upgrade_workers.json'

            junit "test_scenarios-pre-results.xml"
        }

        stage("Satellite upgrade") {
            upgradeUtils.parallel_run_func(
                upgradeUtils.&upgrade_products,
                stepName: "Satellite",
                upgrade_type: 'capsule',
                product: 'satellite',
                satellite_inventory: satellite_inventory,
                capsule_inventory: capsule_inventory
            )
        }

        stage("Capsule upgrade") {
            upgradeUtils.parallel_run_func(
                upgradeUtils.&upgrade_products,
                stepName: "Capsule",
                upgrade_type: 'capsule',
                product: 'capsule',
                satellite_inventory: satellite_inventory,
                capsule_inventory: capsule_inventory
            )
        }

        stage("Run post-upgrade scenarios") {
            if (params.stream == 'y_stream') {
                sh """
                    source ~/.bashrc
                    cd \${ROBOTTELO_DIR}
                    pip uninstall -y nailgun
                    pip install nailgun
                    export ROBOTTELO_ROBOTTELO__SATELLITE_VERSION="${to_version}"
                """
            }

            return_code = robotteloUtils.execute(script: """
                py.test -s -v -rEfs --tb=line -m post_upgrade \
                --continue-on-collection-errors \
                --durations=20 --durations-min=600.0 \
                -n ${params.xdist_workers} \
                --junit-xml=test_scenarios-post-results.xml \
                -o junit_suite_name=test_scenarios-post \
                tests/upgrades
            """)
            results_summary = junit "test_scenarios-post-results.xml"
            currentBuild.result = 'SUCCESS'
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
                    'subject': "${currentBuild.displayName}: Upgrade Customer Scenarios Automation Results Available",
                    'body': email_body.stripIndent()
                )
            }
            else {
                    println("The build is failed due to product or environment-related issues, hence skipping the Email stage")
                    Utils.markStageSkippedForConditional(STAGE_NAME)
            }
        }
    }
    catch (exc){
        echo "Catch Error: \n${exc}"
        currentBuild.result = 'FAILURE'
    }
    finally {
        sh '''
            if [ -f "${UPGRADE_DIR}/upgrade_highlights" ]; then
                cp "${UPGRADE_DIR}/upgrade_highlights" upgrade_highlights
                cp "${UPGRADE_DIR}/full_upgrade" full_upgrade
            fi
        '''
        stage('Check-in upgrade instances') {
            brokerUtils.checkin_all()
        }
        if(currentBuild.result == 'FAILURE'){
            emailUtils.sendEmail(
                'to_nicks': ["sat-qe-jenkins"],
                'reply_nicks': ["sat-qe-jenkins"],
                'subject': "${currentBuild.result}: Upgrade Scenarios ${currentBuild.displayName}",
                'body': '${FILE, path="upgrade_highlights"}\n' +
                        "The build ${currentBuild.displayName} has been ${currentBuild.result}. \n\n Refer ${env.BUILD_URL} for more details.",
                'mimeType': 'text/plain',
                'attachmentsPattern': 'full_upgrade'
            )
        }
    }
}
