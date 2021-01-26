def execute(Map params = [:]) {

    // there can be passed default String parameter and is considered as script parameter
    if (params in String) params = [script: params]

    def inventory = params.get('inventory', [])

    // Use DYNACONF when https://projects.engineering.redhat.com/browse/SATQE-11729 is finished
    for (int i = 0; i < inventory.size(); i++) {
        hostname = inventory[i].hostname
        if (i == 0) {
            sh "crudini --set \${ROBOTTELO_DIR}/robottelo.properties server hostname ${hostname}"
        }
        sh "crudini --set \${ROBOTTELO_DIR}robottelo.properties server gw${i} ${hostname}"
    }

    artifacts = ['robottelo*.log', '*-results.xml', '*.properties']

    def result = sh """
        cd /opt/app-root/src/robottelo
        git log -1
        set +e
        ${params.script}
        set -e
        cp ${artifacts.join(' ')} ${WORKSPACE}
    """
    archiveArtifacts artifacts: artifacts.join(', ')

    return result
}

