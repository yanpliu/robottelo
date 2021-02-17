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

    returnCode = sh (
            returnStatus: true,
            script: """
                cd \${ROBOTTELO_DIR}
                git log -1
                set +e
                ${params.script}
                pytest_rc=\$?
                tar -czf screenshots.tar.gz screenshots
                set -e
                cp ${artifacts.join(' ')} ${WORKSPACE}
                exit \$pytest_rc
            """
    )
    archiveArtifacts artifacts: artifacts.join(', ')

    return returnCode
}
