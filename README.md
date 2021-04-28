# SatelliteQE Jenkins Project

This repo is based on a template for Jenkins Job and Pipeline DSL.

# GitLab Runners

This project uses GitLab CI to execute linting and preliminary testing on each new Merge Request opened against the repo. In order to allow GitLab CI
to execute against your Fork of this project, you need to have maintainer privilege for the repository. Ask current maintainers/owners to grant you this access.

With maintainer privilege, navigate to `Settings->CI/CD->Runners` **on your fork** and enable `docker-runner-satqe-jenkins`.

In case you can't find this runner for any reason, you should contact the maintainers of the repo for more information/help.

## File structure

    .
    ├── src
    │   ├── jobs            # JDSL files
    │   ├── main
    │   │   ├── groovy      # support classes
    │   │   │   └── jobLib  # constants/vars for use in JDSL
    │   │   └── resources   # IDE support for IDEA / IntelliJ
    │   ├── resources       # pipelines called by JDSL
    │   └── test
    │       └── groovy      # spec tests
    ├── vars                # groovy functions for pipelines
    ├── Vagrantfile         # vagrant for a dsl development jenkins
    ├── ansible             # sources for vagrant
    ├── container           # container sources
    └── build.gradle        # build file

# Script Examples

This repo makes use of the [Job DSL Plugin](https://github.com/jenkinsci/job-dsl-plugin/wiki) and is based off of the [Job DSL example project](https://github.com/sheehan/job-dsl-gradle-example). Check out [this presentation](https://www.youtube.com/watch?v=SSK_JaBacE0) for a walk through of this example (starts around 14:00).  Also refer to the [generic DSL API](https://jenkinsci.github.io/job-dsl-plugin/) for documentation on how to create a Job script.  Append `plugin/job-dsl/api-viewer/index.html` to your jenkins url to see the most accurate list of DSL available to you with your set of plugins as some won't be in the generic api (like the UMB functions).

Examples will be in the `src/jobs/` folder with comments inline.  Each example will try to focus on one topic for easy consumption, but you are encouraged to combine concepts to reduce code and have more reusability.


## Testing

`./gradlew test` runs the spec tests.

[JobScriptsSpec](src/test/groovy/com/dslexample/JobScriptsSpec.groovy)
will loop through all DSL files and make sure they don't throw any exceptions when processed.  The seed job will also run the tests before deploying automatically so a broken DSL isn't deployed, which can result in a half broken state between jobs.

## Debug XML

Jenkins itself stores all of its configurations in XML internally. This DSL effectively directs Jenkins to generate this XML.  Sometimes for debugging purposes (or converting from manual jobs to DSL) it is useful to compare or look at the XML directly.

To see the existing xml on any job through the jenkins UI, go to the job and add `/config.xml` to the URL.  For example: `https://<your-jenkins>.rhev-ci-vms.eng.rdu2.redhat.com/job/<your job>/config.xml`

When you run `./gradlew test` the XML output files will be copied to `build/debug-xml/`. This can be useful if you want to inspect the generated XML before check-in.

## Seed Job

Due to the chicken and egg problem of a seed job can't really create itself, there are two seed jobs.  The primary [Seed Job](src/jobs/seed.groovy) triggers every time there is a change to the master branch of this job repo, and generates all the other jenkins jobs.

To propagate changes to the seed itself, run the [Master Seed](src/jobs/masterSeed.groovy) since the seed job can't rewrite itself.

You can also create the example seed job via the Rest API Runner (see below) using the pattern `src/jobs/seed.groovy`. If you wish to upload it to another Jenkins server, or change the seed job itself without the manually created Master Seed.

## REST API Runner

A gradle task is configured that can be used to create/update jobs via the Jenkins REST API, if desired. Normally
a seed job is used to keep jobs in sync with the DSL, but this runner might be useful if you'd rather process the
DSL outside of the Jenkins environment or if you want to create the seed job from a DSL script.

```./gradlew rest -Dpattern=<pattern> -DbaseUrl=<baseUrl> [-Dusername=<username>] [-Dpassword=<password>]```

* `pattern` - ant-style path pattern of files to include
* `baseUrl` - base URL of Jenkins server
* `username` - Jenkins username, if secured
* `password` - Jenkins password or token, if secured

# Running this project

Originally this project was designed for running on your own hosted version of Jenkins (such as the Jenkins CSB) and populating the jobs on there via the seed jobs.  Included here is a vagrant/ansible setup (in the `ansible` folder) that that will start a local Jenkins server that will give you a full access environment to test your jobs and poke around however you like.  Also included is a container environment setup in the `container` folder if you prefer working that way or deploy your jenkins on openshift.  For the purposes of the examples given, both enviornments are set up the same using  [Jenkins Configuration as Code](https://github.com/jenkinsci/configuration-as-code-plugin/blob/master/README.md) to set up the system.

## Vagrant

Before creating a vagrant instance with jenkins on it, it's a good idea to install the vagrant-hostmanager plugin first.  You can do this like so:

```
sudo vagrant plugin install vagrant-hostmanager
```

This plugin will allow the new instance to be accessible via it's hostname which by default is `jenkins.example.com`

`vagrant up` with vagrant installed will spawn a VM that runs an instance of Jenkins, with necessary plugins and a modified seed job that will generate the jobs based on the vagrant shared folder (the top-level directory of this repo). Using this, you can make changes, run `vagrant rsync`, run the seed job, and see the results.

## Vagrant Jenkins

Once you have started your vagrant instance, you can go to it with your browser.  If you installed the hostmanager plugin, you can browse to it from your local machine:

```
http://jenkins.example.com:8080
```

The default user and password are admin/admin

The `Development Seed Job` on this server is equivalent to the `Master Seed` job in the project.

Check out the `jenkins_plugins` var in `ansible/vagrant.yml` for a list of installed plugins.  Included are a big list of plugins for many use cases, you should edit this list to your use case.

## Docker

Included is a `container/Dockerfile` for building a container to run in openstack or the CP environment.  You will have to edit `script_approval.groovy` and `container/jenkins.yaml` to  your liking (tons of examples [here](https://github.com/jenkinsci/configuration-as-code-plugin/tree/master/demos)).  With this Dockerfile it creates a directory `$JENKINS_HOME/dsl-dev`.  If you run your container and mount this project into it you will get the eqivalent to the dev enviorment that we have in vagrant.  An example of how to run this locally is:

```
podman build -t jenkins-example -f container/Dockerfile .
podman run --rm --name jenkins-example -p 8080:8080 -v "$HOME/Projects/jenkins-dsl-template":/var/jenkins_home/dsl-dev  jenkins-example
```
* replace `$HOME/Projects/jenkins-dsl-template` with wherever you have this project

## Jenkins Plugins

When you run the jobs you may get many warnings or possibly errors referring to missing plugins.  You can add these plugins as dependencies in the `build.gradle` using the `testPlugins` function, and they can be installed on the vagrant/development Jenkins instance in the `ansible/vagrant.yml` file under the `jenkins_plugins` var.

Its also worth noting here that this project tries to keep up with the latest Jenkins CSB release.  Jenkins and DSL plugin versions are set project side in `gradle.properties`.

# Creating your own job

Here are some tips and guidelines on how to create your own Job with the Groovy DSL.  Groovy is not terribly hard, and most of the code is hopefully relatively easy to understand.

It all starts from the [seed job](src/jobs/seed.groovy), which will process all files in the `src/jobs/` folder that end in `Jobs.groovy`.  It also runs [views.groovy](src/jobs/views.groovy) which manages the tabs on the jenkins UI.

## The job()

The core of a Job is the job() function.  Just like in the GUI, each job must have a unique name, so the job function takes one argument, which is the name if your job:


```
job("my-job-name") {
    // ...
}
```

The `job()` interface is useful for generic jobs, however you may also opt for `pipelineJob` or `matrixJob` depending on the job type you wish to create.

Past this I really recommend using the [Jenkins Job DSL API Reference](https://jenkinsci.github.io/job-dsl-plugin/) which covers every command available.

## Pipelines

Most of the jobs are actually pipeline jobs which are instead created with `pipelineJob()`.  Most of the work in these jobs are delegated to their `Jenkinsfile` in their respective repos.  Therefore the work done jenkins-side (this project) is quite minimal, and all the jobs can be easily generated in a for loop - iterating over the job configuration (`jobCFG`) map.

## The resources folder

Many jobs will delegate work to a script (pipeline/python/bash/groovy/etc...) during the build step.  Instead of putting the contents of this script as a single string inline, we can put it in the resources folder.  This gives us the ability to edit the script on its own using its own syntax highlighting and IDE integrations.  We can then refer to this script in the job:

```
steps {
    // runs a groovy script
    systemGroovyCommand(readFileFromWorkspace('src/resources/pagure-pr-status-updater.groovy'))

    // runs a bash script
    shell(readFileFromWorkspace('src/resources/some-bash.sh'))
  }
```

The `readFileFromWorkspace()` function returns the entire script as a string so from here you could run that through your favorite templating language from that point.

## IDE integration

While you can use any editor, this project includes DSL definition files for [IntelliJ IDEA](https://www.jetbrains.com/idea/), in the folder `src/main/resources/`.  This gives this editor tab completion and argument hits/docs to the various Job DSL and Jenkins Pipleline functions.  You can also run the `./gradlew` commands with the gradle plugin from the IDE.


# Using Git-Crypt

* Your gpg key must be added to the repo by a currently supported user. Reach out to project admins to have your key added
* Admins have to have pulled your key from the keyservers, and edited it to set it as ultimately trusted. Or include a `--trusted` argument when adding the key.
* Admins will add your key with `git crypt add-gpg-user <fingerprint>` and open an MR to update `master` branch.
* In this repository use `git crypt unlock` which will allow you to read and write to `secrets/`.
* In `secrets/` directory, each file would contain just single credential.

See git-crypt documentation for more information on using git-crypt.  There is no need to `init` within this project.

https://github.com/AGWA/git-crypt#git-crypt---transparent-file-encryption-in-git

```
# cat secrets/tower-password
mypassword
```
* You may create new passwords or secrets as needed. Just 1 secret per file. Continue to next section to see how to use it in Jenkins.
* You can use encrypt/decrypt commands to lock/unlock secrets in your working directory when you clone the repo first time and the files are encrypted:
```
Encrypt: git crypt lock
Decrypt: git crypt unlock
```

## To use the credential in your Jenkins, you need to use following steps

* Clone following repo: https://gitlab.cee.redhat.com/ccit/jenkins-csb/ 
* Next two are only needed for `push-credentials.sh` nothing else. You do not need to commit these changes to repo.
* Please see [CCIT Tools README](https://gitlab.cee.redhat.com/ccit/jenkins-csb/-/tree/2-190-3/cci-jd) for more details on the tooling.
- Make sure properties.yaml correctly declare `OS_PROJECT_NAME` and `OS_TENANT_NAME`, as that is where the credentials will be uploaded.
- Make sure to sym-link `src/jobs` to `jobs` at the root of your `satelliteqe-jenkins` dir using ` ln -s src/jobs jobs`

* Enter into 'ccit/jenkins-csb' repo and run the following command:
```

oc login api.ocp4.prod.psi.redhat.com:6443
< use your ldap login creds >

oc project jenkins-csb-satellite-qe

./cci-jd/push-credentials.sh ../satelliteqe-jenkins
```
* Above step will create you new set of secrets in openshift.
* Now that is done, you can update `casc.yaml` to actually make use of those credentials, an example as follows:
```
credentials:
  system:
    domainCredentials:
      - credentials:
          - usernamePassword:
              scope: GLOBAL
              id: ansible-tower-jenkins-user
              username: "admin"
              password: ${casc-secret/tower-password} #Load from Environment Variable
              description: "Username/Password Credentials for Ansible Tower"
unclassified:
  ansibleTowerGlobalConfig:
    towerInstallation:
      - enableDebugging: true
        towerCredentialsId: ansible-tower-jenkins-user  # create these by hand in jenkins
        towerDisplayName: Infra-Ansible-Tower-01
        towerTrustCert: true
        towerURL: https://infra-ansible-tower-01.infra.sat.rdu2.redhat.com
```

More examples can be found at: https://github.com/jenkinsci/configuration-as-code-plugin/tree/90223edaf191f28c6ec8d46f84ee7feb14172e9b/demos/credentials

* Once this is all done, you should commit the changes to your `casc.yml` and `secrets/*` to your Git repo.
* Make sure that your `CASC_DECLARATION_PROPERTIES` is pointing to correct branch of correct repo/fork for the build `OS_TENANT_NAME-jenkins-docker` and rebuild it either via UI or by issuing `oc start-build OS_TENANT_NAME-jenkins-docker`

This will rebuild your Jenkins docker container and that will trigger rebuild of `OS_TENANT_NAME-jenkins` and finally redeploy Jenkins Pods.

This is what finishes adding new credentials to your Jenkins with Git-Crypt and JCasC.

Additional Reference: https://gitlab.cee.redhat.com/ccit/jenkins-csb/-/blob/2-190-3/cci-jd/docs/git-crypt.md


# OCP Jenkins CSB Modifications

Our Jenkins core build configs and docker images (production and stage) are provided by CCIT through the Jenkins CSB program.

The following modifications have been made to the Jenkins CSB build configs, in order to support our project.


## ConfigMaps

ConfigMaps hold specific key/value pairs that are used by the builder pods or the Jenkins master/slave pods.

There are specific ConfigMaps defined in the `openshift` directory, which need to be loaded into OCP on new deployments or migrations.

These ConfigMap keys should then be attached to the `satqe-jenkins` or `satqe-stage-jenkins` BuildConfig defined by CCIT.  The environment variable name in the BuildConfig should match the ConfigMap data key, like `CASC_KUBE_SERVICE`



## BuildConfigs

The ConfigMap for the relevant Jenkins master pod is added to the BuildConfig for the container image.

For the above example of `satqe-jenkins-casc-vars`, the build config is modified to define `CASC_KUBE_SERVICE` environment variable, with a value pulled from the config map.

This makes the value available to `casc.yaml` during the image build, and Jenkins master configuration is therefore dynamic with its Kubernetes cloud plugin service endpoints.

The below example is a truncated build config yaml for `satqe-jenkins`, and includes the relevant environment variable modifications.  `GIT_SSL_NO_VERIFY`, `TENANT_NAME`, and `JENKINS_SERVICE_NAME` are all set by Jenkins CSB, only the configMapKeyRef is a satelliteqe-jenkins customization.


```
apiVersion: build.openshift.io/v1
kind: BuildConfig
spec:
  strategy:
    sourceStrategy:
      env:
        - name: GIT_SSL_NO_VERIFY
          value: 'true'
        - name: TENANT_NAME
          value: satqe
        - name: JENKINS_SERVICE_NAME
          value: satqe-jenkins
        - name: CASC_KUBE_SERVICE
          valueFrom:
            configMapKeyRef:
              key: CASC_KUBE_SERVICE
              name: stage-jenkins-casc-vars

```

## DeploymentConfigs

The seed job needs more than the default 500Mi of memory afforded by the `satqe-jenkins-slave` DeploymentConfig.

The DeploymentConfig should be updated to include increased limits on resources for the `satqe-jenkins-slave`

```
template:
  spec:
    containers:
      - name: jenkins-slave
        resources:
          limits:
            cpu: 500m
            memory: 2000Mi
```



## Manual Configuration not supported with Casc.yaml

Currently, the GHPRB plugin is no having support for casc.yaml file for setting the credentials for the GHPRB Plugin. Which means we need to set this credentials whenever we reload the Jenkins configuration.

Navigate to Jenkins Url-> Manage Plugin -> Configure System -> GitHub Pull Request Builder -> Select the correct credentials and make sure test the API connections.


# Testing pull requests

A tl;dr style guide to testing your pull requests can be found at: https://docs.engineering.redhat.com/display/SQE/Creating+and+testing+satelliteqe-jenkins+PRs
