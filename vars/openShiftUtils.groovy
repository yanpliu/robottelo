// Helpers for spinning up jenkins slaves running on OpenShift and other OpenShift utils


def withNode(Map parameters = [:], Closure body) {
    /*
    Spins up a pod with 2 containers: jnlp, and specified 'image'
    */
    def image = parameters.get('image', pipelineVars.ciRobotteloImage)
    def cloud = parameters.get('cloud', pipelineVars.defaultCloud)
    def jenkinsSlaveImage = parameters.get('jenkinsSlaveImage', pipelineVars.jenkinsSlaveImage)
    def namespace = parameters.get('namespace', pipelineVars.upshiftNameSpace)
    def requestCpu = parameters.get('resourceRequestCpu', "4")
    def limitCpu = parameters.get('resourceLimitCpu', "6")
    def requestMemory = parameters.get('resourceRequestMemory', "4Gi")
    def limitMemory = parameters.get('resourceLimitMemory', "6Gi")
    def jnlpRequestCpu = parameters.get('jnlpRequestCpu', "1")
    def jnlpLimitCpu = parameters.get('jnlpLimitCpu', "3")
    def jnlpRequestMemory = parameters.get('jnlpRequestMemory', "3Gi")
    def jnlpLimitMemory = parameters.get('jnlpLimitMemory', "4Gi")
    def buildingContainer = parameters.get('buildingContainer', "builder")
    def yaml = parameters.get('yaml')
    def envVars = parameters.get('envVars', []).collect()
    def extraContainers = parameters.get('extraContainers', [])

    def label = "node-${UUID.randomUUID().toString()}"

    def podParameters = [
        label: label,
        slaveConnectTimeout: 120,
        serviceAccount: pipelineVars.jenkinsSvcAccount,
        cloud: cloud,
        namespace: namespace,
        annotations: [
            podAnnotation(key: "job-name", value: "${env.JOB_NAME}"),
            podAnnotation(key: "run-display-url", value: "${env.RUN_DISPLAY_URL}"),
        ]
    ]

    // Inject private ssh key included in satlab-tower vms straight from openshift
    // TODO: attempted to inject this in the dockerfile ENV block, but paramiko didn't accept it
    if (image.contains('robottelo-container')) {

        envVars.add(secretEnvVar(
            key:'ROBOTTELO_server__ssh_key_string',
            secretName:'satqe-casc-secret',
            secretKey:'satlab_automation_rsa'))

        envVars.add(secretEnvVar(
            key:'ROBOTTELO_azurerm__client_secret',
            secretName:'satqe-casc-secret',
            secretKey:'azure_client_secret'))

        envVars.add(secretEnvVar(
            key:'ROBOTTELO_azurerm__ssh_pub_key',
            secretName:'satqe-casc-secret',
            secretKey:'azure_pub_ssh_key'))

        envVars.add(secretEnvVar(
            key:'ROBOTTELO_azurerm__password',
            secretName:'satqe-casc-secret',
            secretKey:'azure_admin_passwd'))

        envVars.add(secretEnvVar(
            key:'ROBOTTELO_docker__private_registry_password',
            secretName:'satqe-casc-secret',
            secretKey:'satqe_shared_password'))

        envVars.add(secretEnvVar(
            key:'ROBOTTELO_http_proxy__password',
            secretName:'satqe-casc-secret',
            secretKey:'satqe_shared_password'))

        envVars.add(secretEnvVar(
            key:'ROBOTTELO_ipa__password_ipa',
            secretName:'satqe-casc-secret',
            secretKey:'ipa_password'))
        envVars.add(secretEnvVar(
            key:'ROBOTTELO_ipa__time_based_secret',
            secretName:'satqe-casc-secret',
            secretKey:'totp_ipa_secret'))
        envVars.add(secretEnvVar(
            key:'ROBOTTELO_ldap__password',
            secretName:'satqe-casc-secret',
            secretKey:'ad_password'))
        envVars.add(secretEnvVar(
            key:'ROBOTTELO_open_ldap__password',
            secretName:'satqe-casc-secret',
            secretKey:'open_ldap_password'))
        envVars.add(secretEnvVar(
            key:'ROBOTTELO_rhsso__user_password',
            secretName:'satqe-casc-secret',
            secretKey:'satqe_shared_password'))
        envVars.add(secretEnvVar(
            key:'ROBOTTELO_rhsso__totp_secret',
            secretName:'satqe-casc-secret',
            secretKey:'totp_rhsso_secret'))

        envVars.add(secretEnvVar(
            key:'ROBOTTELO_bugzilla__api_key',
            secretName:'satqe-casc-secret',
            secretKey:'bz-api-key'))

        envVars.add(secretEnvVar(
            key:'ROBOTTELO_report_portal__api_key',
            secretName:'satqe-casc-secret',
            secretKey:'reportportal-robottelo-token',
        ))
    }

    if (image.contains('robottelo-container') || image.contains('broker-container')) {
        // secrets that both robottelo-container and broker-container need
        envVars.add(secretEnvVar(
            key: 'BROKER_host_password',
            secretName: 'satqe-casc-secret',
            secretKey: 'satqe_shared_password'
        ))
    }

    // Cloud Resource Cleanup Script Vars
    if (image.contains('cloud-cleanup-container')) {
        envVars.add(secretEnvVar(
            key:'CLEANUP_GCE__SERVICE_ACCOUNT',
            secretName:'satqe-casc-secret',
            secretKey:'sat6gce_service_account'))
    }

    if (yaml) {
        podParameters['yaml'] = readTrusted(yaml)
    } else {

        podParameters['containers'] = [
            containerTemplate(
                name: 'jnlp',
                image: jenkinsSlaveImage,
                args: '${computer.jnlpmac} ${computer.name}',
                resourceRequestCpu: jnlpRequestCpu,
                resourceLimitCpu: jnlpLimitCpu,
                resourceRequestMemory: jnlpRequestMemory,
                resourceLimitMemory: jnlpLimitMemory,
            ),
            containerTemplate(
                name: 'builder',
                ttyEnabled: true,
                command: 'cat',
                image: image,
                alwaysPullImage: true,
                resourceRequestCpu: requestCpu,
                resourceLimitCpu: limitCpu,
                resourceRequestMemory: requestMemory,
                resourceLimitMemory: limitMemory,
                envVars: envVars,
            ),
        ]
    }

    if (extraContainers) {
        podParameters['containers'].addAll(extraContainers)
    }

    podTemplate(podParameters) {
        node(label) {
            container(buildingContainer) {
                body()
            }
        }
    }
}
