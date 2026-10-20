package au.org.ala.profile

class ProfileSettings {

    boolean autoFormatProfileName = true
    String  formattedNameText = ''

    static constraints = {
        formattedNameText nullable: true
    }

    static mapping = {
        autoFormatProfileName defaultValue: true
        formattedNameText defaltValue: ''
    }

}
