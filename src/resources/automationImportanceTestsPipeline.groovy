@Library("satqe_pipeline_lib") _

import groovy.json.*

withCredentials([usernamePassword(credentialsId: 'ansible-tower-jenkins-user', passwordVariable: 'USERPASS', usernameVariable: 'USERNAME')]) {
    def at_vars = [
            containerEnvVar(key: 'DYNACONF_AnsibleTower__base_url', value: "${params.tower_url}"),
            containerEnvVar(key: 'DYNACONF_AnsibleTower__username', value: "${USERNAME}"),
            containerEnvVar(key: 'DYNACONF_AnsibleTower__password', value: "${USERPASS}")
    ]

    openShiftUtils.withNode(image: pipelineVars.ciRobotteloImage, envVars: at_vars) {

        stage('Check Out Satellite Instances') {
            inventory = brokerUtils.checkout(
                    'deploy-sat-jenkins':[
                            'sat_version': params.sat_version,
                            'snap_version': params.snap_version,
                            'count': params.appliance_count
                    ],
            )
        }

        stage('Set robottelo.properites') {
            // Use DYNACONF when https://projects.engineering.redhat.com/browse/SATQE-11729 is finished
            for (int i = 0; i < appliance_count.toInteger(); i++) {
                hostname = inventory[i].hostname
                if(i==0) {
                    sh """
                        cd /opt/app-root/src/robottelo
                        crudini --set robottelo.properties server hostname ${hostname}
                    """
                }
                sh """
                    cd /opt/app-root/src/robottelo
                    crudini --set robottelo.properties server gw[${i}] ${hostname}
                """
            }
        }

        stage('Execute Automation Test Suite') {
            sh """
                    cd /opt/app-root/src/robottelo
                    git show
                    py.test -v --importance ${params.importance} \
                        --junit-xml=sat-jenkins-auto-results.xml -o junit_suite_name=sat-jenkins-auto-results \
                        tests/[^upgrade]*/ -n ${appliance_count}

                    cp robottelo*.log robottelo.properties sat-jenkins-auto-results.xml ${WORKSPACE}
                """
                archiveArtifacts artifacts: 'robottelo*.log, sat-jenkins-auto-results.xml, robottelo.properties'
                junit 'sat-jenkins-auto-results.xml'

        }

        stage('Check In Satellite Instances') {
            brokerUtils.checkin_all()
        }
    }
}