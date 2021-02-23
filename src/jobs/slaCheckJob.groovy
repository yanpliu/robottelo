import jobLib.globalJenkinsDefaults
import jenkins.model.*

pipelineJob("vm-sla-enforcement") {
    disabled(Jenkins.getInstance().getRootUrl() != globalJenkinsDefaults.production_url)

    parameters {
        stringParam{
            name("tower_url")
            defaultValue(globalJenkinsDefaults.tower_url)
            description("Ansible Tower URL, format 'https://<url>/'")
            trim(true)
        }
        stringParam {
            name("sla_shutdown_period")
            defaultValue("3")
            description("This field works in conjunction with sla_shutdown_period_unit. By default, VMs will be shut down (sla_shutdown_period * sla_shutdown_period_unit) from their creation date.")
            trim(true)
        }
        stringParam {
            name("sla_shutdown_period_unit")
            defaultValue("86400")
            description("Value in seconds, where 86400 sec = 1 day. Works in conjunction with sla_shutdown_period.")
            trim(true)
        }
        stringParam {
            name("expiration_warning_period")
            defaultValue("1")
            description("How long before the expiration should warning be given. This yields VMs about to expire list." +
                        " It is a multiple of expiration_warning_period_unit. (expiration_warning_period_unit * expiration_warning_period_unit) " +
                        "will decide warning period for vm expiration.")
            trim(true)
        }
        stringParam {
           name("expiration_warning_period_unit")
           defaultValue("86400")
           description("Value in seconds, where 86400 sec = 1 day. Works in conjunction with expiration_warning_period.")
           trim(true)
        }
        stringParam {
           name("default_expire")
           defaultValue("259200")
           description("Default expire time for VMs that don't have expire_date fact set. Value defaults to 3 * 86400 sec(1 day)= 259200 sec(3 days).")
           trim(true)
        }
        stringParam {
           name("search_pattern")
           defaultValue("name=*-*")
           description("Use search pattern to find out all the VMs for enforcing SLA. Search string follows RHV search format.")
           trim(true)
        }
        stringParam {
           name("bad_vms_search_pattern")
           defaultValue("name!=*-*")
           description("Use search pattern to find out all the VMs that don't match standard naming convention. Search string follows RHV search format.")
           trim(true)
        }
        text {
           name("ignore_vm")
           defaultValue("'[\"HostedEngine\"]'")
           description("List of VMs to ignore from SLA check. List should use following format which Ansible would recognize:\n"+
           "'[\"HostedEngine\",\"VM1\",\"VM2\"]'")
        }
    }

    triggers {
        cron('H */12 * * *')
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
            scriptPath("src/resources/slaCheck.groovy")
        }
    }
}
