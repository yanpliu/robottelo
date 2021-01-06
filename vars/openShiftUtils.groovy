// Helpers for spinning up jenkins slaves running on OpenShift and other OpenShift utils


def withNode(Map parameters = [:], Closure body) {
    /*
    Spins up a pod with 2 containers: jnlp, and specified 'image'
    */
    def image = parameters.get('image', pipelineVars.python36Image)
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
    def envVars = parameters.get('envVars', [])
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
    }
    //

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