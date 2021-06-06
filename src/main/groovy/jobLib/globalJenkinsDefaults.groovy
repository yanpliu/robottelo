package jobLib

class globalJenkinsDefaults {
    static String gitlab_url = "https://gitlab.sat.engineering.redhat.com/satelliteqe/satelliteqe-jenkins.git"
    static String git_creds = "gitlab-jenkins-user"
    static String master_branch = "*/master"
    static String polarion_url = "https://polarion.engineering.redhat.com/polarion/"
    static String production_url = "https://satqe-jenkins-csb-satellite-qe.apps.ocp4.prod.psi.redhat.com/"
    static String tower_prod = "Infra-Ansible-Tower-01"
    static String tower_url = "https://infra-ansible-tower-01.infra.sat.rdu2.redhat.com"

    static List sat_versions = ["6.9", "6.10"]
    static List upgrade_versions = ["6.9", "6.10"]
    // The template SLA currently ignores the last version passed to it
    // With pre-snaps for the future y-stream, this would remove snaps for the active y-stream
    // When the workflow has been updated and GA templates are available, we can pass sat_versions
    // https://projects.engineering.redhat.com/browse/SATQE-13911
    static List template_sla_versions = ["6.7", "6.8", "6.9", "6.10"]
}
