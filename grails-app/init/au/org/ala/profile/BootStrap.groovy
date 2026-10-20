package au.org.ala.profile

import au.org.ala.profile.listener.AuditListener
import au.org.ala.profile.listener.LastUpdateListener
import au.org.ala.profile.listener.ValueConverterListener
import au.org.ala.profile.sanitizer.SanitizedHtml
import org.bson.BsonDocument
import org.grails.datastore.mapping.core.Datastore
import org.springframework.web.context.WebApplicationContext

class BootStrap {

    def auditService
    def userService
    def grailsApplication
    def sanitizerPolicy
    def customObjectMarshallers

    def init = { servletContext ->
        def ctx = servletContext?.getAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE) ?: grailsApplication.mainContext
        overrideClassMethod()
        initDatastores(ctx)

        customObjectMarshallers?.register()

        createDefaultTags()
    }
    def destroy = {
    }

    void overrideClassMethod() {
        // grails-datastore-gorm-mongodb:6.1.7.RELEASE has a bug which prevents embedded objects from saving. It occurs
        // since {@link org.bson.BsonDocument.asBoolean} throws an exception when Groovy tries to convert BsonDocument to
        // a boolean. The below hack is to provided the correct Groovy behaviour.
        BsonDocument.metaClass.asBoolean = {
            !delegate.isEmpty()
        }
    }

    void createDefaultTags() {
        if (Tag.count() == 0) {
            Tag iek = new Tag(uuid: UUID.randomUUID().toString(), abbrev: "IEK", name: "Indigenous Ecological Knowledge", colour: "#c7311c")
            iek.save(flush: true)

            Tag flora = new Tag(uuid: UUID.randomUUID().toString(), abbrev: "FLORA", name: "Flora Treatments", colour: "#2ac71c")
            flora.save(flush: true)

            Tag fauna = new Tag(uuid: UUID.randomUUID().toString(), abbrev: "FAUNA", name: "Fauna Treatments", colour: "#8d968c")
            fauna.save(flush: true)
        }
    }


    // Add custom GORM event listeners
    def initDatastores(ctx) {
        ctx?.getBeansOfType(Datastore)?.values().each { Datastore d ->
            log.info "Adding listener for datastore: ${d}"
            ctx.addApplicationListener new AuditListener(d, auditService)
            ctx.addApplicationListener new LastUpdateListener(d, userService)
            ctx.addApplicationListener(ValueConverterListener.of(d, SanitizedHtml, String, sanitizerPolicy.&sanitizeField))
        }
    }

}