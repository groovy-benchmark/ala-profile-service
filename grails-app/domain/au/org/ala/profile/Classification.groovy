package au.org.ala.profile

import grails.gorm.dirty.checking.DirtyCheck
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

import jakarta.persistence.Transient

@ToString
@EqualsAndHashCode
@DirtyCheck
class Classification {
    String rank
    String guid
    String name

    // the following transient fields are populated whenever a profile is retrieved, and are used for hierarchical
    // navigation based on the profile's taxonomy
    @Transient
    Integer childCount
    @Transient
    boolean hasChildren = false
    @Transient
    String profileId
    @Transient
    String profileName

    static constraints = {

    }

    static mapping = {
        name index: true
        rank index: true
    }
}
