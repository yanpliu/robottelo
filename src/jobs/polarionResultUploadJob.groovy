import jobLib.globalJenkinsDefaults
import jenkins.model.*

pipelineJob("polarion-testrun-upload"){
    // disable jobs automatically if not on production
    disabled(Jenkins.getInstance().getRootUrl() != globalJenkinsDefaults.production_url)

    description('Job to upload junit xml test results into Polarion')

    properties {
        cachetJobProperty {
            requiredResources(true)
            resources(["polarion"])
        }
    }

    parameters {
        stringParam(
                'snap_version',
                "",
                "Snap version to deployed, format is x.y")
        stringParam(
                'sat_version',
                "",
                "Satellite version to deployed, format is a.b.c"
        )
        // TODO https://projects.engineering.redhat.com/browse/SATQE-12327
        stringParam(
                'rhel_version',
                "7",
                "Version of RHEL Satellite version is deployed on, format is a"
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
                'test_run_type',
                "",
                "To differentiate special runs (fips, upgrade)"
        )
        stringParam(
                'polarion_url',
                globalJenkinsDefaults.polarion_url,
                "URL of Polarion instance you want to upload results to"
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
            scriptPath("src/resources/polarionTestResultUploadPipeline.groovy")
        }
    }
}
