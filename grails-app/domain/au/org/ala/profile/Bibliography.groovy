package au.org.ala.profile

import au.org.ala.profile.sanitizer.SanitizedHtml
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@EqualsAndHashCode
@ToString
class Bibliography {

    String uuid
    @SanitizedHtml
    String text
    int order

}
