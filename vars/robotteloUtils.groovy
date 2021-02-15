def execute(Map params = [:]) {

    // there can be passed default String parameter and is considered as script parameter
    if (params in String) params = [script: params]

    def inventory = params.get('inventory', [])

    for (int i = 0; i < inventory.size(); i++) {
        hostname = inventory[i].hostname
        if (i == 0) {
           env.ROBOTTELO_Server__hostname = hostname
        }
        env."ROBOTTELO_Server__gw${i}" = hostname
    }

    artifacts = ['robottelo*.log', '*-results.xml', '*.properties', 'screenshots.tar.gz']

    def result = sh """
        cd /opt/app-root/src/robottelo
        git log -1
        set +e
        ${params.script}
        tar -czf screenshots.tar.gz screenshots
        set -e
        cp ${artifacts.join(' ')} ${WORKSPACE}
    """
    archiveArtifacts artifacts: artifacts.join(', ')

    return result
}
