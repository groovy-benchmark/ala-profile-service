package au.org.ala.profile.listener

import au.org.ala.web.AuthService
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEventListener
import org.grails.datastore.mapping.engine.event.EventType
import org.springframework.context.ApplicationEvent


class LastUpdateListener extends AbstractPersistenceEventListener {

    AuthService authService

    public LastUpdateListener(Datastore datastore, AuthService authService) {
        super(datastore)
        this.authService = authService
    }

    @Override
    protected void onPersistenceEvent(AbstractPersistenceEvent event) {
        if (event.eventType == EventType.PreInsert) {
            if (event.entityObject.properties.containsKey("createdBy")) {
                event.getEntityAccess().setProperty("createdBy", getUser())
                event.getEntityAccess().setProperty("lastUpdatedBy", getUser())
                event.entityObject.lastUpdatedBy = getUser()
                event.entityObject.createdBy = getUser()
            }
        } else if (event.eventType == EventType.PreUpdate) {
            if (event.entityObject.properties.containsKey("lastUpdatedBy")) {
                event.getEntityAccess().setProperty("lastUpdatedBy", getUser())
                event.entityObject.lastUpdatedBy = getUser()
            }
        }
    }

    private String getUser() {

        String user
        try {
            user = authService.getUserForUserId(authService.getUserId()).displayName
        } catch (IllegalStateException e) {
            // IllegalStateException will occur when the auth service is used outside the context of a web request - for
            // example, by the bulk data import process. In that case, set the user to 'System'
            user = "System"
        } catch (Exception e) {
            // IllegalStateException will occur when the auth service is used outside the context of a web request - for
            // example, by the bulk data import process. In that case, set the user to 'System'
            user = "Unknown"
        }

        user
    }

    @Override
    boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
        return true
    }
}
