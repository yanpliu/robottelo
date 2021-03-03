package jobLib

class globalJenkinsDefaults {
    static String gitlab_url = "https://gitlab.sat.engineering.redhat.com/satelliteqe/satelliteqe-jenkins.git"
    static String git_creds = "gitlab-jenkins-user"
    static String master_branch = "*/master"
    static String polarion_url = "https://polarion.stage.engineering.redhat.com/polarion/"
    static String production_url = "https://satqe-jenkins-csb-satellite-qe.cloud.paas.psi.redhat.com/"
    static String tower_prod = "Infra-Ansible-Tower-01"
    static String tower_url = "https://infra-ansible-tower-01.infra.sat.rdu2.redhat.com"
    static String tower_user = "ansible-tower-jenkins-user"

    static List sat_versions = ["6.7", "6.8", "6.9", "6.10"]
}
