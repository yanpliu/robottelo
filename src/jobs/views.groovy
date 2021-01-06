import javaposse.jobdsl.dsl.views.jobfilter.MatchType
import javaposse.jobdsl.dsl.views.portlets.TestTrendChartContext.DisplayStatus
import jobLib.globalJenkinsDefaults

globalJenkinsDefaults.sat_versions.each { versionName ->
  dashboardView("${versionName}") {
    jobFilters {
      regex {
        matchType(MatchType.INCLUDE_MATCHED)
        regex(".*${versionName}.*")
      }
    }

    leftPortlets {
        testTrendChart {
            displayName("FAILED Test Trend")
            displayStatus(DisplayStatus.FAILED)
        }
        testStatisticsChart {
            displayName("Test Statistics")
        }
    }

    rightPortlets {
        testTrendChart {
            displayName("SKIPPED Test Trend")
            displayStatus(DisplayStatus.SKIPPED)
        }
        testStatisticsGrid {
            displayName("Test Results")
        }
    }

    bottomPortlets {
        jenkinsJobsList {
            displayName("Jobs Matching ${versionName}")
        }
    }

    // tons of column options: https://jenkinsci.github.io/job-dsl-plugin/#path/listView-columns
    columns {
      status()
      name()
      buildDescriptionColumn {
        // These are required for some reason
        forceWidth(false)
        columnWidth(80)
      }
      lastBuildConsole()
      lastSuccess()
      lastFailure()
      lastDuration()
    }
  }
}

def utilJobs = ['Master-Seed',
                'MonitoringTest',
                'Seed Job',
                'template-sla-enforcement',
                'vm-sla-enforcement',
                'polarion-testrun-upload',
                'polarion-testcase-upload',
                'cloud-resources-cleanup'
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
