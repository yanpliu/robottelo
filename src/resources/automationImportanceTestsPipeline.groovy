@Library("satqe_pipeline_lib") _

import groovy.json.*

withCredentials([usernamePassword(credentialsId: 'ansible-tower-jenkins-user', passwordVariable: 'USERPASS', usernameVariable: 'USERNAME')]) {
    def at_vars = [
            containerEnvVar(key: 'DYNACONF_AnsibleTower__base_url', value: "${params.tower_url}"),
            containerEnvVar(key: 'DYNACONF_AnsibleTower__username', value: "${USERNAME}"),
            containerEnvVar(key: 'DYNACONF_AnsibleTower__password', value: "${USERPASS}")
    ]

    openShiftUtils.withNode(image: pipelineVars.ciRobotteloImage, envVars: at_vars) {

        try {
            stage('Check Out Satellite Instances') {
                inventory = brokerUtils.checkout(
                        'deploy-sat-jenkins': [
                                'sat_version': params.sat_version,
                                'snap_version': params.snap_version,
                                'count': params.appliance_count
                        ],
                )
            }

            stage('Set robottelo.properites') {
                // Hardcoding the RP values for now
                rp_url = "http://reportportal-sat-qe.cloud.paas.psi.redhat.com"
                rp_project = "SatelliteQE"
                sh """
                    crudini --set /opt/app-root/src/robottelo/robottelo.properties report_portal report_portal ${rp_url}
                    crudini --set /opt/app-root/src/robottelo/robottelo.properties report_portal project ${rp_project}
                """
            }

            stage('Execute Automation Test Suite') {
                robotteloUtils.execute(inventory: inventory, script: """
                        py.test -v --importance ${params.importance} -n ${appliance_count} \
                        --junit-xml=sat-${params.importance}-results.xml -o junit_suite_name=sat-${params.importance} \
                        tests/foreman/ 
                    """
                )
                junit "sat-${params.importance}-results.xml"

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