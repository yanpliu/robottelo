@Library("satqe_pipeline_lib") _

import groovy.json.*
import groovy.transform.Field

def at_vars = [
    containerEnvVar(key: 'BROKER_AnsibleTower__base_url', value: "${params.tower_url}"),
]

openShiftUtils.withNode(image: pipelineVars.ciBrokerImage, envVars: at_vars) {

    stage('Run SLA Job in Ansible Tower') {
        output = sh (
            returnStdout: true,
            script: """broker execute --workflow 'remove-template' \
                       --output-format raw --artifacts merge --additional-arg True \
                       --sat_versions ${params.sat_versions} \
                    """
        )
    }

    stage('Parse Output'){
        def jsonSlurper = new JsonSlurperClassic()
        // groovy data processing
        // fixing JSON format
        output_json = jsonSlurper.parseText(output.replace("'","\""))['data_out'];

        println("pretty-printed json:")
        println(JsonOutput.prettyPrint(JsonOutput.toJson(output_json)));
        tpl_cleanup_pass = output_json['list_templates_remove_pass']
        tpl_cleanup_fail = output_json['list_templates_remove_fail']
        println("removed templates: ${tpl_cleanup_pass}");
        println("failed-to-be-removed templates: ${tpl_cleanup_fail}");
        // TODO: Notify Owners when template ownership gets implemented
    }

    stage ('Build and send E-mail report') {
        def subject = "Template SLA Enforcement status report"
        def body = "<h2>Template SLA report</h2>"
        body = body + "<ul><li><b>Removed templates</b>"
        body = body + "<ul>"
        for (String tpl in tpl_cleanup_pass){
            body = body + "<li>${tpl}</li>"
        }
        body = body + "</ul></li>"
        body = body + "<li><b>Cleanup failed for the following templates</b>"
        body = body + "<ul>"
        for (String tpl in tpl_cleanup_fail){
            body = body + "<li>${tpl}</li>"
        }
        body = body + "</ul></li></ul>"
        emailUtils.sendEmail(
            'to_nicks': ["satellite-qe-tower-users"],
            'reply_nicks': ["satellite-qe-tower-users"],
            'subject': subject,
            'body': body
        )
    }
}
