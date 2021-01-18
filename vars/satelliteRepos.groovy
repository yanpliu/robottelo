class satelliteRepos implements Serializable {
    String tools_repo = "http://dogfood.sat.engineering.redhat.com/pulp/repos/Sat6-CI/QA/Tools_{sat_version}_with_{rhel_version}_Server/custom/Satellite_Tools_{sat_version}_Composes/Satellite_Tools_{sat_version}_{rhel_version}_x86_64/"
    String capsule_repo = "http://dogfood.sat.engineering.redhat.com/pulp/repos/Sat6-CI/QA/Capsule_{sat_version}_with_{rhel_version}_Server/custom/Satellite_Capsule_{sat_version}_Composes/Satellite_Capsule_{sat_version}_{rhel_version}/"
}
