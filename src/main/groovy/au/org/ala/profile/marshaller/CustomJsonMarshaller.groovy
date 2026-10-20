package au.org.ala.profile.marshaller

import au.org.ala.profile.Attribute
import au.org.ala.profile.AuditMessage
import au.org.ala.profile.Opus
import au.org.ala.profile.Profile
import grails.converters.JSON

public class AuditMessageMarshaller {

    void register() {
        JSON.registerObjectMarshaller(AuditMessage) { AuditMessage auditMessage ->

            def object = null

            if(auditMessage.entityType == Attribute.class.getName()){
                object = new Attribute(auditMessage.entity)
            } else if (auditMessage.entityType == Profile.class.getName()) {
                object = new Profile(auditMessage.entity)
                if (object.draft) {
                    object = new Profile(object.draft.properties)
                    object.privateMode = true // set the privateMode flag to indicate that the profile was in draft mode at the time the audit was taken
                    object.attributes = null // attributes are not included in the audit history of the profile - they have their own history
                }
            }
            return [
                    uuid : auditMessage.entityId,
                    userId : auditMessage.userId,
                    date : auditMessage.date,
                    userDisplayName : auditMessage.userDisplayName,
                    object: object
            ]
        }
    }

}