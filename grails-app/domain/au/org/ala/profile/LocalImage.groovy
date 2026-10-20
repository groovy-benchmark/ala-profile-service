package au.org.ala.profile

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@EqualsAndHashCode
@ToString
class LocalImage {

    String imageId
    String originalFileName
    String title
    String description
    String rightsHolder
    String rights
    String licence
    String creator
    String contentType
    String created

    static mapping = {
        created matches: /\d{4}-\d{1,2}-\d{1,2}/
    }
}
