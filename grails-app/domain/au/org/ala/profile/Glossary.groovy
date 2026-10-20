package au.org.ala.profile

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@EqualsAndHashCode(excludes = 'items')
@ToString(allProperties = false, excludes = 'items', ignoreNulls = true)
//@ToString(excludes = 'items', ignoreNulls = true)
class Glossary {
    String uuid
    List items

    static hasMany = [items: GlossaryItem]

    static constraints = {
    }

    static mapping = {
        items cascade: "all-delete-orphan", fetch: 'join'
    }

    def beforeValidate() {
        if (uuid == null) {
            uuid = UUID.randomUUID().toString()
        }
    }

    @Override
    String toString() {
        [uuid: uuid].toString()
    }
}
