@Library("satqe_pipeline_lib") _

import groovy.json.*

withCredentials([usernamePassword(credentialsId: 'ansible-tower-jenkins-user', passwordVariable: 'USERPASS', usernameVariable: 'USERNAME')]) {
    def at_vars = [
        containerEnvVar(key: 'DYNACONF_AnsibleTower__base_url', value: "${params.tower_url}"),
        containerEnvVar(key: 'DYNACONF_AnsibleTower__username', value: "${USERNAME}"),
        containerEnvVar(key: 'DYNACONF_AnsibleTower__password', value: "${USERPASS}")
    ]

    openShiftUtils.withNode(image: pipelineVars.ciBrokerImage, envVars: at_vars) {

        String[] versions = params.rhel_version.split(",")
        for (String version: versions) {
            stage('Update RHEL GA ' + version + ' Template') {
                    // Call RHEL GA Template Workflow
                    output_rhel_ga =
                            sh(
                                    returnStdout: true,
                                    script:
                                            """
                                    broker execute --workflow 'update-rhel-ga-template' \
                                    --output-format raw --artifacts last --additional-arg True \
                                    --rhel_version ${version} 
                                """
                            )
            }
        }
    }
}