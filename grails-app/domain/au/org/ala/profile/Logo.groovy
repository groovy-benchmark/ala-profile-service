package au.org.ala.profile

class Logo {
    String logoUrl
    String hyperlink

    static constraints = {
        logoUrl nullable: false
        hyperlink nullable: true
    }
}
