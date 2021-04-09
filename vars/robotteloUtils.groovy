def execute(Map params = [:]) {

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
