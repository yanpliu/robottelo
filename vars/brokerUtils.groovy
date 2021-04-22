//Helper functions to spin up new Satellite/Capsule/RHEL instances

def checkout(Map parameters = [:]) {
    /*
    Here is an example of how one could invoke this method:

    brokerUtils.checkout('deploy-base-rhel':['rhel_version': '7', 'count': '2'],
                         'deploy-sat-jenkins':['template': '<sat_jenkins_template>',
                                               'sat_version': '<sat_jenkins_sat_version>',
                                               'snap_version': '<sat_jenkins_snap_version>',
                                               'count':'<sat_jenkins_count>' ],
                         'any-other-wf-name':['param1':'val1', 'param2':'val2'])

    Based on the input map `parameters` where each key is a name of AnsibleTower Workflow,
    and value is a map of `key/value` pairs that are parsed and provided as an input to 
    `broker checkout` as `--key value` pairs.

    Returns:
        - yamlInvString `str`: containing contents of inventory.yaml file post broker checkout.
    */
    println("Deploying (Checking out) instances of following scenarios: " + parameters.keySet())
    for(String workflow in parameters.keySet()){
        def broker_command = """broker checkout --workflow "${workflow}" """

        // construct remaining broker command by parsing key:val pairs in parameters[workflow]
        // All params will be passed with `--key` e.g. for count, broker acccepts `-c` or `--count`
        // In this function assumption is to get all params spelled out(long version)
        parameters[workflow].each { key, val -> broker_command+="--$key $val " }
        // broker_settings.yaml needs to be present in the BROKER_DIRECTORY before running broker commands
        checkout_rc = sh(returnStatus: true, script: "${broker_command}")
        println('Broker RC for checkout: ' + checkout_rc)

    }
    sh 'cp ${BROKER_DIRECTORY}/inventory.yaml ${WORKSPACE}/inventory.yaml'
    def yamlInvString = readYaml file: "${WORKSPACE}/inventory.yaml"

    sh 'cp ${BROKER_DIRECTORY}/logs/broker.log ${WORKSPACE}/broker.$(date +%s).log'

    println("Output Inventory is: " + yamlInvString)
    archiveArtifacts artifacts: 'inventory.yaml, broker*.log'
    return yamlInvString
}

def checkin_all(){
    // function to checkin all the available hosts in the inventory.yaml under BROKER_DIRECTORY dir
    sh 'cp ${BROKER_DIRECTORY}/inventory.yaml ${WORKSPACE}/inventory_pre_checkin_all.yaml'
    checkin_rc = sh (
        returnStatus: true,
        script: """broker checkin --all"""
    )
    println('Broker RC for "checkin --all": ' + checkin_rc)

    sh 'cp ${BROKER_DIRECTORY}/logs/broker.log ${WORKSPACE}/broker.$(date +%s).log'
    archiveArtifacts artifacts: 'inventory_pre_checkin_all.yaml, broker*.log'
}