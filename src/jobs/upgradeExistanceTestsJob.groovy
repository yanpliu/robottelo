import jobLib.globalJenkinsDefaults
import jenkins.model.*


def OS = ['rhel7']
def jobCFG = ['y-stream', 'z-stream']


globalJenkinsDefaults.upgrade_versions.each { versionName ->
    OS.each { os ->
        jobCFG.each { jobName ->
            if (! (versionName == globalJenkinsDefaults.upgrade_versions.last() && jobName == 'z-stream')) {
                pipelineJob("sat-${versionName}-${jobName}-upgrade-existence-tests-${os}") {
                    disabled(Jenkins.getInstance().getRootUrl() != globalJenkinsDefaults.production_url)
                    description("Satellite upgrade Existence Tests job for ${jobName}")
                    parameters {
                        stringParam('sat_version', "${versionName}", 'Satellite version to deployed, format is a.b')
                        stringParam('os', "${os}", 'RHEL version of Satellite')
                        stringParam(
                                'build_label',
                                '',
                                'Specify the build label of the Satellite. Example Sat6.10.0-1.0 Which is Sat6.y.z-SNAP.COMPOSE'
                        )
                        stringParam('tower_url', globalJenkinsDefaults.tower_url, "Ansible Tower URL, format 'https://<url>/'")
                        stringParam('stream', "${jobName}", 'y-stream or z-stream')
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
