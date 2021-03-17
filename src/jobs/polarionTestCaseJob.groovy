// Polarion Test Case Import

import jobLib.globalJenkinsDefaults
import jenkins.model.*

pipelineJob("polarion-testcase-upload") {
    disabled(Jenkins.getInstance().getRootUrl() != globalJenkinsDefaults.production_url)

    description('Polarion Test Case data upload job.')

    parameters {
        stringParam('polarion_url', globalJenkinsDefaults.polarion_url, "Polarion URL to upload Test Case data to")
    }

    properties {
        cachetJobProperty {
            requiredResources(true)
            resources(["polarion"])
        }
        disableConcurrentBuilds()
        pipelineTriggers {
            triggers {
                GenericTrigger {
                    causeString('OCP robottelo-container postCommit hook')
                    regexpFilterExpression('')
                    regexpFilterText('')
                    token('polariontestcaseupload')
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
            scriptPath("src/resources/polarionTestCasePipeline.groovy")
        }
    }

}
