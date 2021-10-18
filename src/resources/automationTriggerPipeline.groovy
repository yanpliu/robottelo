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
                    ['critical', 'high', 'medium', 'low', 'fips'].each {
                        build job: "sat-${sat_version.tokenize('.').take(2).join('.')}-${params.os}-${it}",
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

        stage('Trigger Foreman-Maintain Automation Tests') {
            steps {
                script {
                    ['Satellite', 'Capsule'].each {
                        build job: "sat-${sat_version.tokenize('.').take(2).join('.')}-${params.os}-maintain",
                        parameters: [
                            [$class: 'StringParameterValue', name: 'component', value: "${it}"],
                            [$class: 'StringParameterValue', name: 'snap_version', value: params.snap_version],
                            [$class: 'StringParameterValue', name: 'sat_version', value: params.sat_version],
                            [$class: 'StringParameterValue', name: 'pytest_options', value: (it=='Capsule')?'-m capsule tests/':'tests/'],
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
