// All-Tier and EndToEnd Tests for Upgrade

import jobLib.globalJenkinsDefaults
import jenkins.model.*

def OS = ['rhel7']

def jobCFG = [
        'z_stream':   ['z_stream': true],
        'y_stream':   ['z_stream': false],
]


globalJenkinsDefaults.upgrade_versions.each { versionName ->
    OS.each { os ->
        jobCFG.each { jobName, config ->
            if (! (versionName == globalJenkinsDefaults.upgrade_versions.last() && config['z_stream'])) {
                pipelineJob("sat-${versionName}-${jobName}-upgrade-all-tier-${os}") {
                    disabled(Jenkins.getInstance().getRootUrl() != globalJenkinsDefaults.production_url)
                    description("Satellite upgrade All-Tier/EndToEnd job for ${jobName}")
                    parameters {
                        stringParam('sat_version', "${versionName}", 'Satellite version to deployed, format is a.b')
                        stringParam('snap_version', '', 'Snap version to deployed, format is x.y')
                        stringParam('os', "${os}", 'RHEL version of Satellite')
                        stringParam('xdist_workers', '4', 'Number of Workers/Satellites to run the tests')
                        stringParam(
                            'build_label',
                            '',
                            "Specify the build label. Ex. '6.8 TO 6.9 Snap: 1.0', which is FROM_VERSION TO SAT_VERSION SNAP: x.y"
                        )
                        booleanParam('zstream_upgrade', config['z_stream'], 'Check if z-stream upgrade')
                        booleanParam(
                            'downstream_fm_upgrade',
                            false,
                            'Use to enable the required repository for non-release-fm version upgrade'
                        )
                        booleanParam(
                            'foreman_maintain_satellite_upgrade',
                            true,
                            'Use foreman-maintain for satellite upgrade'
                        )
                        booleanParam(
                            'foreman_maintain_capsule_upgrade',
                            true,
                            'Use foreman-maintain for capsule upgrade'
                        )
                        choiceParam(
                            'distribution',
                            ['downstream', 'cdn'],
                            """Option to allow configure satellite and capsule repos based on their distribution type,
                            \nSelect 'downstream' to perform the Y and Z-stream upgrade.
                            \nSelect 'cdn' to perform the Z-stream upgrade only,
                            \n Y-stream cdn upgrade support is not yet implemented
                            """
                        )
                        choiceParam(
                            'upgrade_type',
                            ['capsule', 'longrun', 'satellite', 'client', 'n-1'],
                            """Satellite & Capsule upgrade type:
                            \nSelect 'satellite' to perform only Satellite upgrade
                            \nSelect 'capsule'to perform both Capsule as well as its associated Satellite upgrade
                            \nSelect 'client' to perform Clients as well as its associated Satellite upgrade
                            \nSelect 'longrun' to perform Satellite, Capsule and Clients upgrade
                            \nSelect 'n-1' to perform only Satellite upgrade, keep capsule with last z-stream version
                            """
                        )
                        booleanParam('use_reportportal', true, 'Determines whether or not to push results to RP')
                        stringParam('tower_url', globalJenkinsDefaults.tower_url, "Tower URL, format 'https://<url>/'")
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
                            scriptPath("src/resources/upgradeAllTierPipeline.groovy")
                        }
                    }
                }
            }
        }
    }
}
