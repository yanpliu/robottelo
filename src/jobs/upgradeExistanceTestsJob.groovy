import jobLib.globalJenkinsDefaults
import jenkins.model.*


globalJenkinsDefaults.upgrade_versions.each { versionName ->
    globalJenkinsDefaults.sat_os.each { os ->
        globalJenkinsDefaults.streams.each { stream ->
            if (! (versionName == globalJenkinsDefaults.upgrade_versions.last() && stream == 'z_stream')) {
                pipelineJob("sat-${versionName}-${stream}-upgrade-existence-tests-${os}") {
                    disabled(Jenkins.getInstance().getRootUrl() != globalJenkinsDefaults.production_url)
                    description("Satellite upgrade Existence Tests job for ${stream}")
                    parameters {
                        stringParam('sat_version', "${versionName}", 'Satellite version to deployed, format is a.b.c')
                        stringParam('snap_version', '', 'Snap version to deployed, format is x.y')
                        stringParam('os', "${os}", 'RHEL version of Satellite')
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
                                "This option helps to enable the required repository for non-release-fm version upgrade"
                        )
                        booleanParam('foreman_maintain_satellite_upgrade', true, 'This option allows to use foreman-maintain for satellite upgrade.')
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
                            scriptPath("src/resources/upgradeExistancePipeline.groovy")
                        }
                    }
                }
            }
        }
    }
}
