// Manifest Downloader

import jobLib.globalJenkinsDefaults
import jenkins.model.*

pipelineJob("manifest-downloader") {
    disabled(Jenkins.getInstance().getRootUrl() != globalJenkinsDefaults.production_url)

    description('Satellite Manifest downloader job.')

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
            scriptPath("src/resources/manifestDownloaderPipeline.groovy")
        }
    }

}
