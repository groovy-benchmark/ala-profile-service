package au.org.ala.profile.util

import spock.lang.Specification

class UtilsSpec extends Specification {

    def "sanitizeRegex should remove all unsafe regex symbols"() {
        when:
        String safe = Utils.sanitizeRegex('abc *+?|\\^$:= 123')

        then:
        safe == "abc  123"
    }

    def "sanitizeRegex should escape all whitelisted regex symbols"() {
        when:
        String safe = Utils.sanitizeRegex('abc .()- 123')

        then:
        safe == "abc \\.\\(\\)\\- 123"
    }
}
