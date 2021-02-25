job('Seed Job') {
  label(name='satqe-jenkins-slave')
  wrappers {
    preBuildCleanup()
  }
  scm {
    git {
      branch('master')
      remote {
        name('upstream')
        // replace this with whever you put this repo
        url('https://gitlab.sat.engineering.redhat.com/satelliteqe/satelliteqe-jenkins.git')
      }
    }
  }
  properties {
    pipelineTriggers {
      triggers {
        pollSCM {
          // we will poll 5 times an hour for changes
          scmpoll_spec('H/5 * * * *')
        }
      }
    }
  }
  steps {
    // running tests first prevents half deployments which could break
    gradle 'clean test'
    dsl {
      // any job ending in Job.groovy will be deployed
      external 'src/jobs/**/*Job.groovy'
      additionalClasspath 'src/main/groovy'
    }
    dsl {
      external 'src/jobs/views.groovy'
      additionalClasspath 'src/main/groovy'
    }
  }
  publishers {
    publishHtml {
      report('build/reports/tests/') {
        reportName('Grade Test Results')
      }
    }
  }
}

// This accepts changes in the script approval section of 'Manage Jenkins'.  You may or may not want this.
org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval.get().with { approval ->
  approval.preapproveAll()
  approval.save()
}
