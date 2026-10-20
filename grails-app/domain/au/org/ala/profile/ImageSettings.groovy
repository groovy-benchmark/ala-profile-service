package au.org.ala.profile

import au.org.ala.profile.sanitizer.SanitizedHtml
import au.org.ala.profile.sanitizer.SanitizerPolicyConstants
import au.org.ala.profile.util.ImageOption
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@EqualsAndHashCode
@ToString
class ImageSettings {

    @SanitizedHtml(SanitizerPolicyConstants.SINGLE_LINE)
    String caption
    ImageOption imageDisplayOption

    static mapping = {
        version false
    }

    static constraints = {
        caption nullable: true
        imageDisplayOption nullable: true
    }
}
