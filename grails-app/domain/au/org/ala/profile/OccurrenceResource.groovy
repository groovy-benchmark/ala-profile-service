package au.org.ala.profile

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

/**
 * Represents an occurrence dataset that has been uploaded.
 */
@EqualsAndHashCode
@ToString
class OccurrenceResource {

    String name
    String webserviceUrl        //http://sandbox.ala.org.au/biocache-service
    String uiUrl                //http://sandbox.ala.org.au/ala-hub
    String dataResourceUid
    String pointColour

    static constraints = {}
}
