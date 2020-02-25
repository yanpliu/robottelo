import javaposse.jobdsl.dsl.views.jobfilter.MatchType

def factoryJobs = ['estuary-api',
                   'freshmaker-build',
                   'freshmaker-dev-check',
                   'freshmaker-prs',
                   'greenwave',
                   'greenwave-prs',
                   'waiverdb',
                   'waiverdb-prs',
                   'Factory Master Seed',
                   'Factory Seed Job',
                   'resultsdb-updater'
]

// views == tabs in the jenkins UI, the arg is the string used for the tab label
// docs: https://jenkinsci.github.io/job-dsl-plugin/#path/listView
listView('Factory 2.0') {
  filterExecutors()

  // here we will run through the list above and individually add jobs to this view
  jobs {
    factoryJobs.each { j ->
      name(j)
    }
  }

  // you can also add jobs to the view via regex
  jobFilters {
    regex {
      matchType(MatchType.INCLUDE_MATCHED)
      regex('factory2-.*')
    }
  }

  // tons of column options: https://jenkinsci.github.io/job-dsl-plugin/#path/listView-columns
  columns {
    status()
    weather()
    name()
    lastSuccess()
    lastFailure()
    lastDuration()
    progressBar()
  }
}
