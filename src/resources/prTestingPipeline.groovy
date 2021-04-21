@Library("satqe_pipeline_lib") _


def at_vars = [
        containerEnvVar(key: 'BROKER_AnsibleTower__base_url', value: "${params.tower_url}")
]

throttle(['pr-tester']) {

    openShiftUtils.withNode(image: pipelineVars.ciRobotteloImage, envVars: at_vars) {

            stage('Load Github Comment Config'){
                println("Trigger comment: ${env.ghprbCommentBody}")
                if (!env.ghprbCommentBody.contains("test-robottelo")) {
                    // https://projects.engineering.redhat.com/browse/SATQE-13619
                    currentBuild.description = "BAD TRIGGER COMMENT: " + currentBuild.description
                    error("The trigger comment did not contain the expected string")
                }
                config = readYaml text: env.ghprbCommentBody.replaceAll("\\\\r\\\\n", "\r\n")
                if (!(config instanceof java.util.LinkedHashMap)) {
                    // Have to assume that the comment contained test-robottelo trigger string
                    println("Trigger phrase found, not in expected format, using default")
                    config = ["trigger": "test-robottelo"]
                }
                println("Parsed comment: ${config}")
            }

            // In below block, we can not merge the PR unless has the valid git configs.
            stage("Checkout Test PR"){
                sh """
                    cd \${ROBOTTELO_DIR}
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
                    cd \${ROBOTTELO_DIR}
                    pip install -qU pip
                    pip install -qU -r requirements.txt
                """
            }

            stage("Checkout Airgun/Nailgun Code"){

                if(config.get('airgun')){
                    airgun_link = "https://github.com/SatelliteQE/airgun/pull/${config.get('airgun')}"
                    airgun_status = "<br>Airgun: <a href=${airgun_link}>PR #${config.get('airgun')}</a>"
                    sh """
                        pip uninstall airgun -y
                        pip install git+https://github.com/SatelliteQE/airgun.git@refs/pull/${config.get('airgun')}/head

                    """
                    currentBuild.description = currentBuild.description + airgun_status
                }

                if(config.get('nailgun')){
                    nailgun_link = "https://github.com/SatelliteQE/nailgun/pull/${config.get('nailgun')}"
                    nailgun_status = "<br>Nailgun: <a href=${nailgun_link}>PR #${config.get('nailgun')}</a>"
                    sh """
                        pip uninstall nailgun -y
                        pip install git+https://github.com/SatelliteQE/nailgun.git@refs/pull/${config.get('nailgun')}/head

                    """
                    currentBuild.description = currentBuild.description + nailgun_status
                }

                // Freeze requirements for artifacts, robotteloUtils will artifact robottelo*.log
                sh """
                    cd \${ROBOTTELO_DIR}
                    pip freeze > robottelo-freeze.log
                """

            }

    try {
            stage('Check Out Satellite Instances') {
                brokerUtils.checkout(
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
                robotteloUtils.execute(script: """
                    $pytest_command \
                    --junit-xml=sat-results.xml \
                    -o junit_suite_name=sat-result \
                        """)

                junit "sat-results.xml"

            }
        }

    finally {
            stage('Check In Satellite Instances') {
                brokerUtils.checkin_all()
            }
        }
    }
}

