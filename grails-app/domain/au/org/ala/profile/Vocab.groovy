package au.org.ala.profile

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@EqualsAndHashCode(excludes='terms')
@ToString(allProperties = false, excludes='terms')
//@ToString(excludes='terms')
class Vocab {

    String uuid
    String name
    boolean strict = false
    List<Term> terms
    static hasMany = [terms: Term]

    static constraints = {}

    def beforeValidate() {
        if (uuid == null) {
            uuid = UUID.randomUUID().toString()
        }
    }

    static mapping = {
        terms fetch: 'join'
    }
}
