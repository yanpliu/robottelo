@Library("satqe_pipeline_lib") _

import groovy.json.*

def os_ver = "${params.os}"
def to_version = sat_version.tokenize('.').take(2).join('.')
def from_version = ("${params.stream}" == 'z_stream')? to_version : upgradeUtils.previous_version(to_version)

def at_vars = [
        containerEnvVar(key: 'BROKER_AnsibleTower__base_url', value: "${params.tower_url}"),
        containerEnvVar(key: 'UPGRADE_UPGRADE__FROM_VERSION', value: "'${from_version}'"),
        containerEnvVar(key: 'UPGRADE_UPGRADE__TO_VERSION', value: "'${to_version}'"),
        containerEnvVar(key: 'UPGRADE_UPGRADE__OS', value: os_ver),
        containerEnvVar(key: 'UPGRADE_UPGRADE__DISTRIBUTION', value: params.distribution),
        containerEnvVar(key: 'UPGRADE_UPGRADE__DOWNSTREAM_FM_UPGRADE', value: "${params.downstream_fm_upgrade}"),
        containerEnvVar(key: 'UPGRADE_UPGRADE__FOREMAN_MAINTAIN_SATELLITE_UPGRADE', value: "${params.foreman_maintain_satellite_upgrade}"),
]

openShiftUtils.withNode(image: pipelineVars.ciUpgradesImage, envVars: at_vars) {
    try {
        stage('Check out satellite of GA version') {
            satellite_inventory = brokerUtils.checkout(
                'deploy-satellite-upgrade': [
                    'deploy_sat_version' : from_version,
                    'deploy_scenario'    : 'satellite-upgrade',
                    'deploy_rhel_version': os_ver[-1]
                ],
            )
            env.satellite_hostname = satellite_inventory[0].hostname
            env.capsule_hostnames = ''
            calculated_build_name = from_version + " to " + sat_version + " snap: " + "${params.snap_version}"
            currentBuild.displayName = "${params.build_label}" ?: calculated_build_name
            env.ROBOTTELO_robottelo__satellite_version = "'${to_version}'"
            env.UPGRADE_robottelo__satellite_version = "'${to_version}'"
        }

        stage('Setup ssh-agent') {
            sh """
                echo \"\${USER_NAME:-default}:x:\$(id -u):0:\${USER_NAME:-default} user:\${HOME}:/sbin/nologin\" >> /etc/passwd
                echo \"\$(ssh-agent -s)\" >> ~/.bashrc
                source ~/.bashrc
                ssh-add - <<< \$SATLAB_PRIVATE_KEY
            """
        }

        stage('Collect API and CLI data before upgrade') {
            default_artifacts = ['preupgrade_cli', 'preupgrade_api', 'preupgrade_templates.tar.gz']
            sh """
                cd \${UPGRADE_DIR}
                source ~/.bashrc
                fab -u root set_datastore:"preupgrade","cli",\${satellite_hostname}
                fab -u root set_datastore:"preupgrade","api",\${satellite_hostname}
                fab -u root set_templatestore:"preupgrade",\${satellite_hostname}
                tar --ignore-failed-read -czf preupgrade_templates.tar.gz preupgrade_templates
                cp --parents ${default_artifacts.join(' ')} ${WORKSPACE}
            """
            archiveArtifacts artifacts: default_artifacts.join(', ')
        }

        stage("Setup products for upgrade") {
            sh """
                cd \${UPGRADE_DIR}
                source ~/.bashrc
                fab -u root product_setup_for_upgrade_on_brokers_machine:"satellite","${params.os}",'${satellite_hostname}','${capsule_hostnames}'
            """
        }

        stage("Satellite upgrade") {
            sh """
                cd \${UPGRADE_DIR}
                source ~/.bashrc
                fab -u root product_upgrade:'satellite','satellite',\${satellite_hostname}
            """
        }

        stage('Collect API and CLI data after upgrade') {
            default_artifacts = ['postupgrade_cli', 'postupgrade_api', 'postupgrade_templates.tar.gz']
            sh """
                cd \${UPGRADE_DIR}
                source ~/.bashrc
                fab -u root set_datastore:"postupgrade","cli",\${satellite_hostname}
                fab -u root set_datastore:"postupgrade","api",\${satellite_hostname}
                fab -u root set_templatestore:"postupgrade",\${satellite_hostname}
                tar --ignore-failed-read -czf postupgrade_templates.tar.gz postupgrade_templates
                cp --parents ${default_artifacts.join(' ')} ${WORKSPACE}
            """
            archiveArtifacts artifacts: default_artifacts.join(', ')
        }

        stage("Run Existence Tests for CLI endpoint") {
            upgradeUtils.execute(
                script: """
                    export UPGRADE_UPGRADE__EXISTENCE_TEST__ENDPOINT='cli'
                    py.test -v --continue-on-collection-errors --junit-xml=test_existance_cli-results.xml \
                    -o junit_suite_name=test_existance_cli upgrade_tests/test_existance_relations/cli/
                """
            )
            junit "test_existance_cli-results.xml"
        }

        stage("Run Existence Tests for API endpoint") {
            upgradeUtils.execute(
                script: """
                    export UPGRADE_UPGRADE__EXISTENCE_TEST__ENDPOINT='api'
                    py.test -v --continue-on-collection-errors --junit-xml=test_existance_api-results.xml \
                    -o junit_suite_name=test_existance_api upgrade_tests/test_existance_relations/api/
                """
            )
            junit "test_existance_api-results.xml"
        }

        currentBuild.result = 'SUCCESS'
    }

    catch (exc) {
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

        emailUtils.sendEmail(
            'to_nicks': ["sat-qe-jenkins"],
            'reply_nicks': ["sat-qe-jenkins"],
            'subject': "${currentBuild.result}: Upgrade Existence Tests status from ${from_version} to ${sat_version} snap: ${snap_version} on ${os_ver}",
            'body': '${FILE, path="upgrade_highlights"}' + "The build ${env.BUILD_URL} has been completed.",
            'mimeType': 'text/plain',
            'attachmentsPattern': 'full_upgrade'
        )
    }
}
