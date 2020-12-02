// Automation Tests for Foreman-Maintain

import jobLib.globalJenkinsDefaults
import jenkins.model.*

globalJenkinsDefaults.sat_versions.each { versionName ->
    pipelineJob("sat-${versionName}-maintain-tests") {
        disabled(Jenkins.getInstance().getRootUrl() != globalJenkinsDefaults.production_url)
        description('Automation job for foreman-maintain testing')
        parameters {
            choiceParam(
                'component',
                ['Satellite', 'Capsule'],
                'Select product where you want to test foreman-maintain.'
            )
            stringParam('sat_version', "${versionName}", 'Satellite version to deployed, format is a.b.c')
            stringParam('snap_version', '', 'Snap version to deployed (default latest), format is x.y')
            booleanParam('test_upstream', false, 'If checked, will use upstream foreman-maintain')
            booleanParam('test_open_pr', false, 'If checked, will use foreman-maintain upstream open PRs for testing')
            stringParam('pr_number', '', 'Mandatory if test_open_pr is checked')
            stringParam('branch_name', '', 'Mandatory if test_open_pr is checked')
            stringParam(
                'pytest_options',
                'tests/',
                'Specify pytest options here, E.g tests/test_health.py -k test_positive_check_hammer_ping -m test_marker'
            )
            stringParam('tower_url', globalJenkinsDefaults.tower_url, "Ansible Tower URL, format 'https://<url>/'")
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
                scriptPath('src/resources/foremanMaintainTestsPipeline.groovy')
            }
        }
    }
}
