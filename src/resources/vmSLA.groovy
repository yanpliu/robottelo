@Library("satqe_pipeline_lib") _

import groovy.json.*
import groovy.transform.Field

// This becomes boilerplate on jobs using broker
def at_vars = [
    containerEnvVar(key: 'BROKER_AnsibleTower__base_url', value: "${params.tower_url}"),
]

openShiftUtils.withNode(image: pipelineVars.ciBrokerImage, envVars: at_vars) {

    stage('Run SLA Job in Ansible Tower') {
        output = sh (
            returnStdout: true,
            script: """broker execute --workflow 'sla-regular-check-wf' \
                             --output-format raw --artifacts merge --additional-arg True \
                             --bad_vms_search_pattern $bad_vms_search_pattern \
                             --default_expire $default_expire \
                             --expiration_warning_period $expiration_warning_period \
                             --expiration_warning_period_unit $expiration_warning_period_unit \
                             --ignore_vm $ignore_vm \
                             --sla_shutdown_period $sla_shutdown_period \
                             --sla_shutdown_period_unit $sla_shutdown_period_unit \
                             --search_pattern $search_pattern
                    """
        )
    }

    stage('Parse Output'){
        def jsonSlurper = new JsonSlurper()
        // groovy data processing
        // fixing JSON format
        def output_json = jsonSlurper.parseText(output.replace("'","\""))
        output_json = output_json['data_out'];
        println(JsonOutput.prettyPrint(JsonOutput.toJson(output_json)));

        user_wise_vms_expiring_soon = [:]
        user_wise_vms_shutdown = [:]
        user_wise_vms_removed = [:]
        for( vm_name in output_json['vms_expiring_soon'] ){
            user = vm_name.split("-")[0]
            if(user_wise_vms_expiring_soon.containsKey(user)){
                user_wise_vms_expiring_soon[user] += [vm_name]
            }
            else
            {
                user_wise_vms_expiring_soon[user] = [vm_name]
            }
        }
        for( vm_name in output_json['shutdown_vms'] ){
            user = vm_name.split("-")[0]
            if(user_wise_vms_shutdown.containsKey(user)){
                user_wise_vms_shutdown[user] += [vm_name]
            }else{
                user_wise_vms_shutdown[user] = [vm_name]
            }
        }
        for( vm_name in output_json['removed_vms'] ){
            user = vm_name.split("-")[0]
            if(user_wise_vms_removed.containsKey(user)){
                user_wise_vms_removed[user] += [vm_name]
            }else{
                user_wise_vms_removed[user] = [vm_name]
            }
        }
        println("User wise VMs expiring soon: " + JsonOutput.prettyPrint(JsonOutput.toJson(user_wise_vms_expiring_soon)));
        println("User wise VMs shutdown:" + JsonOutput.prettyPrint(JsonOutput.toJson(user_wise_vms_shutdown )));
        println("User wise VMs Removed:" + JsonOutput.prettyPrint(JsonOutput.toJson(user_wise_vms_removed)));
        // TODO: Notify Users
    }

    stage ('Build Email and Notify Associates') {
        users = user_wise_vms_expiring_soon.keySet() +
                user_wise_vms_shutdown.keySet() +
                user_wise_vms_removed.keySet() as String[]

        for (user in users) {
                def body = "${user}:<br><br> "
                def subject = "SLA Enforcement ${user} Report for Build ${BUILD_NUMBER}"

                if(user == pipelineVars.towerUser) {
                    println("skipping jenkins user")
                    continue;
                }

                if(user_wise_vms_expiring_soon.containsKey(user)) {
                    body = body + "VMs expiring soon: <br><br>" +
                            JsonOutput.prettyPrint(JsonOutput.toJson(user_wise_vms_expiring_soon.get(user))) + "<br><br>"
                }
                if(user_wise_vms_shutdown.containsKey(user)) {
                    body = body + "VMs Shut down by SLA:<br><br>" +
                            JsonOutput.prettyPrint(JsonOutput.toJson(user_wise_vms_shutdown.get(user))) + "<br><br>"
                }
                if(user_wise_vms_removed.containsKey(user)) {
                    body = body + "VMs Removed by SLA:<br><br>" +
                            JsonOutput.prettyPrint(JsonOutput.toJson(user_wise_vms_removed.get(user)))
                }

                emailUtils.sendEmail(
                    'to_nicks': [user],
                    'reply_nicks': [user],
                    'subject': subject,
                    'body': body
                )
        }
    }
}