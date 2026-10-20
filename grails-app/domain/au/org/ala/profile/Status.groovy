package au.org.ala.profile

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@EqualsAndHashCode
@ToString
class Status {
    boolean searchReindex = false
    int lastReindexDuration = -1

    static constraints = {

    }
}
