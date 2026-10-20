package au.org.ala.profile

class EmailService {
    def grailsApplication

    void sendEmail(List<String> recipients, String sender, String subjectText, String bodyHtml) {
        log.debug("Sending email to ${recipients} with subject '${subjectText}'")

        if (recipients) {
            sendMail {
                to recipients.toArray()
                from sender
                subject subjectText
                html bodyHtml
            }
        }
    }
}
