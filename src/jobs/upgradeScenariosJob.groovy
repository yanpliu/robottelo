import jobLib.globalJenkinsDefaults
import jenkins.model.*


globalJenkinsDefaults.sat_rhel_matrix.each { sat_version, rhels ->
    rhels.each { os ->
        globalJenkinsDefaults.streams.each { stream ->
            if ((! (sat_version == globalJenkinsDefaults.sat_rhel_matrix.keySet().last() && stream == 'z_stream')) && (os != 'rhel8')) {
                pipelineJob("sat-${sat_version}-${os}-${stream}-upgrade-scenarios") {
                    disabled(Jenkins.getInstance().getRootUrl() != globalJenkinsDefaults.production_url)
                    description("Satellite upgrade scenarios for ${stream}")
                    parameters {
                        stringParam('sat_version', "${sat_version}", 'Satellite version to deployed, format is a.b.c')
                        stringParam('snap_version', '', 'Snap version to be deployed, format is x.y')
                        stringParam('os', "${os}", 'RHEL version of Satellite')
                        stringParam('xdist_workers', '2', 'Number of Workers/Satellites to run the tests')
                        stringParam(
                            'build_label',
                            '',
                            "Specify the build label. Ex. '6.8 TO 6.9 Snap: 1.0', which is FROM_VERSION TO SAT_VERSION SNAP: x.y"
                        )
                        stringParam('tower_url', globalJenkinsDefaults.tower_url, "Ansible Tower URL, format 'https://<url>/'")
                        stringParam('stream', "${stream}", 'y_stream or z_stream')
                        booleanParam(
                            'downstream_fm_upgrade',
                            false,
                            'This option helps to enable the required repository for non-release-fm version upgrade'
                        )
                        booleanParam(
                            'foreman_maintain_satellite_upgrade',
                            true,
                            'This option allows to use foreman-maintain for satellite upgrade.'
                        )
                        booleanParam(
                            'foreman_maintain_capsule_upgrade',
                            true,
                            'This option allows to use foreman-maintain for capsule upgrade.'
                        )
                        choiceParam(
                            'distribution',
                            ['downstream', 'cdn'],
                            'This option allows to configure the satellite and capsule repository based on their distribution type'
                        )
                        stringParam(
                            'specific_upgrade_base_version',
                            '',
                            'Provide the specific upgrade base version to perform the upgrade, this is optional parameter, if you do not provide then the upgrade will execute with the latest version'
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
                            scriptPath("src/resources/upgradeScenariosPipeline.groovy")
                        }
                    }
                }
            }
        }
    }
}
