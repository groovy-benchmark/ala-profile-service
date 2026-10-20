package au.org.ala.profile.marshaller

import au.org.ala.profile.Glossary
import grails.converters.JSON

class GlossaryMarshaller {
    void register() {
        JSON.registerObjectMarshaller(Glossary) { Glossary glossary ->
            return [
                    uuid : glossary.uuid,
                    items: glossary.items.collect { [uuid: it.uuid, term: it.term, description: it.description, cf: it.cf] }
            ]
        }
    }

}
