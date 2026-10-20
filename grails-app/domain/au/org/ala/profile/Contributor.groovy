package au.org.ala.profile

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@EqualsAndHashCode
@ToString
class Contributor {

    String uuid
    String userId  //CAS ID may be null
    String name
    String dataResourceUid

    Date dateCreated
    Date lastUpdated

    def beforeValidate() {
        if (uuid == null) {
            //mint an UUID
            uuid = UUID.randomUUID().toString()
        }
    }

    static constraints = {
        userId nullable: true
        dataResourceUid nullable: true
    }

    static mapping = {
    }
}
