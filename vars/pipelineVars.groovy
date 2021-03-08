class pipelineVars implements Serializable {
    String jenkinsSvcAccount = "jenkins"
    String defaultNameSpace = "jenkins"

    String gitSshCreds = "satqe_gitlab_jenkins_token"

    String jenkinsSlaveImage = 'registry.access.redhat.com/openshift3/jenkins-slave-base-rhel7:v3.11'

    String centralCIjenkinsSlaveImage = 'docker-registry.upshift.redhat.com/insights-qe/jenkins-slave-base:latest'

    String python36Image = 'registry.access.redhat.com/ubi8/python-36:latest'
    String ciBrokerImage = 'docker-registry.default.svc:5000/jenkins-csb-satellite-qe/broker-container'
    String ciRobotteloImage = 'docker-registry.default.svc:5000/jenkins-csb-satellite-qe/robottelo-container'
    String ciCleanScriptImage = 'docker-registry.default.svc:5000/jenkins-csb-satellite-qe/cloud-cleanup-container'

    String defaultCloud = 'upshift'
    String upshiftNameSpace = 'jenkins-csb-satellite-qe'

    String ibutsuBaseOptions = '--ibutsu https://ibutsu-api.apps.ocp4.prod.psi.redhat.com/ --ibutsu-project satellite-qe '

    String reportPortalServer = 'https://reportportal-sat-qe.apps.ocp4.prod.psi.redhat.com'
    String reportPortalProject = 'Satellite6'

    String polarionProject= 'RHSAT6'

}
