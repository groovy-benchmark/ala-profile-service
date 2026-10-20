package au.org.ala.profile

import org.bson.types.ObjectId


/**
 * Represents multimedia documents that can be embeded on a profile
 */
class Document {

    def grailsApplication

    static mapping = {
        name index: true
        documentId index: true // does this even do anything on an embedded doc?
    }

    ObjectId id
    String documentId
    String name // caption, document title, etc
    String attribution  // source, owner
    String licence
    String type // video, audio etc
    String url

    Date dateCreated
    Date lastUpdated


    static constraints = {
        name nullable: false
        url nullable: false
        type nullable: false
        attribution nullable: true
        licence nullable: true
        type nullable: true
    }
}
