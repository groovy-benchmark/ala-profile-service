package au.org.ala.profile

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@ToString
@EqualsAndHashCode
class Name {

    private static final String NOT_ANALYZED_INDEX = "true"

    static searchable = {
        root = false
        only = ["scientificName", "fullName", "nameAuthor"]
        scientificName multi_field: true
        fullName multi_field: true
        nameAuthor index: NOT_ANALYZED_INDEX
    }

    String scientificName
    String nameAuthor
    String fullName
    String guid

    static constraints = {
        scientificName nullable: true
        nameAuthor nullable: true
        fullName nullable: true
        guid nullable: true
    }

    static mapping = {
        nameAuthor index: true
    }
}
