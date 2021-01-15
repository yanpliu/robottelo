import javaposse.jobdsl.dsl.views.jobfilter.MatchType
import jobLib.globalJenkinsDefaults

globalJenkinsDefaults.sat_versions.each { versionName ->
  listView("${versionName}") {

    jobFilters {
      regex {
        matchType(MatchType.INCLUDE_MATCHED)
        regex(".*${versionName}.*")
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
}

def utilJobs = ['Master-Seed',
                'MonitoringTest',
                'Seed Job',
                'sla-enforcement',
]

listView("Utility") {

    jobs {
      utilJobs.each { j ->
        name(j)
      }
    }

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

listView("Templatization") {

     jobFilters {
      regex {
        matchType(MatchType.INCLUDE_MATCHED)
        regex(".*templ.*")
      }
    }

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
