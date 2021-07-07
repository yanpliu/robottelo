// Update Satellite & Capsule Templates

import jobLib.globalJenkinsDefaults
import jenkins.model.*

globalJenkinsDefaults.upgrade_versions.each { versionName ->
    pipelineJob("Satellite-${versionName}-Upgrade-Trigger") {
        disabled(Jenkins.getInstance().getRootUrl() != globalJenkinsDefaults.production_url)
        logRotator {
            daysToKeep(42)
        }
        description("Upgrade Automation trigger for ${versionName}")
        parameters {
            stringParam('CI_MESSAGE', '', 'UMB message that User should provide with satellite, snap and RHEL major version.')
            stringParam('sat_version', "${versionName}", "Satellite version to be deployed, format is a.b.c")
            stringParam('tower_url', globalJenkinsDefaults.tower_url, "Ansible Tower URL, format 'https://<url>/'")
        }

        definition {
            cpsScm {
                lightweight(false)
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
                scriptPath("src/resources/upgradeTriggerPipeline.groovy")
            }
        }
    }
}
