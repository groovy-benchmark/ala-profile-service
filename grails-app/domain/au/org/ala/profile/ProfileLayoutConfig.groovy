package au.org.ala.profile

/**
 * Embedded entity to encapsulate the configuration items that control the layout of Profile pages
 */
class ProfileLayoutConfig {
    String layout

    static constraints = {
        layout nullable: true
    }

    static mapping = {
    }
}
