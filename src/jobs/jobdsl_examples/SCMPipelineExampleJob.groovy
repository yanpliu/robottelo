// Example on how to create a pipeline job which pulls its pipeline from a git repo.
// API docs: https://jenkinsci.github.io/job-dsl-plugin/#path/pipelineJob
//pipelineJob('SCMPipeline') {
//    description('A basic pipeline job that pulls the pipeline from a git repo.')
//
//    // while you can add your parameters in your pipleine script, it is easier to add them here since the pipeline
//    // has to run once before the job is updated.
//    parameters {
//      stringParam('PARAM',  // this variable will be avalable to your pipleine as env.PARAM
//          'Default Value',
//          'Description of what this paramater is')
//    }

    // https://jenkinsci.github.io/job-dsl-plugin/#path/pipelineJob-triggers
    // many triggering types available, using a simple polling on the repo here.
//    triggers {
//      scm('H/5 * * * *')
//    }
//
//    definition {
//      cpsScm {
//        scm {
//          git {
//            remote {
//              name('origin')
//              url('https://pagure.io/some-project.git')
//            }
//            branch('master')
//            extensions {
//              cleanAfterCheckout()
//              disableRemotePoll()
//            }
//          }
//        }
//        // Many keep their pipeline in the root of the project, but it can be anywhere
//        scriptPath('Jenkinsfile')
//      }
//    }
//  }
