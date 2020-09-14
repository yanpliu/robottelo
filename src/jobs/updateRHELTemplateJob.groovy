// Update RHEL Templates

import jobLib.globalJenkinsDefaults

pipelineJob('rhel-ga-template-update') {

    description('RHEL Template Update Pipeline.')
    parameters {
        stringParam('rhel_version',"7.5,7.6,7.7,7.8,7.9,8.0,8.1","RHEL version to be update, format is a comma separated list of x.y with no spaces")
        stringParam('tower_url', globalJenkinsDefaults.tower_url, "Ansible Tower URL, format 'https://<url>/'")
    }

   properties {
       pipelineTriggers {
           triggers {
                cron {
                    spec('0 0 * * 5')
                }
           }
       }
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
            scriptPath("src/resources/rhelUpdateTemplatePipeline.groovy")
        }
    }

}