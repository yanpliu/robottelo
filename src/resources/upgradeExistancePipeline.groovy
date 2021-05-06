@Library("satqe_pipeline_lib") _

import groovy.json.*

def os_ver = "${params.os}"
def to_version = "${params.sat_version}"
def from_version = ("${params.stream}" == 'z-stream')? to_version : upgradeUtils.previous_version(to_version)

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

        stage('__SETUP__\nSatellite and Capsule GA version') {

            satellite_inventory = brokerUtils.checkout(
                'deploy-satellite-upgrade': [
                    'deploy_sat_version' : from_version,
                    'deploy_scenario'    : 'satellite-upgrade',
                    'deploy_rhel_version': os_ver[-1]
                ],
            )
            env.satellite_hostname = satellite_inventory[0].hostname
        }

        stage('__ENV-SETUP__\nSet BuildName, passwd and bashrc') {
            currentBuild.displayName = "# ${env.BUILD_NUMBER} Upgrade_Existence_Tests_from_${from_version}_to_${to_version} ${params.build_label}"
            sh '''
                echo \"\${USER_NAME:-default}:x:\$(id -u):0:\${USER_NAME:-default} user:\${HOME}:/sbin/nologin\" >> /etc/passwd
                echo \"\$(ssh-agent -s)\" >> ~/.bashrc
                ssh-add - <<< \\$SATLAB_PRIVATE_KEY
                source ~/.bashrc
            '''
        }

        stage('__DATASTORE__\nCollect before Upgrade') {
            default_artifacts = ['preupgrade_cli', 'preupgrade_api', 'preupgrade_templates.tar.gz']
            sh """
                source ~/.bashrc
                cd \${UPGRADE_DIR}
                fab -u root set_datastore:"preupgrade","cli",\${satellite_hostname}
                fab -u root set_datastore:"preupgrade","api",\${satellite_hostname}
                fab -u root set_templatestore:"preupgrade",\${satellite_hostname}
                tar --ignore-failed-read -czf preupgrade_templates.tar.gz preupgrade_templates
                cp --parents ${default_artifacts.join(' ')} ${WORKSPACE}
            """
            archiveArtifacts artifacts: default_artifacts.join(', ')
        }

        stage("__SETUP__\nEnvironment setup for Satellite for upgrade") {
            upgradeUtils.setup_products(product: 'satellite', os_ver: os_ver, satellite: satellite_inventory[0])
        }

        stage("__UPGRADE__\nSatellite") {
            sh """
                source ~/.bashrc
                cd \${UPGRADE_DIR}
                fab -u root product_upgrade:'satellite','satellite',\${satellite_hostname}
            """
        }

        stage('__DATASTORE__\nCollect after Upgrade') {
            default_artifacts = ['postupgrade_cli', 'postupgrade_api', 'postupgrade_templates.tar.gz']
            sh """
                source ~/.bashrc
                cd \${UPGRADE_DIR}
                fab -u root set_datastore:"postupgrade","cli",\${satellite_hostname}
                fab -u root set_datastore:"postupgrade","api",\${satellite_hostname}
                fab -u root set_templatestore:"postupgrade",\${satellite_hostname}
                tar --ignore-failed-read -czf postupgrade_templates.tar.gz postupgrade_templates
                cp --parents ${default_artifacts.join(' ')} ${WORKSPACE}
            """
            archiveArtifacts artifacts: default_artifacts.join(', ')
        }

        stage("__TEST__\nExistence Tests - CLI Endpoint") {
            upgradeUtils.execute(
                script: """
                    export UPGRADE_UPGRADE__EXISTENCE_TEST__ENDPOINT='cli'
                    py.test -v --continue-on-collection-errors --junit-xml=test_existance_cli-results.xml \
                    -o junit_suite_name=test_existance_cli upgrade_tests/test_existance_relations/cli/
                """
            )
            junit "test_existance_cli-results.xml"
        }

        stage("__TEST__\nExistence Tests - API Endpoint") {
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
        stage('Check In Satellite and Capsule Instances') {
            brokerUtils.checkin_all()
        }

        emailUtils.sendEmail(
            'to_nicks': ["sat-qe-jenkins"],
            'reply_nicks': ["sat-qe-jenkins"],
            'subject': "${currentBuild.result}: Upgrade Existence Tests Status From ${from_version} To ${to_version} on ${os_ver}",
            'body': "The build ${BUILD_LABEL} has been completed. \n\n Refer ${env.BUILD_URL} for more details."
        )
    }
}
