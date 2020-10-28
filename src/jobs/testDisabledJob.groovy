import jobLib.globalJenkinsDefaults

import jenkins.model.*

pipelineJob("Check Production First") {
    disabled(shouldDisable=Jenkins.getInstance().getRootUrl().contains('satqe-jenkins-csb-satellite-qe'))

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
            scriptPath("src/resources/testJobDisabled.groovy")
        }
    }
}
