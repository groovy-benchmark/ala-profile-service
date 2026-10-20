package au.org.ala.profile

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@EqualsAndHashCode
@ToString
class Tag {
    String uuid
    String abbrev
    String name
    String colour

    static constraints = {

    }
}
