// This is a real life example from the factory 2.0 project.  They are testing multiple projects in multiple repos.
// We will loop through and create all "basic" pipeline jobs.
// In this case, 'basic' means a simple pipeline job that points to a pagure repo
// and execute the Jenkinsfile in that repo.

// descriptions
//def freshmakerDesc = 'Builds freshmaker container images when new commit appears on <a href="https://pagure.io/freshmaker">https://pagure.io/freshmaker</a>.'
//def greenwaveDesc = '<a href="https://pagure.io/greenwave">https://pagure.io/greenwave</a>'
//def waiverdbDesc = '<a href="https://pagure.io/waiverdb">https://pagure.io/waiverdb</a>'


//  format for jobCFG entries:
//  jenkins-jobName : [ description > project description that will be seen on jenkins job page in "safe html" format
//                      project > this is the pagure.io project name
//                      api-token-id > this is the jenkins credential ID for this project's pagure api token
//                    ]

//def jobCFG = ['freshmaker-build'        : ['description' : freshmakerDesc,
//                                           'project'     : 'freshmaker',
//                                           'api-token-id': 'pagure_api_token_freshmaker'],
//              'greenwave'               : ['description' : greenwaveDesc,
//                                           'project'     : 'greenwave',
//                                           'api-token-id': 'pagure_api_token_greenwave'],
//              'greenwave-prs'           : ['description' : greenwaveDesc,
//                                           'project'     : 'greenwave',
//                                           'api-token-id': 'pagure_api_token_greenwave',
//                                           'refspec'     : '+refs/pull/*/head:refs/remotes/origin/pr/*'],
//              'waiverdb'                : ['description' : waiverdbDesc,
//                                           'project'     : 'waiverdb',
//                                           'api-token-id': 'pagure_api_token_waiverdb'],
//              ]

//jobCFG.each { jobName, config ->
//
//  String pagureURL = "https://pagure.io/${config['project']}.git"
//
//  pipelineJob(jobName) {
//    description(config['description'])
//    disabled(true)
//    properties {
//      ownership {
//        primaryOwnerId('pnt-factory2-devel')
//      }
//    }
//
//    throttleConcurrentBuilds{
//      maxTotal(1)
//    }
//
//    parameters {
//      credentialsParam('PAGURE_API_TOKEN') {
//        type('org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl')
//        defaultValue(config['api-token-id'])
//        description('The token used to update pagure PR status')
//      }
//
//
//      def defaultBranch = (config.containsKey('refspec')) ? '' : 'master'
//      stringParam('BRANCH',
//          defaultBranch,
//          'Branch to build, ex: master , pr/200 , myFeature')
//    }
//
//    triggers {
//      scm('H/5 * * * *')
//    }
//
//    definition {
//      cpsScm {
//        lightweight(false)
//        scm {
//          git {
//            remote {
//              name('origin')
//              url(pagureURL)
//              if (config.containsKey('refspec')) {
//                refspec(config['refspec'])
//              }
//            }
//            branch('$BRANCH')
//            extensions {
//              cleanAfterCheckout()
//              disableRemotePoll()
//            }
//          }
//        }
//        scriptPath('Jenkinsfile')
//      }
//    }
//  }
//}

// this is another downstream job which is not part of the pipeline that updtaes results.
//job('Pagure-PR-Status-Updater') {
//  logRotator(10)
//  label('master')
//  parameters {
//    stringParam('PAGURE_SITE', 'https://pagure.io', 'Defaults to the official pagure, can be used to point to others.' )
//    stringParam('PAGURE_REPO', '', 'Name of your pagure project.' )
//    stringParam('PAGURE_PR', '', 'Number of the PR' )
//    stringParam('PERCENT_PASSED', '', 'Percent of the tests that passed, usually 0/100 for simple pass/fail.')
//    stringParam('COMMENT', '', 'Comment added to the status update.')
//    stringParam('REFERENCE_URL', '', 'Link back to the jenkins run.' )
//    stringParam('REFERENCE_JOB_NAME', '', 'Name of the job that ran the tests.')
//    nonStoredPasswordParam('TOKEN', 'API token for the project.')
//  }
//  wrappers {
//    preBuildCleanup()
//  }
//  steps {
//    systemGroovyCommand(readFileFromWorkspace('src/resources/pagure-pr-status-updater.groovy'))
//  }
//}
