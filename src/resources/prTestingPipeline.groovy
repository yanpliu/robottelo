@Library("satqe_pipeline_lib") _


withCredentials([usernamePassword(credentialsId: 'ansible-tower-jenkins-user', passwordVariable: 'USERPASS', usernameVariable: 'USERNAME')]) {
    def at_vars = [
            containerEnvVar(key: 'DYNACONF_AnsibleTower__base_url', value: "${params.tower_url}"),
            containerEnvVar(key: 'DYNACONF_AnsibleTower__username', value: "${USERNAME}"),
            containerEnvVar(key: 'DYNACONF_AnsibleTower__password', value: "${USERPASS}")
    ]

    throttle(['pr-tester']) {

        openShiftUtils.withNode(image: pipelineVars.ciRobotteloImage, envVars: at_vars) {

                stage('Load Github Comment Config'){

                    comment = env.ghprbCommentBody
                    comment = comment.replaceAll("\\\\r\\\\n", "\r\n")
                    pytest_options = ""
                    config = ""
                    config = readYaml text: comment
                }

                // In below block, we can not merge the PR unless has the valid git configs.
                stage("Checkout Test PR"){
                    sh """
                        cd /opt/app-root/src/robottelo/
                        git fetch origin
                        git fetch origin refs/pull/${env.ghprbPullId}/head:refs/remotes/origin/pr/${env.ghprbPullId}
                        git config --local user.name "Omkar Khatavkar"
                        git config --local user.email okhatavkar007@gmail.com
                        git merge origin/pr/${env.ghprbPullId}
                        git log -n 5
                    """
                }

                stage("Pip Update"){
                    sh """
                        cd /opt/app-root/src/robottelo/
                        pip install --upgrade pip
                        pip install -U -r requirements.txt
                    """
                }

                stage("Checkout Airgun/Nailgun Code"){
                    if(config.get('airgun')){
                        sh """
                            pip uninstall airgun -y
                            pip install git+https://github.com/SatelliteQE/airgun.git@refs/pull/${config.get('airgun')}/head

                        """
                    }
                    if(config.get('nailgun')){
                        sh """
                            pip uninstall nailgun -y
                            pip install git+https://github.com/SatelliteQE/nailgun.git@refs/pull/${config.get('nailgun')}/head

                        """
                    }
                }

        try {
                stage('Check Out Satellite Instances') {
                    inventory = brokerUtils.checkout(
                        'deploy-sat-jenkins': [
                            'sat_version': 'latest',
                            'count': 1
                        ],
                    )
                }

                stage("Running Tests"){
                    pytest_command = "py.test /opt/app-root/src/robottelo/tests/foreman -v -m 'build_sanity'"
                    if(config.get('pytest')) {
                        pytest_command = "py.test ${config.get('pytest')}"
                    }
                    robotteloUtils.execute(inventory: inventory, script: """
                        $pytest_command \
                        --junit-xml=sat-results.xml \
                        -o junit_suite_name=sat-result \
                            """)

                    junit "sat-results.xml"

                }
            }

        finally {
                if(inventory) {
                    stage('Check In Satellite Instances') {
                        brokerUtils.checkin_all()
                    }
                }
            }
        }
    }
}
