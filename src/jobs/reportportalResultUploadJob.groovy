import jobLib.globalJenkinsDefaults
import jenkins.model.*

pipelineJob("reportportal-launch-upload"){
    // disable jobs automatically if not on production
    disabled(Jenkins.getInstance().getRootUrl() != globalJenkinsDefaults.production_url)

    description('Job to parse junit xml and push test results into Report Portal')

    parameters {
        stringParam(
                'snap_version',
                "",
                "Snap version tested, used for description")
        stringParam(
                'sat_version',
                "",
                "Satellite version tested, used for description"
        )
        stringParam(
                'test_run_type',
                "",
                "To differentiate special runs (fips, upgrade)"
        )
        stringParam(
                'results_job_name',
                "",
                "Name of the Job that results will be uploaded from"
        )
        stringParam(
                'results_build_number',
                "",
                "Build number of the Job that results will be uploaded from"
        )
        stringParam(
                'rp_launch_name',
                "",
                "Name to give to the Report portal launch"
        )
        stringParam(
                'rp_launch_description',
                "",
                "Description for the Report Portal launch"
        )
        stringParam(
                'rp_launch_attrs',
                "",
                "Attributes for the Report portal launch: e.g. 'sat_version=6.10-1.0 appliance_count=5'"
        )
        stringParam(
                'rp_rerun_of',
                "",
                "UUID of the reference Report portal launch if this is supposed to be a re-run"
        )
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
            scriptPath("src/resources/reportportalTestResultUploadPipeline.groovy")
        }
    }
}
