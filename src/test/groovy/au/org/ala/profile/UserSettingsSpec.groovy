package au.org.ala.profile


import grails.testing.gorm.DomainUnitTest
import spock.lang.Specification

class UserSettingsSpec extends Specification implements DomainUnitTest<UserSettings>{

    def setup() {
    }

    def cleanup() {
    }

    void "test enableFlorulaList"() {
        UserSettings userSettings = new UserSettings(id: 'a')
        String opusId = 'opus'
        String listId = 'list'
        userSettings.enableFlorulaList(opusId, listId)
        userSettings.allFlorulaSettings[opusId].drUid == listId
    }
}
