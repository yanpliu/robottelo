// Defines a new VM template from a given QCOW image url

import jobLib.globalJenkinsDefaults
import jenkins.model.*

pipelineJob('PIT-create-template') {
    disabled(Jenkins.getInstance().getRootUrl() != globalJenkinsDefaults.production_url)
    
    def uuid = { UUID.randomUUID().toString() }
    parameters {
        stringParam('qcow_url', "", "URL of the target QCOW image")
    	stringParam('rhel_nvr', "", "Name-Version-Release string of the given OS - will be used as a suffix of the new template. e.g. RHEL-8.4.0-20210503.1")
	    stringParam('rhel_os_repo', "", "Optional, URL of the OS repository")
        stringParam('tower_url', globalJenkinsDefaults.tower_url, "Ansible Tower URL, format 'https://<url>/'")
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
            scriptPath("src/resources/createPITTemplatePipeline.groovy")
        }
    }
}
