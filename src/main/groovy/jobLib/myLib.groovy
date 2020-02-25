package jobLib

import javaposse.jobdsl.dsl.Job

class myLib {
  // job: the job object we will be modifying
  // channel:  the irc channel to message, notice default to #mychannel
  static addIrcNotifier = { Job job, String room = '#mychannel' ->
    job.with {
      publishers {
        // https://jenkinsci.github.io/job-dsl-plugin/#method/javaposse.jobdsl.dsl.helpers.publisher.PublisherContext.irc
        irc {
          channel(room)
          strategy('FAILURE_AND_FIXED')
          notificationMessage('Default')
        }
      }
    }
  }
}
