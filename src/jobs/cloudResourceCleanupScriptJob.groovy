// Cloud Resources cleanup job

import jobLib.globalJenkinsDefaults
import jenkins.model.*


pipelineJob("cloud-resources-cleanup") {
    disabled(Jenkins.getInstance().getRootUrl() != globalJenkinsDefaults.production_url)

    description("Scheduled job for cloud resources cleanup")

    logRotator {
        daysToKeep(10)
    }

    properties {
        disableConcurrentBuilds()
    }

    triggers {
        cron('H 0 * * 2')
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
            scriptPath("src/resources/cloudCleanupScriptPipeline.groovy")
        }
    }
}
