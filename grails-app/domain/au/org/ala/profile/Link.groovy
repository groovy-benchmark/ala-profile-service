package au.org.ala.profile

import au.org.ala.profile.sanitizer.SanitizedHtml
import au.org.ala.profile.sanitizer.SanitizerPolicyConstants
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@EqualsAndHashCode
@ToString
class Link {

    String uuid
    String url
    @SanitizedHtml(SanitizerPolicyConstants.SINGLE_LINE)
    String title
    @SanitizedHtml(SanitizerPolicyConstants.SINGLE_LINE)
    String description
    String doi
    String edition
    String publisherName
    String fullTitle

    def beforeValidate() {
        if (!uuid) {
            //mint an UUID
            uuid = UUID.randomUUID().toString()
        }
    }

    static constraints = {
        doi nullable: true
        edition nullable: true
        publisherName nullable: true
        fullTitle nullable: true
    }
}
