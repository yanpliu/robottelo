import javaposse.jobdsl.dsl.views.jobfilter.MatchType
import javaposse.jobdsl.dsl.views.portlets.TestTrendChartContext.DisplayStatus
import jobLib.globalJenkinsDefaults


globalJenkinsDefaults.sat_rhel_matrix.each { sat_version, rhels ->
    rhels.each { os ->
        dashboardView("${sat_version} ${os.capitalize()}") {
            jobFilters {
            regex {
                matchType(MatchType.INCLUDE_MATCHED)
                regex(".*${sat_version}.*${os}.*(critical|high|medium|low|trigger|maintain|fips).*")
                }
            }

            leftPortlets {
                testTrendChart {
                    displayName("FAILED Test Trend")
                    displayStatus(DisplayStatus.FAILED)
                    dateRange(14)
                    graphHeight(300)
                    graphWidth(450)
                }
                testStatisticsChart {
                    displayName("Test Result Proportions")
                }
            }

            rightPortlets {
                testTrendChart {
                    displayName("SKIPPED Test Trend")
                    displayStatus(DisplayStatus.SKIPPED)
                    dateRange(14)
                    graphHeight(300)
                    graphWidth(450)
                }

                testStatisticsGrid {
                    displayName("Test Result Statistics")
                    useBackgroundColors(true)
                }
            }

            bottomPortlets {
                jenkinsJobsList {
                    displayName("Jobs Matching ${sat_version}")
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

  // Upgrade Dashboard
  dashboardView("${sat_version} Upgrade") {
    jobFilters {
      regex {
        matchType(MatchType.INCLUDE_MATCHED)
        regex(".*${sat_version}.*(Upgrade|upgrade).*")
      }
    }

    leftPortlets {
        testTrendChart {
            displayName("FAILED Test Trend")
            displayStatus(DisplayStatus.FAILED)
            dateRange(14)
            graphHeight(300)
            graphWidth(450)
        }
        testStatisticsChart {
            displayName("Test Result Proportions")
        }
    }

    rightPortlets {
        testTrendChart {
            displayName("SKIPPED Test Trend")
            displayStatus(DisplayStatus.SKIPPED)
            dateRange(14)
            graphHeight(300)
            graphWidth(450)
        }
        testStatisticsGrid {
            displayName("Test Result Statistics")
            useBackgroundColors(true)
        }
    }

    bottomPortlets {
        jenkinsJobsList {
            displayName("Jobs Matching ${sat_version}")
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
                'cloud-resources-cleanup',
                'robottelo-pr-testing',
                'manifest-downloader',
                'reportportal-launch-upload'
]

listView("Utility") {

    jobs {
      utilJobs.each { j ->
        name(j)
      }
    }

    columns {
      status()
      name()
      lastBuildConsole()
      lastSuccess()
      lastDuration()
      cronTrigger()
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
      name()
      lastSuccess()
      lastFailure()
      lastDuration()
      progressBar()
    }
}
