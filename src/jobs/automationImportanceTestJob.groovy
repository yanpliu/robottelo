// Automation Tests for critical, high, medium, low

import jobLib.globalJenkinsDefaults
import jenkins.model.*

def defaultConfig = [
    'rp_launch': 'OCP-Jenkins-CI',
    'workflow': 'deploy-sat-jenkins',
    ]

def jobCFG = [
        'critical':   ['xdist_workers': '5',
                       'importance': 'Critical',
                       'pytest_options': "-m 'not destructive' --importance Critical tests/foreman/",
                       ] << defaultConfig,
        'high':       ['xdist_workers': '10',
                       'importance'  : 'High',
                       'pytest_options': "-m 'not destructive' --importance High tests/foreman/",
                       ] << defaultConfig,
        'medium':     ['xdist_workers': '5',
                       'importance'  : 'Medium',
                       'pytest_options': "-m 'not destructive' --importance Medium tests/foreman/",
                       ] << defaultConfig,
        'low':        ['xdist_workers': '5',
                       'importance': 'Low',
                       'pytest_options': "-m 'not destructive' --importance Low tests/foreman/",
                       ] << defaultConfig,
        'fips':        ['xdist_workers': '5',
                       'importance': 'Fips',
                       'rp_launch': 'OCP-Jenkins-CI-FIPS',
                       'workflow': 'install-sat-jenkins-fips',
                       'pytest_options': "-m 'not destructive and upgrade' tests/foreman/",
                       ],
]


globalJenkinsDefaults.sat_rhel_matrix.each { sat_version, rhels ->
    rhels.each { os ->
        jobCFG.each { jobName, config ->
            pipelineJob("sat-${sat_version}-${os}-${jobName}") {
                disabled(Jenkins.getInstance().getRootUrl() != globalJenkinsDefaults.production_url)

                description("Automation job for case importance of ${jobName}")

                parameters {
                    stringParam('snap_version', "", "Snap version to deployed, format is x.y")
                    // Will default when https://projects.engineering.redhat.com/browse/SATQE-12568 is finished
                    stringParam(
                        'sat_version',
                        "${sat_version}",
                        "Satellite version to deployed, format is a.b.c"
                    )
                    stringParam(
                        'os',
                        "${os}",
                        "RHEL version of satellite"
                    )
                    stringParam(
                        'template_name',
                        "",
                        "Specific template name to deploy, for emergency use"
                    )
                    stringParam(
                        'xdist_workers',
                        "${config['xdist_workers']}",
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
                        'workflow',
                        "${config['workflow']}",
                        "SatLab workflow to be used"
                    )
                    stringParam(
                        "rp_launch",
                        "${config['rp_launch']}",
                        "Report Portal launch name"
                    )
                    stringParam(
                        'pytest_options',
                        "${config['pytest_options']}",
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
                    artifactDaysToKeep(10)
                    daysToKeep(21)
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
}
