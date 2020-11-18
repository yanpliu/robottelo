import javaposse.jobdsl.dsl.views.jobfilter.MatchType

def versionCFG = ['6.7','6.8','6.9']


versionCFG.each { versionName ->
  listView("${versionName}") {

    jobFilters {
      regex {
        matchType(MatchType.INCLUDE_MATCHED)
        regex("sat-${versionName}-.*")
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
