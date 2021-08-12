import jobLib.globalJenkinsDefaults
import jenkins.model.*

customer_databases = ['DogFood','Softlayer','SaintGobain']

globalJenkinsDefaults.sat_versions.each { versionName ->
    if (versionName == globalJenkinsDefaults.sat_versions.last()) {
        customer_databases.each { customer_name ->
            pipelineJob("sat-db-${versionName}-upgrade-for-${customer_name}") {
                disabled(Jenkins.getInstance().getRootUrl() != globalJenkinsDefaults.production_url)
                description("Satellite DB Upgrade Migrate For ${customer_name}")
                parameters {
                    stringParam(
                        'sat_version',
                        "${versionName}",
                        "Satellite version to deployed, format is a.b.c"
                    )
                    choiceParam(
                        'os',
                        ['rhel7','rhel6'],
                        "Select OS version of target Satellite"
                    )
                    stringParam(
                        'snap_version',
                        '',
                        'Snap version to be deployed, format is x.y'
                    )
                    stringParam(
                        'build_label',
                        "",
                        "Specify the build label of the Satellite. Example Sat6.3.0-1.0 Which is Sat6.y.z-SNAP.COMPOSE"
                    )
                    stringParam(
                        'tower_url',
                        globalJenkinsDefaults.tower_url,
                        "Ansible Tower URL, format 'https://<url>/'"
                    )
                    stringParam(
                        'stream',
                        "y_stream",
                        'y_stream or z_stream'
                    )
                    stringParam(
                        'customer_name',
                        "${customer_name}",
                        "Specify the customer name for which you run the satellite restore and upgrade"
                    )
                    booleanParam(
                        'restorecon',
                        false,
                        "Enable the restorecon, this would help to restore the selinux contentx accross filesystem, for now it is required for wallmart db"
                    )
                    booleanParam(
                        'include_pulp_data',
                        false,
                        "Enable the include_pulp_data if the backup data contains the pulp content"
                    )
                    booleanParam(
                        'clone_rpm',
                        false,
                        "This option allow us to restore the satellite using satellite clone rpm"
                    )
                    choiceParam(
                        'ansible_repo_version',
                        ['2.9', '2.8', '2.7'],
                        "Ansible repo version for capsule upgrade"
                    )
                    booleanParam(
                        'mongodb_upgrade',
                        false,
                        'This option is used to test the mongodb upgrade after satllite upgrade'
                    )
                    booleanParam(
                        'satellite_backup',
                        true,
                        'This option is used to test the satellite backup after satellite upgrade'
                    )
                    booleanParam(
                        "foreman_maintain_satellite_upgrade",
                        true,
                        "This option is used to run the satellite upgrade using foreman-maintain"
                    )
                    booleanParam(
                        "downstream_fm_upgrade",
                        false,
                        "This option enable the required reposiory for non-release-fm version upgrade"
                    )
                    booleanParam(
                        "satellite_capsule_setup_reboot",
                        true,
                        "This option allows the reboot of satellite and capsule setup after upgrade."
                    )
                    booleanParam(
                        "setup_preserve",
                        false,
                        "This option allows us to preserve the setup after upgrade for investigation purpose"
                    )
                    choiceParam(
                        "distribution",
                        ['downstream', 'cdn'],
                        """This option allows to configure the satellite and capsule repository based on their distribution type:
                        \nSelect 'downstream' to perform the Y and Z-stream upgrade.
                        \nSelect 'cdn' to perform the Z-stream upgrade only, Y-stream cdn upgrade support is not yet implemented"""
                    )
                    choiceParam(
                        'upgrade_type',
                        ['satellite'],
                        "upgrade type: \nSelect 'satellite' to perform only Satellite upgrade"
                    )
                    booleanParam(
                        'extend_vm',
                        true,
                        "This option extend the vm sla for 6 days from the current time"
                    )
                 }

                logRotator {
                    daysToKeep(42)
                }

                properties {
                    disableConcurrentBuilds()
                }

                definition {
                    cpsScm {
                        lightweight(true)
                            scm {
                                git {
                                    remote {
                                        name()
                                        url(globalJenkinsDefaults.gitlab_url)
                                        credentials(globalJenkinsDefaults.git_creds)
                                    }
                                branch(globalJenkinsDefaults.master_branch)
                            }
                        }
                        scriptPath("src/resources/satelliteDbUpgradePipeline.groovy")
                    }
                }
            }
        }
    }
}
