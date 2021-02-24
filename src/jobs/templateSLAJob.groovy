import jobLib.globalJenkinsDefaults
import jenkins.model.*

pipelineJob("template-sla-enforcement") {
    disabled(Jenkins.getInstance().getRootUrl() != globalJenkinsDefaults.production_url)

    parameters {
        stringParam{
            name("tower_url")
            defaultValue(globalJenkinsDefaults.tower_url)
            description("Ansible Tower URL, format 'https://<url>/'")
            trim(true)
        }
        stringParam {
           name("sat_versions")
           defaultValue(globalJenkinsDefaults.sat_versions.join(","))
           description("Comma-separated list of current satellite versions (X.Y).")
           trim(false)
        }
    }

    properties {
        pipelineTriggers {
            triggers {
                cron {
                    // execute this every Friday
                    spec('H H * * 5')
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
            scriptPath("src/resources/templateSLA.groovy")
        }
    }
}
