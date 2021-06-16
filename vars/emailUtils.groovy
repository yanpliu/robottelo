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