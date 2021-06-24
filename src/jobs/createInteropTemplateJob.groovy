// Triggers Product Interoperability testing on all GA versions of Satellite

import jobLib.globalJenkinsDefaults
import jenkins.model.*

pipelineJob('interop-os-templatization') {
    disabled(Jenkins.getInstance().getRootUrl() != globalJenkinsDefaults.production_url)

    def uuid = { UUID.randomUUID().toString() }
    def consumerID = { "Consumer.rh-jenkins-ci-plugin.${uuid()}" }
    String consumerTopic = "${consumerID()}.VirtualTopic.qe.ci.product-scenario.build.complete."
    description('UMB Listerner Job for New Interop requests.')
    parameters {
        stringParam('CI_MESSAGE',"",'UMB message that comes from CI-trigger. It is expected to provide an artifact  .products.*.name:satellite, .products.*.version, .products.*.name:rhel, .products.*.nvr, .products.*.repos.*.base_url')
        stringParam('MESSAGE_HEADERS',"","Headers of the message that comes from CI-trigger")
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
                      // Message Checks
                      checks {
                        msgCheck {
                          field('$..products.*.name')
                          expectedValue('satellite')
                        }
                        msgCheck {
                          field('$..products.*.state')
                          expectedValue('interop ready')
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
            scriptPath("src/resources/createInteropTemplatePipeline.groovy")
        }
    }
}
