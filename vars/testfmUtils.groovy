def execute(Map parameters = [:]) {

    // there can be passed default String parameter and is considered as script parameter
    if (parameters in String) parameters = [script: parameters]

    def inventory = parameters.get('inventory', [])

    for (int i = 0; i < inventory.size(); i++) {
        hostname = inventory[i].hostname
        if (i == 0) {
            sh "sed -i 's/<server_hostname>/${hostname}/g' \${TESTFM_DIR}/testfm/inventory"
            sh """
                echo \"\${USER_NAME:-default}:x:\$(id -u):0:\${USER_NAME:-default} user:\${HOME}:/sbin/nologin\" >> /etc/passwd
                echo \"\$(ssh-agent -s)\" >> ~/.bashrc
                source ~/.bashrc
                ssh-add - <<< \$SATLAB_PRIVATE_KEY
            """
            def FM_VER = sh(
                returnStdout: true,
                script: """
                    source ~/.bashrc &> /dev/null
                    ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -o LogLevel=ERROR root@${hostname} rpm --queryformat='%{VERSION}' -q rubygem-foreman_maintain
                """
            )
            println(FM_VER)
            currentBuild.description += ' Ver.' + FM_VER
        }
    }

    def result = sh """
            source ~/.bashrc
            cd \${TESTFM_DIR}
            git log -1
            set +e
            ${parameters.script}
            set -e
            cp *.log *-results.xml ${WORKSPACE}
    """
    archiveArtifacts artifacts: '*.log, *-results.xml'

    return result
}
