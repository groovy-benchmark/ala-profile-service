package au.org.ala.profile

import au.org.ala.profile.sanitizer.SanitizedHtml

class OpusLayoutConfig {
    @SanitizedHtml
    String explanatoryText
    List<Image> images = []
    Integer duration = 5000
    @SanitizedHtml
    String updatesSection
    String helpTextSearch
    String helpTextIdentify
    String helpTextFilter
    String helpTextBrowse
    String helpTextDocuments
    @SanitizedHtml
    String bannerOverlayText
    static constraints = {
        explanatoryText nullable: true
        updatesSection nullable: true
        helpTextSearch nullable: true
        helpTextIdentify nullable: true
        helpTextFilter nullable: true
        helpTextBrowse nullable: true
        helpTextDocuments nullable: true
        bannerOverlayText nullable: true
    }

    static embedded = ['images']
}
