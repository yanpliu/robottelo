/**
 * Email notification helper class
 * Requires Email Extension Plugin to be installed and configured"
 */

def sendEmail(parameters = [:]) {
    /**
     * Send an email
     *
     * Usage:
     * emailUtils.sendEmail(
     *      'to_nicks': ['juwatts', 'kkulkarni', 'lpramuk'],
     *      'reply_nicks': ['sat-qe-jenkins', 'juwatts'],
     *      'subject': "Jenkins Build ${env.JOB_NAME} ${BUILD_NUMBER} Results: ${currentBuild.currentResult}",
     *      'body':'Testing')
     */
    def to_nicks = parameters.get('to_nicks', ['sat-qe-jenkins'])
    def reply_nicks = parameters.get('reply_nicks', ['sat-qe-jenkins'])
    def subject = parameters.get('subject', '')
    def body = parameters.get('body', '')
    def mime_type = parameters.get('mimeType','text/html')
    def attachment_pattern = parameters.get('attachmentsPattern','')

    def email_addrs = ''

    for (t_nick in to_nicks) {
        t_nick = t_nick.replaceAll("\\s","")+"@redhat.com,"
        email_addrs += t_nick
    }

    def reply_emails = ''

    for (r_nick in reply_nicks) {
        reply_emails += r_nick.replaceAll("\\s","")+"@redhat.com,"
    }

    println("""
        Composing email with:
        to: $email_addrs
        reply to: $reply_emails
        body: $body
        subject: $subject"""
    )

    if(email_addrs != null && !email_addrs.isEmpty()) {
      emailext(body: body, mimeType: mime_type,
         replyTo: reply_emails, subject: subject,
         to: email_addrs, attachLog: true, attachmentsPattern: attachment_pattern)
    } else {
        println("'To' email addresses were not provided, no email will be sent ")
    }
}

def emailBody(Map parameters = [:]) {
    def results_summary = parameters.get('results_summary', 'No results available')
    def importance = parameters.get('importance', '')
    def sat_version = parameters.get('sat_version', '6.10.0')
    def description = parameters.get('description', "${currentBuild.description}")
    def ibutsu_link = parameters.get('ibutsu_link', 'No ibutsu link available')

    def importance_html_tag = (importance != '')
    importance_html_tag = ("${importance}" != "")? "Importance: ${importance}" : ""
    email_body = """\
        <h3>${description} ${importance} Automation Results</h3>
        <h3>${importance_html_tag}</h3>
        <ul>
            <lh><h4>Result Counts</h4></lh>
            <li><b>Tests: </b> ${results_summary.getTotalCount()}</li>
            <li><b>Failures: </b> ${results_summary.getFailCount()}</li>
            <li><b>Skipped: </b> ${results_summary.getSkipCount()}</li>
            <li><b>Passed: </b> ${results_summary.getPassCount()}</li>
        </ul>
        <ul>
            <lh><h4>Result URLs</h4></lh>
            <li><a href=\"${JOB_URL}test_results_analyzer/\"><b>Jenkins Test Result Analyzer</b> (Compare builds) </a></li>
            <li><a href=\"${BUILD_URL}testReport/\"><b>Jenkins Test Results</b> (Single Build Results) </a></li>
            <li><a href=\"${ibutsu_link}\"><b>Ibutsu Test Run</b> (Analyze Failure Trends) </a></li>
        </ul>
        This email was generated automatically, if you want to improve it look here:
        <br>https://gitlab.sat.engineering.redhat.com/satelliteqe/satelliteqe-jenkins/-/blob/master/vars/emailUtils.groovy
    """
    // Include a link to sign-off sheet for z-stream builds, check sat_version
    if (sat_version.tokenize('.')[2]?.toInteger() > 0) {
        email_body = email_body + """\
            <br><br><h4>This is a z-stream snap, update component status on the <a href=\"${pipelineVars.zstream_signoffsheet}\">Sign Off Sheet</a></h4>
        """.stripIndent()
    }
    return email_body
}
