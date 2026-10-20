package au.org.ala.profile

import au.org.ala.profile.sanitizer.SanitizedHtml
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@EqualsAndHashCode
@ToString
class Attribute implements Comparable<Attribute> {

    static auditable = true

    static searchable = {
        root = false
        only = ["text", "title"]
        title component: true
   }

    String uuid
    Term title
    @SanitizedHtml
    String text // = "This animal lives...."
    Attribute original // The original attribute this was copied from
    String source
    List<Double> numbers
    NumberRange numberRange
    List<String> constraintList

    Date dateCreated
    Date lastUpdated

    static hasMany = [creators: Contributor, editors: Contributor]

    static belongsTo = [profile: Profile]

    static embedded = ["numberRange"]

    static constraints = {
        text nullable: true
        original nullable: true
        source nullable: true
        numbers nullable: true
        numberRange nullable: true
        constraintList nullable: true
    }

    static mapping = {
        profile index: true
        uuid index: true
        title fetch: 'join'
        profile fetch: 'join'
        creators fetch: 'join'
        editors fetch: 'join'
    }

    def beforeValidate() {
        if (uuid == null) {
            //mint an UUID
            uuid = UUID.randomUUID().toString()
        }
        if (text == null) {
            text = ''
        }
    }

    int compareTo(Attribute right) {
        if (title.order == right.title.order) {
            if (title.name.equalsIgnoreCase(right.title.name)) {
                text <=> right.text
            } else {
                title.name.toLowerCase() <=> right.title.name.toLowerCase()
            }
        } else {
            title.order <=> right.title.order
        }
    }
}
