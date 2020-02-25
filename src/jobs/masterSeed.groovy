job('Master-Seed') {
  description('Helper that simply populates the seed job since the seed cant rewrite itself.')
  wrappers {
    preBuildCleanup()
  }
  scm {
    git {
      branch('master')
      remote {
        name('upstream')
        // replace this url with wherever you stick this repo
        url('https://gitlab.sat.engineering.redhat.com/jmolet/jenkins-dsl-template.git')
      }
    }
  }

  steps {
    gradle 'clean test'
    dsl {
      external 'src/jobs/seed.groovy'
    }
  }
}
