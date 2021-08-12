@Library("satqe_pipeline_lib") _
import groovy.json.*

def to_version = "${params.sat_version}".tokenize('.').take(2).join('.')
def from_version = ("${params.stream}" == 'z_stream')? to_version : upgradeUtils.previous_version(to_version)
def at_vars = [
        containerEnvVar(key: 'BROKER_AnsibleTower__base_url', value: "${params.tower_url}"),
        containerEnvVar(key: 'UPGRADE_CLONE__CLONE_RPM', value: "${params.clone_rpm}"),
        containerEnvVar(key: 'UPGRADE_CLONE__INCLUDE_PULP_DATA', value: "${params.include_pulp_data}"),
        containerEnvVar(key: 'UPGRADE_CLONE__RESTORECON', value: "${params.restorecon}"),
        containerEnvVar(key: 'UPGRADE_CLONE__CUSTOMER_NAME', value: "${params.customer_name}"),
        containerEnvVar(key: 'UPGRADE_UPGRADE__FROM_VERSION', value: "'${from_version}'"),
        containerEnvVar(key: 'UPGRADE_UPGRADE__TO_VERSION', value: "'${to_version}'"),
        containerEnvVar(key: 'UPGRADE_UPGRADE__OS', value: "${params.os}"),
        containerEnvVar(key: 'UPGRADE_UPGRADE__ANSIBLE_REPO_VERSION', value: "'${params.ansible_repo_version}'"),
        containerEnvVar(key: 'UPGRADE_UPGRADE__DISTRIBUTION', value: "${params.distribution}"),
        containerEnvVar(key: 'UPGRADE_UPGRADE__MONGODB_UPGRADE', value: "${params.mongodb_upgrade}"),
        containerEnvVar(key: 'UPGRADE_UPGRADE__SATELLITE_BACKUP', value: "${params.satellite_backup}"),
        containerEnvVar(key: 'UPGRADE_UPGRADE__FOREMAN_MAINTAIN_SATELLITE_UPGRADE', value: "${params.foreman_maintain_satellite_upgrade}"),
        containerEnvVar(key: 'UPGRADE_UPGRADE__DOWNSTREAM_FM_UPGRADE', value: "${params.downstream_fm_upgrade}"),
        containerEnvVar(key: 'UPGRADE_UPGRADE__SATELLITE_CAPSULE_SETUP_REBOOT', value: "${params.satellite_capsule_setup_reboot}"),
]

openShiftUtils.withNode(image: pipelineVars.ciUpgradesImage, envVars: at_vars) {
    try {
        stage('Check out rhel instance') {
            satellite_inventory = brokerUtils.checkout(
                'deploy-base-rhel': [
                    'target_cores': '8',
                    'target_memory': '24GiB',
                    'rhel_version': '7.9',
                    ],
                )
            env.satellite_hostname = satellite_inventory[0].hostname
            env.satellite_name = satellite_inventory[0].name
            env.capsule_hostnames = ''
            storage_space_expand =
                sh(
                    returnStdout: true,
                    script: """
                        broker execute --workflow 'increase-storage' \
                        --output-format raw --artifacts last --additional-arg True \
                        --rhvm_hostname ${satellite_name}
                    """
                )
            if (params.extend_vm){
                extend_vm =
                    sh(
                        returnStdout: true,
                        script: """
                            broker execute --workflow 'extend-vm' \
                            --output-format raw --artifacts last --additional-arg True \
                            --target_vm ${satellite_name} \
                            --new_expire_time '+518400'
                        """
                    )
             }
            calculated_build_name = "From " + from_version + " To " + "${params.sat_version}" + " Snap: " + "${params.snap_version}"
            currentBuild.displayName = "${params.build_label}" ?: calculated_build_name
            env.ROBOTTELO_robottelo__satellite_version = "'${to_version}'"
            env.UPGRADE_robottelo__satellite_version = "'${to_version}'"
        }
        stage("Setup ssh-agent"){
            sh """
                echo \"\${USER_NAME:-default}:x:\$(id -u):0:\${USER_NAME:-default} user:\${HOME}:/sbin/nologin\" >> /etc/passwd
                echo \"\$(ssh-agent -s)\" >> ~/.bashrc
                source ~/.bashrc
                ssh-add - <<< \$SATLAB_PRIVATE_KEY
            """
        }
        stage("Satellite restore"){
           sh """
            cd \${UPGRADE_DIR}
            source ~/.bashrc
            fab -u root product_setup_for_db_upgrade:'${satellite_hostname}'
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
                fab -u root product_upgrade:"${params.upgrade_type}",'satellite','${satellite_hostname}'
            """
        }
        currentBuild.result = 'SUCCESS'
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

         stage('Check In Satellite Instances') {
            if ((! params.setup_preserve) && (currentBuild.result == 'SUCCESS')) {
                brokerUtils.checkin_all()
            }
          }


        emailUtils.sendEmail(
            'to_nicks': ["sat-qe-jenkins"],
            'reply_nicks': ["sat-qe-jenkins"],
            'subject': "${currentBuild.result}: ${params.customer_name} Db Upgrade Status ${currentBuild.displayName}",
            'body': '${FILE, path="upgrade_highlights"}' + "The build ${env.BUILD_URL} has been completed.",
            'mimeType': 'text/plain',
            'attachmentsPattern': 'full_upgrade'
        )
    }
}
