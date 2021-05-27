// Automation Tests for critical, high, medium, low

import jobLib.globalJenkinsDefaults
import jenkins.model.*

def jobCFG = [
        'critical':   ['num_appliances': '5',
                       'importance': 'Critical',],
        'high':       ['num_appliances': '10',
                       'importance'  : 'High',],
        'medium':     ['num_appliances': '5',
                       'importance'  : 'Medium',],
        'low':        ['num_appliances': '5',
                       'importance': 'Low',],
]

globalJenkinsDefaults.sat_versions.each { versionName ->
    jobCFG.each { jobName, config ->
        pipelineJob("sat-${versionName}-${jobName}-tests") {
            disabled(Jenkins.getInstance().getRootUrl() != globalJenkinsDefaults.production_url)

            description("Automation job for case importance of ${jobName}")
            logRotator {
                artifactDaysToKeep(10)
                daysToKeep(21)
            }
            parameters {
                stringParam('snap_version', "", "Snap version to deployed, format is x.y")
                // Will default when https://projects.engineering.redhat.com/browse/SATQE-12568 is finished
                stringParam(
                    'sat_version',
                    "${versionName}",
                    "Satellite version to deployed, format is a.b.c"
                )
                stringParam(
                    'appliance_count',
                    "${config['num_appliances']}",
                    "Number of Satellite instances to checkout for each pytest session"
                )
                stringParam(
                    'tower_url',
                    globalJenkinsDefaults.tower_url,
                    "Ansible Tower URL, format 'https://<url>/'"
                )
                stringParam(
                    'importance',
                    "${config['importance']}",
                    "Importance mark for pytest session. Must be capitalized"
                )
                stringParam(
                    'pytest_options',
                    "-m 'not destructive' tests/foreman/",
                    'Pytest options, other than those specified with unique string params.'
                )
                booleanParam(
                    'use_ibutsu',
                    true,
                    'Determines whether or not to push results to ibutsu'
                )
                booleanParam(
                    'use_reportportal',
                    true,
                    'Determines whether or not to push results to report portal'
                )
                stringParam(
                    'rerun_of',
                    '',
                    'Marks the build as a re-run of the given RP Launch UUID (overrides the default re-run logic)'
                )
            }

            logRotator {
                daysToKeep(42)
            }

            properties {
                disableConcurrentBuilds()
            }

            definition {
                cpsScm {
                    lightweight(true)
                    scm {
                        git {
                            remote {
                                name()
                                url(globalJenkinsDefaults.gitlab_url)
                                credentials(globalJenkinsDefaults.git_creds)
                            }
                            branch(globalJenkinsDefaults.master_branch)
                        }
                    }
                    scriptPath("src/resources/automationImportanceTestsPipeline.groovy")
                }
            }

        }
    }
}

