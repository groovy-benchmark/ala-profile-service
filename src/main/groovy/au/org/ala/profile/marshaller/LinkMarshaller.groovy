package au.org.ala.profile.marshaller

import au.org.ala.profile.Link
import grails.converters.JSON

class LinkMarshaller {

    void register() {
        JSON.registerObjectMarshaller(Link) { Link link ->
            return [
                    "uuid"         : "${link.uuid}",
                    "url"          : "${link.url}",
                    "title"        : "${link.title}",
                    "fullTitle"    : "${link.fullTitle}",
                    "edition"      : "${link.edition}",
                    "publisherName": "${link.publisherName}",
                    "doi"          : "${link.doi}",
                    "description"  : "${link.description}",
                    "creators"     : link.creators.collect { it.name }
            ]
        }
    }

}
