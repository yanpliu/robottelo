import jobLib.globalJenkinsDefaults
import jenkins.model.*

def upgrade_pipeline = ["upgrade-testing", "upgrade-phase"]


globalJenkinsDefaults.sat_rhel_matrix.each { sat_version, rhels ->
    rhels.each { os ->
        globalJenkinsDefaults.streams.each { stream ->
            upgrade_pipeline.each { pipelineType ->
                if ((! (sat_version == globalJenkinsDefaults.sat_rhel_matrix.keySet().last() && stream == 'z_stream')) && (os != 'rhel8')) {
                    pipelineJob("sat-${sat_version}-${os}-${stream}-${pipelineType}") {
                        disabled(Jenkins.getInstance().getRootUrl() != globalJenkinsDefaults.production_url)
                        description("Satellite, Capsule Upgrade job for ${stream}")
                        parameters {
                            stringParam('sat_version', "${sat_version}", 'Satellite version to deployed, format is a.b.c')
                            stringParam('snap_version', '', 'Snap version to be deployed, format is x.y')
                            stringParam(
                                'os',
                                "${os}",
                                "RHEL version of Satellite"
                            )
                            stringParam(
                                'build_label',
                                "",
                                "Specify the build label. Ex. '6.8 TO 6.9 Snap: 1.0', which is FROM_VERSION TO SAT_VERSION SNAP: x.y"
                            )
                            stringParam(
                                'tower_url',
                                globalJenkinsDefaults.tower_url,
                                "Ansible Tower URL, format 'https://<url>/'"
                            )
                            stringParam('stream', "${stream}", 'y_stream or z_stream')
                            booleanParam(
                                'db_trigger',
                                 false,
                                 'This option use to trigger the customer db upgrade job after satellite upgrade'
                            )
                            booleanParam(
                                "foreman_maintain_satellite_upgrade",
                                true,
                                'This option allows to use foreman-maintain for satellite-upgrade.'
                            )
                            booleanParam(
                                "downstream_fm_upgrade",
                                false,
                                'This option helps to enable the required reposiory for non-release-fm version upgrade'
                            )
                            booleanParam(
                                "foreman_maintain_capsule_upgrade",
                                true,
                                'This option allows to use foreman-maintain for capsule upgrade.'
                            )
                            booleanParam(
                                "satellite_capsule_setup_reboot",
                                true,
                                'This option allows the reboot of satellite and capsule setup after upgrade.'
                            )
                            booleanParam(
                                "upgrade_with_http_proxy",
                                false,
                                'This option allows to perform the upgrade using http_proxy.'
                            )
                            booleanParam(
                                "setup_preserve",
                                false,
                                'This option allows us to preserve the setup after upgrade for investigation purpose'
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
                                'Provide the exteranl satellite details'
                            )
                            stringParam(
                                'external_capsule_hostnames',
                                '',
                                'Provide the external capsule hostname, if you have multiple capsules then enter it by keeping space'
                            )
                            choiceParam(
                                'ansible_repo_version', [
                                '2.9',
                                '2.8',
                                '2.7'
                                ],
                              'Ansible repo version for capsule upgrade'
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
                            stringParam(
                                'pipelineType',
                                "${pipelineType}",
                                "upgrade-testing: trigger by user for testing purpose or upgrade-phase: trigger by the upstream job"
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
}
