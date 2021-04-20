def execute(Map parameters = [:]) {

    // Default artifacts to collect for importance jobs
    defaultArtifacts = ['logs/*.log', '*-results.xml', '*.properties', 'screenshots.tar.gz']
    def artifacts = parameters.get('artifacts', defaultArtifacts)

    returnCode = sh (
            returnStatus: true,
            script: """
                cd \${ROBOTTELO_DIR}
                git log -1
                set +e
                ${parameters.script}
                pytest_rc=\$?
                tar --ignore-failed-read -czf screenshots.tar.gz screenshots
                set -e
                cp --parents ${artifacts.join(' ')} ${WORKSPACE}
                exit \$pytest_rc
            """
    )
    archiveArtifacts artifacts: artifacts.join(', ')

    return returnCode
}
