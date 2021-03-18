import jobLib.globalJenkinsDefaults
import jenkins.model.*

def os = ['rhel7']

def jobCFG = [
        'z_stream':   ['num_appliances': '1',
                       'z_stream': true,
                      ],
        'y_stream':   ['num_appliances': '1',
                        'z_stream': false,
                      ],
]

globalJenkinsDefaults.sat_versions.each { versionName ->
    os.each { OS ->
        jobCFG.each { jobName, config ->
            if (! (versionName == globalJenkinsDefaults.sat_versions.last() && config['z_stream'])) {
                pipelineJob("sat-${versionName}-${jobName}-Upgrade-Phase-${OS}") {
                    disabled(Jenkins.getInstance().getRootUrl() != globalJenkinsDefaults.production_url)
                    description("Satellite, Capsule Upgrade job for ${jobName}")
                    parameters {
                        stringParam(
                            'sat_version',
                            "${versionName}",
                            "Satellite version to deployed, format is a.b"
                        )
                        stringParam(
                            'os',
                            "${OS}",
                            "RHEL version of Satellite"
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
                        booleanParam(
                            'zstream_upgrade',
                             config['z_stream'],
                            "This option, enable for Z-stream and disable for Y-stream upgrade"
                        )
                        booleanParam(
                            "foreman_maintain_satellite_upgrade",
                            true,
                            "This option allows to use foreman-maintain for satellite-upgrade."
                        )
                        booleanParam(
                            "downstream_fm_upgrade",
                            false,
                            "This option helps to enable the required reposiory for non-release-fm version upgrade"
                        )
                        booleanParam(
                            "foreman_maintain_capsule_upgrade",
                            true,
                            "This option allows to use foreman-maintain for capsule upgrade."
                        )
                        booleanParam(
                            "satellite_capsule_setup_reboot",
                            true,
                            "This option allows the reboot of satellite and capsule setup after upgrade."
                        )
                        booleanParam(
                            "upgrade_with_http_proxy",
                            false,
                            "This option allows to perform the upgrade using http_proxy."
                        )
                        booleanParam(
                            "setup_preserve",
                            false,
                            "This option allows us to preserve the setup after upgrade for investigation purpose"
                        )
                        stringParam(
                            'capsule_count',
                            '1',
                            'number of associated capsule for a satellite, by default it is 1 but it could be any number'
                        )
                        choiceParam(
                            "distribution", [
                            'downstream',
                            'cdn'
                            ],
                             """This option allows to configure the satellite and capsule repository based on their distribution type:
                            \nSelect 'downstream' to perform the Y and Z-stream upgrade.
                            \nSelect 'cdn' to perform the Z-stream upgrade only, Y-stream cdn upgrade support is not yet implemented"""
                        )
                        stringParam(
                            'external_satellite_hostname',
                            '',
                            "Provide the exteranl satellite details"
                        )
                        stringParam(
                            'external_capsule_hostnames',
                            '',
                            "Provide the external capsule hostname, if you have multiple capsules then enter it by keeping space"
                        )
                        choiceParam(
                            'ansible_repo_version', [
                            '2.9',
                            '2.8',
                            '2.7'
                            ],
                          "Ansible repo version for capsule upgrade"
                        )
                        stringParam(
                            'specific_upgrade_base_version',
                            '',
                            'Provide the specific upgrade base version to perform the upgrade, this is optional parameter, if you do not provide then the upgrade will execute with the latest version'
                        )
                        choiceParam(
                            'upgrade_type', [
                                    'longrun',
                                    'satellite',
                                    'capsule',
                                    'client',
                                    'n-1'
                                    ],
                         """Satellite & Capsule upgrade type:
                            \nSelect 'satellite' to perform only Satellite upgrade
                            \nSelect 'capsule'to perform both Capsule as well as its associated Satellite upgrade
                            \nSelect 'client' to perform Clients as well as its associated Satellite upgrade
                            \nSelect 'longrun' to perform Satellite, Capsule and Clients upgrade
                            \nSelect 'n-1' to perform only satellite upgrade, by keeping capsule at last released zStream version."""
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
                            scriptPath("src/resources/upgradePhasePipeline.groovy")
                        }
                    }
                }
             }
        }
    }
}
