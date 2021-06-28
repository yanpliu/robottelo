// Helper functions for upgrades

def previous_version(String sat_version) {
    // Calculates and Returns the previous major release from sat_verion
    def (major, minor) = sat_version.tokenize('.')
    def last_version = [major, minor.toInteger()-1].join('.')
    return last_version
}

def integrate_satellite_capsule(Map parameters = [:]) {
    println("Integrating Satellites with Capsules")
    sh """
        cd \${ROBOTTELO_DIR}
        broker execute --workflow 'satellite-capsule-integration' \
        --artifacts last --additional-arg True \
        --capsule_hostname ${parameters.capsule.hostname} \
        --capsule_name ${parameters.capsule.name} \
        --capsule_rename "true" \
        --satellite_hostname ${parameters.satellite.hostname} \
        --satellite_name ${parameters.satellite.name} \
        --sat_cap_version ${parameters.satellite_version} \
        --rhn_username ${parameters.subscriptions.SUBSCRIPTION.RHN_USERNAME} \
        --rhn_password \${UPGRADE_subscription__rhn_password} \
        --rhn_pool ${parameters.subscriptions.SUBSCRIPTION.RHN_POOLID} \
        --distribution "cdn"
    """
}

def setup_products(Map parameters = [:]) {
    env.satellite_hostname = parameters.satellite.hostname
    env.capsule_hostname = parameters.capsule.hostname
    sh """
        source ~/.bashrc
        cd \${UPGRADE_DIR}
        fab -u root product_setup_for_upgrade_on_brokers_machine:${parameters.product},${parameters.os_ver},${parameters.satellite.hostname},${parameters.capsule.hostname}
    """
}

def upgrade_products(Map parameters = [:]) {
    env.satellite_hostname = parameters.satellite.hostname
    env.capsule_hostname = parameters.capsule.hostname
    sh """
        source ~/.bashrc
        cd \${UPGRADE_DIR}
        fab -u root product_upgrade:${parameters.upgrade_type},${parameters.product},${parameters.satellite.hostname}
    """
}

def parallel_run_func(Map parameters = [:], Closure func) {
    script {
        stepsforparallel = [:]
        stepsforparallel['failFast'] = true
        def product = parameters.get('product', '')
        def os_ver = parameters.get('os_ver', '')
        def satellite_version = parameters.get('version', '')
        def subscriptions = parameters.get('subscriptions', '')
        def upgrade_type = parameters.get('upgrade_type', '')
        for (int i = 0; i < parameters.satellite_inventory.size(); i++) {
            def satellite = parameters.satellite_inventory[i]
            def capsule = parameters.capsule_inventory[i]
            def stepName = "${parameters.stepName}\n${i + 1}"
            stepsforparallel[stepName] = {
                stage(stepName) {
                    func(
                            product: product,
                            satellite: satellite,
                            capsule: capsule,
                            os_ver: os_ver,
                            satellite_version: satellite_version,
                            subscriptions: subscriptions,
                            upgrade_type: upgrade_type
                    )
                }
            }
        }
        parallel stepsforparallel
    }
}

def execute(Map parameters = [:]) {

    // Default artifacts to collect for importance jobs
    defaultArtifacts = ['*-results.xml']
    def artifacts = parameters.get('artifacts', defaultArtifacts)

    returnCode = sh (
            returnStatus: true,
            script: """
                cd \${UPGRADE_DIR}
                set +e
                ${parameters.script}
                pytest_rc=\$?
                set -e
                cp --parents ${artifacts.join(' ')} ${WORKSPACE}
                exit \$pytest_rc
            """
    )
    archiveArtifacts artifacts: artifacts.join(', ')

    return returnCode
}
