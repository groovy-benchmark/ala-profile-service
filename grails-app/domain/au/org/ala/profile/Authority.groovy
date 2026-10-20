package au.org.ala.profile

import au.org.ala.profile.security.Role
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@ToString(allProperties = false, excludes = 'user')
//@ToString(excludes = 'user')
@EqualsAndHashCode(excludes = 'user')
class Authority {

    String uuid 
    Contributor user
    Role role
    String notes

    static belongsTo = [Opus, Contributor]

    static constraints = {
        notes nullable: true
    }

    def beforeValidate() {
        if (uuid == null) {
            uuid = UUID.randomUUID().toString()
        }
    }
}
