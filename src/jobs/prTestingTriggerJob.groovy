// Pull Request Testing Trigger Job

import jobLib.globalJenkinsDefaults
import jobLib.prTesterDefaults
import jenkins.model.*

pipelineJob("robottelo-pr-testing") {
    disabled(Jenkins.getInstance().getRootUrl() != globalJenkinsDefaults.production_url)

    description("Robottelo PR Testing Pipeline")
    logRotator {
        artifactDaysToKeep(7)
        daysToKeep(14)
    }
    parameters {
        stringParam('tower_url', globalJenkinsDefaults.tower_url, "Ansible Tower URL, format 'https://<url>/'")
    }

    logRotator {
        daysToKeep(15)
    }

    properties {
        githubProjectUrl(prTesterDefaults.robotteloRepoUrl)
    }

    triggers {
        githubPullRequest {
            admins(prTesterDefaults.admins)
            userWhitelist(prTesterDefaults.userWhitelist)
            cron('H/5 * * * *')
            triggerPhrase('.*test-robottelo.*')
            onlyTriggerPhrase()
            extensions {
                commitStatus {
                    context('Robottelo-Runner')
                    triggeredStatus('PRT Job has been triggered ...')
                    startedStatus('PRT Job still in progress ...')
                    statusUrl('--none--')
                    addTestResults(true)
                    completedStatus('SUCCESS', 'Build ${BUILD_NUMBER} has Passed!  ')
                    completedStatus('FAILURE', 'Build ${BUILD_NUMBER} has Failed!  ')
                    completedStatus('ERROR', 'Build ${BUILD_NUMBER} encountered an error, please re-trigger the job.  ')
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
            scriptPath("src/resources/prTestingPipeline.groovy")
        }
    }


}
