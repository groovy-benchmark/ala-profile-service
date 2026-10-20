package au.org.ala.profile

import grails.gorm.transactions.Transactional

@Transactional
class UserSettingsService extends BaseDataAccessService {

    /**
     * Gets the UserSettings for a given user id, inserting a new one if there was no existing user settings.
     * @param userId The user id
     * @return The user settings
     */
    UserSettings getUserSettings(String userId) {
        checkArgument userId
        def settings = UserSettings.get(userId)
        if (!settings) {
            def newSettings = new UserSettings(id: userId)
            newSettings.id = userId
            settings = newSettings.insert(validate: true, failOnError: true, flush: true)
        }
        return settings
    }

    /**
     * Set the florula list for the given user, opus
     * @param userId The user id
     * @param opusUuid The opus id
     * @param listId The list drUid
     */
    void setFlorulaList(UserSettings userSettings, String opusUuid, String listId) {
        checkArgument userSettings
        userSettings.enableFlorulaList(opusUuid, listId)
        userSettings.markDirty('allFlorulaSettings')
        userSettings.save(validate: true, failOnError: true, flush: true)
    }
}
