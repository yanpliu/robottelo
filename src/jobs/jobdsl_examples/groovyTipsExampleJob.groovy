// This example covers tips and tricks in groovy that aren't directly DSL related.

/* If the file extension didn't give it away, these Job DSL scripts are actually groovy.
   It is import to note that this isn't the full groovy out in the wild, but a sandboxed groovy provided by jenkins.
   You may run into cases where certain functions are blocked.  You can unblock these by going to Manage Jenkins >
   In process Script Approval.  Note that there are significant security implications for approving these scripts as you
   can access live running Jenkins objects.  If you dig deep enough into the Jenkins source code you can pretty much do
   anything here that you can in groovy - but its not necesarily a good idea.  This example will cover the basics of
   what I find useful for dynamically creating Job DSL.
   */

// import Job object so we can manipulate it directly later
//import javaposse.jobdsl.dsl.Job

// Variables
// immutable strings are single quoted
//def foo = 'world'

// string templates are double quoted
//def bar = "Hello, ${foo}."  //Hello, world.

// you can save jobs to variables for later manipulation
//def someJob = job('Some Job') {
//    steps{
//        shell("echo '${bar}'")
//    }
//}

// you can create functions which add common resuable parts to jobs
//def addCronTrigger = { Job job, String interval = 'H/5 * * * *' ->
//    // '.with' will insert DSL closures into any Job object. Note that pipelines and matrix jobs are subclasses of Job.
//    job.with {
//        triggers {
//            cron(interval)
//        }
//    }
//}

// call the function
//addCronTrigger(someJob)

// Generating Jobs

// often times you'll want to create the same job many times with differing values;
// an easy way to do this is to loop over a configuration map (dictionary).

// here I will make key, value :: <job name>, <configuration map>
//def jobCFG = [
//    'job-foo' : ['script': 'echo hello',
//                 'description': 'This job is very friendly.',
//                 'disabled': false],
//    'job-bar' : ['script': 'echo what',
//                 'description': 'This job is very confused.',
//                 'disabled': true,
//                 'trigger': 'H/2 * * * *']
//]

// loop over this config map to create multiple jobs at once
//jobCFG.each { jobName, config ->  // destructure the map to make it easier to work with
//    job(jobName) {
//        description(config['description'])
//        disabled(config['disabled'])
//        steps{
//            shell(config['script'])
//        }
//        // you can also conditionally add DSL
//        if (config.containsKey('trigger')) {
//            triggers {
//                cron(config['trigger'])
//            }
//        }
//    }
//}
