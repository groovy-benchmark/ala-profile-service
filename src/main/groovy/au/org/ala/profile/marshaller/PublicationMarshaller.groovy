package au.org.ala.profile.marshaller

import au.org.ala.profile.Publication
import grails.converters.JSON

class PublicationMarshaller {
    void register() {
        JSON.registerObjectMarshaller(Publication) { Publication publication ->
            return [
                    uuid           : publication.uuid,
                    title          : publication.title,
                    description    : publication.description,
                    publicationDate: publication.publicationDate,
                    uploadDate     : publication.uploadDate,
                    authors        : publication.authors,
                    doi            : publication.doi,
                    version        : publication.version
            ]
        }
    }
}
