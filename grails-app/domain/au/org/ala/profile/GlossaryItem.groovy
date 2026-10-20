package au.org.ala.profile

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@EqualsAndHashCode(excludes = 'cf,glossary')
@ToString(allProperties = false, excludes = 'cf,glossary')
//@ToString(excludes = 'cf,glossary')
class GlossaryItem {
    String uuid
    String term
    String description

    static hasMany = [cf: GlossaryItem]

    static belongsTo = [glossary: Glossary]

    static mapping = {
        glossary fetch: 'join'
        cf fetch: 'join'
    }

    def beforeValidate() {
        if (uuid == null) {
            uuid = UUID.randomUUID().toString()
        }
    }
}
