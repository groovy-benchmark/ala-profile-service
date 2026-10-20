package au.org.ala.profile

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

import jakarta.persistence.Transient
@ToString
@EqualsAndHashCode
class Attachment {

    String uuid
    String filename
    String url
    String title
    String description
    String contentType
    String rightsHolder
    String rights
    String licence
    String creator
    String category
    Date createdDate

    @Transient
    String downloadUrl

    static mapping = {
        filename nullable: true
        url nullable: true
        title nullable: true
        description nullable: true
        contentType nullable: true
        rights nullable: true
        rightsHolder nullable: true
        creator nullable: true
        licence nullable: true
        createdDate nullable: true
        category nullable: true
    }

    static constraints = {
        category blank: true,  validator: {grailsApplication.config.getProperty('attachement.categories',List).value.contains(val)}
    }
}
