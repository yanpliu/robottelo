# Jenkins DSL Example Project

This repo is a template for Jenkins Job and Pipeline DSL.  It has examples on how to create jobs in various scenarios. Ideally this project would be used as a starting point for your Jenkins CI repo - replacing the examples with your jobs.

## File structure

    .
    ├── src
    │   ├── jobs          # DSL job definition files
    │   ├── main
    │   │   ├── groovy    # support classes
    │   │   └── resources # IDE support for IDEA / IntelliJ
    │   ├── resources     # resources for DSL scripts
    │   └── test
    │       └── groovy    # spec tests
    ├── Vagrantfile       # vagrant for a dsl development jenkins
    ├── ansible           # sources for vagrant
    ├── container            # container sources
    └── build.gradle      # build file

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
cd container
podman build -t jenkins-example .
podman run --rm --name jenkins-example -p 8080:8080 -v "$HOME/Projects/jenkins-dsl-template":/var/jenkins_home/dsl-dev  jenkins-example
```
* replace `$HOME/Projects/jenkins-dsl-template` with whever you have this project

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
