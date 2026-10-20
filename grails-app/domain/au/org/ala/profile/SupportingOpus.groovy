package au.org.ala.profile

import au.org.ala.profile.util.ShareRequestStatus
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@ToString
@EqualsAndHashCode
class SupportingOpus {

    String uuid
    String title
    ShareRequestStatus requestStatus
    Date dateRequested
    Date dateApproved
    Date dateRejected

    static constraints = {
        dateRejected nullable: true
        dateRequested nullable: true
        dateApproved nullable: true
    }

}
