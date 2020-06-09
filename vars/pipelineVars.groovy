class pipelineVars implements Serializable {
    String jenkinsSvcAccount = "jenkins"
    String defaultNameSpace = "jenkins"

    String gitSshCreds = "satqe_gitlab_jenkins_token"


    String jenkinsSlaveImage = (
        'registry.access.redhat.com/openshift3/jenkins-slave-base-rhel7:v3.11'
    )

    String centralCIjenkinsSlaveImage = (
        'docker-registry.upshift.redhat.com/insights-qe/jenkins-slave-base:latest'
    )

    String python36Image = 'registry.access.redhat.com/ubi8/python-36:latest'
    String brokerImage = 'satelliteqe/broker'

    String defaultCloud = 'upshift'
    String upshiftNameSpace = 'jenkins-csb-satellite-qe'

}