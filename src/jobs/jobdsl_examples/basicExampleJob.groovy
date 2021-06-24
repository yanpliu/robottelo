// This example creates a freestyle job

// docs: https://jenkinsci.github.io/job-dsl-plugin/#path/job
//job('freestyle-job') { // job names must be unique or they will be overwritten
//  // add a top level dsl
//  logRotator(10)
//
//  // It is good to restrict your jobs to run on your jenkins nodes/slaves.  Running everything on master slows down jenkins
//  label('master')
//
//  // many jobs are parameterized.
//  parameters {
//    stringParam('FOO', 'default value', 'Some description of what FOO is.' )
//    nonStoredPasswordParam('TOKEN', 'API token for the project.')
//  }
//
//  // many of the checkboxes in the Jenkins UI are wrappers in the DSL
//  wrappers {
//    preBuildCleanup()
//    timestamps()
//  }
//
//  steps {
//    // this allows me to edit bash.sh with my preferred editor and not as an inline string here.
//    def myScript = readFileFromWorkspace('src/resources/bash.sh')
//    shell(myScript)
//  }
//}
