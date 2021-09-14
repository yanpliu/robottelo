// Update Satellite & Capsule Templates

import jobLib.globalJenkinsDefaults
import jenkins.model.*

pipelineJob('sat-upgrade-trigger') {
    disabled(Jenkins.getInstance().getRootUrl() != globalJenkinsDefaults.production_url)
    logRotator {
        daysToKeep(42)
    }

    def uuid = { UUID.randomUUID().toString() }
    def consumerID = { "Consumer.rh-jenkins-ci-plugin.${uuid()}" }
    String consumerTopic = "${consumerID()}.VirtualTopic.eng.sat6eng-ci.snap.ready"

    description("UMB trigger for Satellite Upgrade Automation")
    parameters {
        stringParam('CI_MESSAGE', '', 'UMB message that User should provide with satellite, snap and RHEL major version.')
        stringParam('tower_url', globalJenkinsDefaults.tower_url, "Ansible Tower URL, format 'https://<url>/'")
    }

    properties {
        disableConcurrentBuilds()
        pipelineTriggers {
          triggers {
            ciBuildTrigger {
              providers {
                providerDataEnvelope {
                  providerData {
                    activeMQSubscriber {
                      name("Red Hat UMB")
                      overrides {
                        topic(consumerTopic)
                      }
                      checks {
                       msgCheck {
                         field('$.satellite_version')
                         expectedValue('\\d+\\.\\d+\\.\\d+')
                       }
                     }
                    }
                  }
                }
              }
              noSquash(true)
            }
          }
        }
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
