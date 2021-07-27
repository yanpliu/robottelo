class pipelineVars implements Serializable {
    String jenkinsSvcAccount = "default"
    String defaultNameSpace = "jenkins"

    String gitSshCreds = "satqe_gitlab_jenkins_token"

    String jenkinsSlaveImage = 'registry.redhat.io/openshift4/ose-jenkins-agent-base:v4.6'

    String ciBrokerImage = 'image-registry.openshift-image-registry.svc:5000/jenkins-csb-satellite-qe/broker-container'
    String ciRobotteloImage = 'image-registry.openshift-image-registry.svc:5000/jenkins-csb-satellite-qe/robottelo-container'
    String ciCleanScriptImage = 'image-registry.openshift-image-registry.svc:5000/jenkins-csb-satellite-qe/cloud-cleanup-container'
    String ciUpgradesImage = 'image-registry.openshift-image-registry.svc:5000/jenkins-csb-satellite-qe/sat-upgrade-container'
    String ciTestFmImage = 'image-registry.openshift-image-registry.svc:5000/jenkins-csb-satellite-qe/testfm-container'
    String ciUpgradeRobotteloImage = 'image-registry.openshift-image-registry.svc:5000/jenkins-csb-satellite-qe/sat-upgrades-robottelo-container'

    Map robotteloImageTags = [ // maps sat_version to robottelo container tag
        '6.10': 'latest',
        '6.9': '6.9.z',
    ]

    String defaultCloud = 'upshift'
    String upshiftNameSpace = 'jenkins-csb-satellite-qe'
    String defaultSecretName = 'satqe-casc-secret'

    String ibutsuBaseOptions = '--ibutsu https://ibutsu-api.apps.ocp4.prod.psi.redhat.com/ --ibutsu-project satellite-qe '

    String reportPortalServer = 'https://reportportal-sat-qe.apps.ocp4.prod.psi.redhat.com'
    String reportPortalProject = 'Satellite6'

    String polarionProject = 'RHSAT6'
    String polarionUser = 'rhsat6_machine'

    String towerUser = 'satqe_auto_droid'

    List customer_databases = ['DogFood','Securian','PrincipalFinancial','BNYMelon']
}
