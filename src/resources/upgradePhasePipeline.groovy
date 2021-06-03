@Library("satqe_pipeline_lib") _

import groovy.json.*

def to_version = params.sat_version
def from_version = params['zstream_upgrade']? params.sat_version : upgradeUtils.previous_version(sat_version)
def upgrade_base_version = params.specific_upgrade_base_version?specific_upgrade_base_version:from_version
def at_vars = [
        containerEnvVar(key: 'BROKER_AnsibleTower__base_url', value: "${params.tower_url}"),
        containerEnvVar(key: 'ROBOTTELO_ROBOTTELO__SATELLITE_VERSION', value: "'${params.sat_version}'"),
        containerEnvVar(key: 'UPGRADE_ROBOTTELO__SATELLITE_VERSION', value: "'${params.sat_version}'"),
        containerEnvVar(key: 'UPGRADE_UPGRADE__FROM_VERSION', value: "'${from_version}'"),
        containerEnvVar(key: 'UPGRADE_UPGRADE__TO_VERSION', value: "'${to_version}'"),
        containerEnvVar(key: 'UPGRADE_UPGRADE__OS', value: params.os),
        containerEnvVar(key: 'UPGRADE_UPGRADE__ANSIBLE_REPO_VERSION', value: params.ansible_repo_version),
        containerEnvVar(key: 'UPGRADE_UPGRADE__DISTRIBUTION', value: params.distribution),
        containerEnvVar(key: 'UPGRADE_UPGRADE__FOREMAN_MAINTAIN_SATELLITE_UPGRADE', value: "${params.foreman_maintain_satellite_upgrade}"),
        containerEnvVar(key: 'UPGRADE_UPGRADE__DOWNSTREAM_FM_UPGRADE', value: "${params.downstream_fm_upgrade}"),
        containerEnvVar(key: 'UPGRADE_UPGRADE__FOREMAN_MAINTAIN_CAPSULE_UPGRADE', value: "${params.foreman_maintain_capsule_upgrade}"),
        containerEnvVar(key: 'UPGRADE_UPGRADE__SATELLITE_CAPSULE_SETUP_REBOOT', value: "${params.satellite_capsule_setup_reboot}"),
        containerEnvVar(key: 'UPGRADE_UPGRADE__UPGRADE_WITH_HTTP_PROXY', value: "${params.upgrade_with_http_proxy}"),
]

openShiftUtils.withNode(image: pipelineVars.ciUpgradesImage, envVars: at_vars) {
    try {
        stage('Check Out Satellite and capsule upgrade Instances') {
            if (! params.external_satellite_hostname.trim()){
                satellite_inventory = brokerUtils.checkout(
                    'deploy-satellite-upgrade': [
                        'deploy_sat_version': upgrade_base_version,
                        'deploy_scenario': 'satellite-upgrade',
                        'deploy_rhel_version': params.os[-1],
                        ],
                )
                env.satellite_hostname = satellite_inventory[0].hostname
                env.satellite_name = satellite_inventory[0].name
                env.capsule_hostnames = ''

                if (params.upgrade_type != "satellite" && params.upgrade_type != "client") {
                    capsule_inventory = brokerUtils.checkout(
                    'deploy-capsule-upgrade': [
                        'deploy_sat_version': upgrade_base_version,
                        'deploy_scenario': 'capsule-upgrade',
                        'deploy_rhel_version': params.os[-1],
                        'count': params.capsule_count,
                        ],
                    )
                    capsule_inventory.remove(capsule_inventory.indexOf(satellite_inventory[0]))
                    sh 'cp "${UPGRADE_DIR}/conf/subscription.yaml" subscription.yaml'
                    subscription_detail = readYaml file: 'subscription.yaml'
                    env.rhn_username = subscription_detail.SUBSCRIPTION.RHN_USERNAME
                    env.rhn_pool = subscription_detail.SUBSCRIPTION.RHN_POOLID

                    def cap_hosts = ""
                    for (cap in capsule_inventory) {
                        env.capsule_hostname = cap.hostname
                        env.capsule_name =  cap.name
                        sat_cap_integration =
                            sh(
                                returnStdout: true,
                                script: """
                                    broker execute --workflow 'satellite-capsule-integration' \
                                    --output-format raw --artifacts last --additional-arg True \
                                    --capsule_hostname ${capsule_hostname} \
                                    --capsule_name ${capsule_name} \
                                    --capsule_rename "true" \
                                    --satellite_hostname ${satellite_hostname} \
                                    --satellite_name ${satellite_name} \
                                    --sat_cap_version ${from_version} \
                                    --rhn_username ${rhn_username} \
                                    --rhn_password \${UPGRADE_subscription__rhn_password} \
                                    --rhn_pool ${rhn_pool} \
                                    --distribution ${params.distribution}
                                """
                            )
                        cap_hosts = cap_hosts + " ${cap.hostname}"
                     }
                     env.capsule_hostnames = cap_hosts
                }
            }

            else {
                env.satellite_hostname = params.external_satellite_hostname
                env.capsule_hostnames = params.external_capsule_hostnames
            }
            currentBuild.displayName = "#${env.BUILD_NUMBER} upgrade_${from_version}_to_${sat_version} ${params.build_label}"
        }
        stage("Setup ssh-agent"){
            sh """
                echo \"\${USER_NAME:-default}:x:\$(id -u):0:\${USER_NAME:-default} user:\${HOME}:/sbin/nologin\" >> /etc/passwd
                echo \"\$(ssh-agent -s)\" >> ~/.bashrc
                source ~/.bashrc
                ssh-add - <<< \$SATLAB_PRIVATE_KEY
            """
        }
        stage("Setup products for upgrade"){
            sh """
                cd \${UPGRADE_DIR}
                source ~/.bashrc
                fab -u root product_setup_for_upgrade_on_brokers_machine:"${params.upgrade_type}","${params.os}",'${satellite_hostname}',"${capsule_hostnames}"
            """
        }
        stage("Satellite upgrade"){
            sh """
                cd \${UPGRADE_DIR}
                source ~/.bashrc
                fab -u root product_upgrade:"${params.upgrade_type}",'satellite'
            """
        }
        stage("Capsule upgrade"){
            sh """
                cd \${UPGRADE_DIR}
                source ~/.bashrc
                fab -u root product_upgrade:"${params.upgrade_type}",'capsule'
            """
        }
        stage("Content host upgrade"){
            sh """
                cd \${UPGRADE_DIR}
                source ~/.bashrc
                fab -u root product_upgrade:"${params.upgrade_type}",'client'
            """
        }
        currentBuild.result = 'SUCCESS'
        sh '''
            if [ -f "${UPGRADE_DIR}/upgrade_highlights" ]; then
                cp "${UPGRADE_DIR}/upgrade_highlights" upgrade_highlights
                cp "${UPGRADE_DIR}/full_upgrade" full_upgrade
            fi
           '''
    }
    catch (exc){
        echo "Catch Error: \n${exc}"
        currentBuild.result = 'FAILURE'
    }
    finally {
        if((! params.setup_preserve) && (! params.external_satellite_hostname.trim())) {
            stage('Check In Satellite Instances') {
                    brokerUtils.checkin_all()
                }
            }
        emailext(
            to: "sat-qe-jenkins@redhat.com",
            subject: "Upgrade Status ${from_version} to ${sat_version} on ${os} ${BUILD_LABEL} ${currentBuild.result}",
            body: '${FILE, path="upgrade_highlights"}' + "The build ${env.BUILD_URL} has been completed.",
            attachmentsPattern: 'full_upgrade'
        )
    }
}
