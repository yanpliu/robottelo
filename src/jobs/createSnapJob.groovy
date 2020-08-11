// Update Satellite & Capsule Templates

import jobLib.globalJenkinsDefaults

pipelineJob('snap-templatization') {
    def uuid = { UUID.randomUUID().toString() }
    def consumerID = { "Consumer.rh-jenkins-ci-plugin.${uuid()}" }
    String consumerTopic = "${consumerID()}.VirtualTopic.eng.sat6eng-ci.snap.ready"
    description('UMB Listerner Job for New Satelittle Snaps.')
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
                      // Message Checks
                      // Keeping this for an example if we need to be more specific
//                       checks {
//                        msgCheck {
//                          field('$..satellite_version')
//                          expectedValue('6.8.0')
//                        }
//                      }
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
            scriptPath("src/resources/snapTemplateCreationPipeline.groovy")
        }
    }
}