package au.org.ala.profile

class UserSettings {

    // user id
    String id

    // Previously definition was Map<String, FlorulaSettings> allFlorulaSettings. However, grails 3 is having issue
    // saving FlorulaSettings class.
    Map<String, Map> allFlorulaSettings = [:]

    Date dateCreated
    Date lastUpdated

    void enableFlorulaList(String opusUuid, String listId) {
        def florulaSettings = allFlorulaSettings[opusUuid]
        if (!florulaSettings) {
            florulaSettings = [:]
            allFlorulaSettings[opusUuid] = florulaSettings
        }
        florulaSettings.drUid = listId
    }

    static mapping = {
        id generator: 'assigned', unique: true
    }
}
