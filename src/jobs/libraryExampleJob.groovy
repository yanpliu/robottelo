// This example shows how to import functionality from libraries instead of putting everything in this file.
// This is ideal for reusing common reusable steps.
// Notes:
//  - To use this you must add the extra classpath in your seed job (see seed.groovy -> additionalClasspath)
//  - This will be blocked with jenkins security roles and must be approved with
//    Manage Jenkins > In Process Script Approval, on the jenkins master.

import jobLib.myLib  // see the library in src/jobLib/myLib.groovy

def job = job('Library Example') {
  steps {
    shell 'echo "Hello Library."'
  }
}

myLib.addIrcNotifier(job)  // we could also pass the irc channel but will rely on the default

