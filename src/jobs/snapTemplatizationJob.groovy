// Update Satellite & Capsule Templates

import jobLib.globalJenkinsDefaults
import jenkins.model.*

pipelineJob('snap-templatization') {
    disabled(Jenkins.getInstance().getRootUrl() != globalJenkinsDefaults.production_url)
    blockOn(['rhel-templatization', 'rhel-ga-template-update']) {
        blockLevel('GLOBAL')
        scanQueueFor('ALL')
    }

    def uuid = { UUID.randomUUID().toString() }
    def consumerID = { "Consumer.rh-jenkins-ci-plugin.${uuid()}" }
    String consumerTopic = "${consumerID()}.VirtualTopic.eng.sat6eng-ci.snap.ready"
    description('UMB Listener Job for New Satellite Snaps.')
    parameters {
        stringParam('CI_MESSAGE',"",'UMB message that comes from CI-trigger. It is expected to provide an artifact  .id:COMPOSE_ID, extra.target:CDN, extra.results_id:JIRA')
        stringParam('MESSAGE_HEADERS',"","Headers of the message that comes from CI-trigger")
        stringParam('tower_url', globalJenkinsDefaults.tower_url, "Ansible Tower URL, format 'https://<url>/'")
    }

    properties {
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
            scriptPath("src/resources/snapTemplateCreationPipeline.groovy")
        }
    }
}
