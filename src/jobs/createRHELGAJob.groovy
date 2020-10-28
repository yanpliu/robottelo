// Create RHEL Templates

import jobLib.globalJenkinsDefaults
import jenkins.model.*

pipelineJob('rhel-templatization') {
    disabled(Jenkins.getInstance().getRootUrl() != globalJenkinsDefaults.production_url)

    description('RHEL Templatization Pipeline.')
    parameters {
//         stringParam('CI_MESSAGE',"",'UMB message that comes from CI-trigger. It is expected to provide an artifact  .id:COMPOSE_ID, extra.target:CDN, extra.results_id:JIRA')
//        stringParam('MESSAGE_HEADERS',"","Headers of the message that comes from CI-trigger")
        stringParam('qcow_url',"",'URL to the QCOW image that will be templatized')
        stringParam('rhel_version',"","RHEL Version, format is x.y ")
        stringParam('tower_url', globalJenkinsDefaults.tower_url, "Ansible Tower URL, format 'https://<url>/'")
    }

    // PLace holder for RHEL GA UMB
//    properties {
//        pipelineTriggers {
//          triggers {
//            ciBuildTrigger {
//              providers {
//                providerDataEnvelope {
//                  providerData {
//                    activeMQSubscriber {
//                      name("Red Hat UMB")
//                      overrides {
//                        topic(consumerTopic)
//                      }
                      // Message Checks
                      // Keeping this for an example if we need to be more specific
//                       checks {
//                        msgCheck {
//                          field('')
//                          expectedValue('')
//                        }
//                      }
//                    }
//                  }
//                }
//              }
//              noSquash(true)
//            }
//          }
//        }
//    }


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
            scriptPath("src/resources/rhelGATemplateCreationPipeline.groovy")
        }
    }

}
