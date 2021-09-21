// Automation Trigger Job

import jobLib.globalJenkinsDefaults
import jenkins.model.*
import hudson.model.Item
import hudson.model.Items


def jobProperties

globalJenkinsDefaults.sat_versions.each { versionName ->
    Item currentJob = Jenkins.instance.getItemByFullName("${versionName}-automation-trigger")
    if (currentJob) {
        jobProperties = currentJob.@properties
    }
    pipelineJob("${versionName}-automation-trigger") {
        disabled(Jenkins.getInstance().getRootUrl() != globalJenkinsDefaults.production_url)

        description("Automation trigger for ${versionName}")
        parameters {
            stringParam('snap_version', "", "Snap version to be deployed, format is x.y")
            stringParam('sat_version', "${versionName}", "Satellite version to be deployed, format is a.b.c")
            stringParam('tower_url', globalJenkinsDefaults.tower_url, "Ansible Tower URL, format 'https://<url>/'")
        }

        logRotator {
            daysToKeep(42)
        }

        properties {
            disableConcurrentBuilds()
        }

        // Add Robotello trigger or Cron trigger

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
                    scriptPath("src/resources/automationTriggerPipeline.groovy")
                }
        }
        if (jobProperties) {
            configure { root ->
                def properties = root / 'properties'
                jobProperties.each { property ->
                    String xml = Items.XSTREAM2.toXML(property)
                    def jobPropertiesPropertyNode = new XmlParser().parseText(xml)
                    properties << jobPropertiesPropertyNode
                }
            }
        }
    }
}
