pipeline {
    agent any
    stages {
        stage('Set Build Description'){
            steps {
                script {
                    currentBuild.description = sat_version + " snap: " + snap_version
                }
            }
        }

        stage('Trigger Importance Automation Tests') {
            steps {
                script {
                    ['critical', 'high', 'medium', 'low'].each {
                        build job: "sat-${sat_version.tokenize('.').take(2).join('.')}-${it}-tests",
                        parameters: [
                            [$class: 'StringParameterValue', name: 'snap_version', value: params.snap_version],
                            [$class: 'StringParameterValue', name: 'sat_version', value: params.sat_version],
                            [$class: 'StringParameterValue', name: 'tower_url', value: params.tower_url]
                        ],
                        wait: false
                    }
                }
            }
        }

        // stage('Trigger Upgrade Automation Tests') {}
    }
}