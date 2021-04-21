@Library("satqe_pipeline_lib") _

import groovy.json.*

def at_vars = [
    containerEnvVar(key: 'BROKER_AnsibleTower__base_url', value: "${params.tower_url}"),
]

openShiftUtils.withNode(image: pipelineVars.ciBrokerImage, envVars: at_vars) {

    String[] versions = params.rhel_version.split(",")
    def output_rhel_ga = [:]
    for (String version: versions) {
        stage('Update RHEL GA ' + version + ' Template') {
            // Call RHEL GA Template Workflow
            output_wf =
                    sh(
                        returnStdout: true,
                        script:
                            """
                                broker execute --workflow 'update-rhel-ga-template' \
                                --output-format raw --artifacts last --additional-arg True \
                                --rhel_version ${version}
                            """
                    )
            def jsonSlurper = new JsonSlurper()
            // groovy data processing
            // fixing JSON format and removing 'template' key
            def output_json = jsonSlurper.parseText(output_wf.replace("'","\""))
            output_rhel_ga["${version}"] = output_json['template']
        }
    }

    stage ('Parse Broker Output') {
        println("RHEL Version and Template names are: " + JsonOutput.prettyPrint(JsonOutput.toJson(output_rhel_ga)));
        if (output_rhel_ga.every {it.value}) {
            print "RHEL GA Templates have been updated"
            email_to = ['sat-qe-jenkins', 'satellite-qe-tower-users']
            subject = "RHEL ${params.rhel_version} GA Template Is Now Available and Updated"
        } else {
            print "One or more template names were empty"
            email_to = ['sat-qe-jenkins', 'satellite-lab-list']
            subject = "${env.JOB_NAME} Build ${BUILD_NUMBER} has Failed. Please Investigate"
            currentBuild.result = 'UNSTABLE'
        }

    }

    stage('Build Notification') {
        emailUtils.sendEmail(
            'to_nicks': email_to,
            'reply_nicks': email_to,
            'subject': subject,
            'body':"${BUILD_URL}"
        )
    }
}
