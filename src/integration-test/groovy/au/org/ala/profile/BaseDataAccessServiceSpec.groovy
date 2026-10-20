package au.org.ala.profile

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import spock.lang.Unroll

@Integration
@Unroll
@Rollback
class BaseDataAccessServiceSpec extends BaseIntegrationSpec {

    BaseDataAccessService service = new BaseDataAccessService()

    def "save should throw IllegalStateException if the entity is null"() {
        when:
        service.save(null)

        then:
        thrown(IllegalStateException)
    }

    def "save should return true if the entity was successfully saved"() {
        when:
        Opus opus = new Opus(title: "new opus", glossary: new Glossary(), dataResourceUid: "drId")
        boolean success = service.save(opus)

        then:
        success
        Opus.count() == 1
    }

    def "save should return false if the entity was not successfully saved"() {
        when:
        Opus opus = new Opus(title: null /* title is a mandatory field */, glossary: new Glossary(), dataResourceUid: "drId")
        boolean success = service.save(opus)

        then:
        !success
    }

    def "delete should throw IllegalStateException if the entity is null"() {
        when:
        service.delete(null)

        then:
        thrown(IllegalStateException)
    }

    def "delete should return true if the entity was successfully deleted"() {
        given:
        Opus opus = new Opus(title: "new opus", glossary: new Glossary(), dataResourceUid: "drId")
        save opus

        expect:
        Opus.count() == 1

        when:
        boolean success = service.delete(opus)

        then:
        success
        Opus.count() == 0
    }

    def "checkArgument should throw an IllegalArgumentException for null or empty arguments"() {
        when:
        service.checkArgument(value)

        then:
        thrown(IllegalArgumentException)

        where:
        value << [null, [], "", [:]]
    }

    def "checkState should not throw an IllegalArgumentException non-null or non-empty arguments or booleans"() {
        when:
        service.checkArgument(value)

        then:
        noExceptionThrown()

        where:
        value << [new Object(), ["a","b"], "xyz", [a: "bla"], true, false, 1]
    }

    def "checkState should throw an IllegalStateException for null, empty arguments, or false"() {
        when:
        service.checkState(value)

        then:
        thrown(IllegalStateException)

        where:
        value << [null, [], "", [:], false]
    }

    def "checkState should not throw an IllegalStateException non-null, non-empty arguments or true"() {
        when:
        service.checkState(value)

        then:
        noExceptionThrown()

        where:
        value << [new Object(), ["a","b"], "xyz", [a: "bla"], true, 1]
    }

}
