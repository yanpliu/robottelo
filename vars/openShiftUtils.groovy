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
    def limitCpu = parameters.get('resourceLimitCpu', "8")
    def requestMemory = parameters.get('resourceRequestMemory', "2Gi")
    def limitMemory = parameters.get('resourceLimitMemory', "10Gi")
    def jnlpRequestCpu = parameters.get('jnlpRequestCpu', "1")
    def jnlpLimitCpu = parameters.get('jnlpLimitCpu', "3")
    def jnlpRequestMemory = parameters.get('jnlpRequestMemory', "1Gi")
    def jnlpLimitMemory = parameters.get('jnlpLimitMemory', "4Gi")
    def buildingContainer = parameters.get('buildingContainer', "builder")
    def yaml = parameters.get('yaml')
    def envVars = parameters.get('envVars', []).collect()
    def secretName = parameters.get('secretName', pipelineVars.defaultSecretName)
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
    if (image.contains('robottelo-container') || image.contains('sat-upgrades-robottelo-container')) {

        envVars.add(secretEnvVar(
            key:'ROBOTTELO_server__ssh_password',
            secretName: secretName,
            secretKey:'satqe_shared_password'))

        envVars.add(secretEnvVar(
            key:'ROBOTTELO_azurerm__client_secret',
            secretName: secretName,
            secretKey:'azure_client_secret'))

        envVars.add(secretEnvVar(
            key:'ROBOTTELO_azurerm__ssh_pub_key',
            secretName: secretName,
            secretKey:'azure_pub_ssh_key'))

        envVars.add(secretEnvVar(
            key:'ROBOTTELO_azurerm__password',
            secretName: secretName,
            secretKey:'azure_admin_passwd'))

        envVars.add(secretEnvVar(
            key:'ROBOTTELO_docker__private_registry_password',
            secretName: secretName,
            secretKey:'satqe_shared_password'))

        envVars.add(secretEnvVar(
            key:'ROBOTTELO_http_proxy__password',
            secretName: secretName,
            secretKey:'satqe_shared_password'))

        envVars.add(secretEnvVar(
            key:'ROBOTTELO_rh_cloud__token',
            secretName: secretName,
            secretKey:'rh-cloud-token'))

        envVars.add(secretEnvVar(
            key:'ROBOTTELO_ipa__password',
            secretName: secretName,
            secretKey:'ipa_password'))
        envVars.add(secretEnvVar(
            key:'ROBOTTELO_ipa__time_based_secret',
            secretName: secretName,
            secretKey:'totp_ipa_secret'))

        envVars.add(secretEnvVar(
            key:'ROBOTTELO_ldap__password',
            secretName: secretName,
            secretKey:'ad_password'))

        envVars.add(secretEnvVar(
            key:'ROBOTTELO_open_ldap__password',
            secretName: secretName,
            secretKey:'satqe_shared_password'))

        envVars.add(secretEnvVar(
            key:'ROBOTTELO_rhsso__rhsso_password',
            secretName: secretName,
            secretKey:'satqe_shared_password'))

        envVars.add(secretEnvVar(
            key:'ROBOTTELO_rhsso__totp_secret',
            secretName: secretName,
            secretKey:'totp_rhsso_secret'))

        envVars.add(secretEnvVar(
            key:'ROBOTTELO_bugzilla__api_key',
            secretName: secretName,
            secretKey:'bz-api-key'))

        envVars.add(secretEnvVar(
            key:'ROBOTTELO_report_portal__api_key',
            secretName: secretName,
            secretKey:'reportportal-robottelo-token'))

        envVars.add(secretEnvVar(
            key:'ROBOTTELO_osp__password',
            secretName: secretName,
            secretKey:'osp_password'))

        envVars.add(secretEnvVar(
            key:'ROBOTTELO_osp__project_domain_id',
            secretName: secretName,
            secretKey:'osp_project_domain_id'))

        envVars.add(secretEnvVar(
            key:'ROBOTTELO_ec2__access_key',
            secretName: secretName,
            secretKey:'ec2_access_key'))

        envVars.add(secretEnvVar(
            key:'ROBOTTELO_ec2__secret_key',
            secretName: secretName,
            secretKey:'ec2_secret_key'))

        envVars.add(secretEnvVar(
            key:'ROBOTTELO_rhev__password',
            secretName: secretName,
            secretKey:'satqe_shared_password'))

        envVars.add(secretEnvVar(
            key:'ROBOTTELO_rhev__image_password',
            secretName: secretName,
            secretKey:'satqe_shared_password'))

        envVars.add(secretEnvVar(
            key:'ROBOTTELO_vmware__username',
            secretName: secretName,
            secretKey:'vmware_username'))

        envVars.add(secretEnvVar(
            key:'ROBOTTELO_vmware__password',
            secretName: secretName,
            secretKey:'vmware_password'))

        envVars.add(secretEnvVar(
            key:'ROBOTTELO_vmware__image_password',
            secretName: secretName,
            secretKey:'satqe_shared_password'))

        // polarion script use through robottelo-container
        envVars.add(secretEnvVar(
            key: 'POLARION_PASSWORD',
            secretName: secretName,
            secretKey: 'polarion-password'))

        // needs for subscribing the satellite with cdn
        envVars.add(secretEnvVar(
            key: 'ROBOTTELO_subscription__rhn_password',
            secretName: secretName,
            secretKey: 'rhn_password'))

        envVars.add(secretEnvVar(
            key:'ROBOTTELO_gce__project_id',
            secretName: secretName,
            secretKey:'gce_project_id'))

        envVars.add(secretEnvVar(
            key:'ROBOTTELO_gce__client_email',
            secretName: secretName,
            secretKey:'gce_client_email'))

        envVars.add(secretEnvVar(
            key:'ROBOTTELO_gce__cert',
            secretName: secretName,
            secretKey:'gce_cert'))

        envVars.add(secretEnvVar(
            key: 'ROBOTTELO_container_repo__registries__redhat__password',
            secretName: secretName,
            secretKey: 'container_repo_rh_registry_token'))

        envVars.add(secretEnvVar(
            key: 'ROBOTTELO_container_repo__registries__quay__password',
            secretName: secretName,
            secretKey: 'container_repo_quay_robot_token'))

        envVars.add(secretEnvVar(
            key: 'ROBOTTELO_virtwho__esx__hypervisor_password',
            secretName: secretName,
            secretKey: 'esx_hypervisor_password'))

        envVars.add(secretEnvVar(
            key: 'ROBOTTELO_virtwho__xen__hypervisor_password',
            secretName: secretName,
            secretKey: 'xen_hypervisor_password'))

        envVars.add(secretEnvVar(
            key: 'ROBOTTELO_virtwho__hyperv__hypervisor_password',
            secretName: secretName,
            secretKey: 'hyperv_hypervisor_password'))

        envVars.add(secretEnvVar(
            key: 'ROBOTTELO_virtwho__rhevm__hypervisor_password',
            secretName: secretName,
            secretKey: 'rhevm_hypervisor_password'))

        envVars.add(secretEnvVar(
            key: 'ROBOTTELO_virtwho__libvirt__hypervisor_password',
            secretName: secretName,
            secretKey: 'libvirt_hypervisor_password'))

        envVars.add(secretEnvVar(
            key: 'ROBOTTELO_virtwho__kubevirt__hypervisor_password',
            secretName: secretName,
            secretKey: 'kubevirt_hypervisor_password'))
    }

    if (image.contains('robottelo-container') || image.contains('broker-container') || image.contains('sat-upgrade-container') || image.contains('testfm-container') || image.contains('sat-upgrades-robottelo-container')) {
        // secrets that robottelo-container, broker-container and upgrade container need
        envVars.add(secretEnvVar(
            key: 'BROKER_host_password',
            secretName: secretName,
            secretKey: 'satqe_shared_password'))
        envVars.add(secretEnvVar(
            key: 'BROKER_AnsibleTower__password',
            secretName: secretName,
            secretKey: 'tower-password'))
        envVars.add(secretEnvVar(
            key:'SATLAB_PRIVATE_KEY',
            secretName: secretName,
            secretKey:'satlab_automation_rsa'))
    }

    if (image.contains('sat-upgrade-container') || image.contains('sat-upgrades-robottelo-container')) {
        envVars.add(secretEnvVar(
            key:'UPGRADE_subscription__rhn_password',
            secretName: secretName,
            secretKey:'rhn_password'))
        envVars.add(secretEnvVar(
            key:'UPGRADE_upgrade__oauth_consumer_key',
            secretName: secretName,
            secretKey:'oauth_consumer_key'))
        envVars.add(secretEnvVar(
            key:'UPGRADE_upgrade__oauth_consumer_secret',
            secretName: secretName,
            secretKey:'oauth_consumer_secret'))
        envVars.add(secretEnvVar(
            key:'UPGRADE_upgrade__remote_ssh_password',
            secretName: secretName,
            secretKey:'satqe_shared_password'))
    }

    // Cloud Resource Cleanup Script Vars
    if (image.contains('cloud-cleanup-container')) {
        envVars.add(secretEnvVar(
            key: 'CLEANUP_GCE__SERVICE_ACCOUNT',
            secretName: secretName,
            secretKey: 'sat6gce_service_account'))
        envVars.add(secretEnvVar(
            key: 'CLEANUP_AZURE__PASSWORD',
            secretName: secretName,
            secretKey: 'azure_client_secret'))
        envVars.add(secretEnvVar(
            key: 'CLEANUP_EC2__PASSWORD',
            secretName: secretName,
            secretKey: 'ec2_secret_key'))
    }

    // Injecting ssh-key and password vars to container
    if (image.contains('testfm-container')) {
       envVars.add(secretEnvVar(
            key:'TESTFM_SUBSCRIPTION__RHN_PASSWORD',
            secretName: secretName,
            secretKey:'rhn_password'))
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
