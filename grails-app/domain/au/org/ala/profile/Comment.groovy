package au.org.ala.profile

import au.org.ala.profile.sanitizer.SanitizedHtml
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@ToString(allProperties = false, excludes = 'children')
//@ToString(excludes = 'children')
@EqualsAndHashCode(excludes = 'children')
class Comment {

    String uuid
    @SanitizedHtml
    String text
    String profileUuid
    Contributor author
    Comment parent
    Date dateCreated
    Date lastUpdated
    List children

    static hasMany = [children: Comment]

    static constraints = {
        parent nullable: true
    }

    static mapping = {
        children cascade: "all-delete-orphan", fetch: 'join'
    }

    def beforeValidate() {
        if (uuid == null) {
            uuid = UUID.randomUUID().toString()
        }
    }
}
