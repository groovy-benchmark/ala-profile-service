package au.org.ala.profile

import au.org.ala.profile.sanitizer.SanitizedHtml

/**
 * Embedded entity to encapsulate the configuration items that control the branding on the UI (banners, logos, etc)
 */
class BrandingConfig {

    String opusBannerUrl // banner image for non-profile pages

    String profileBannerUrl // banner image for profile pages

    String pdfBannerUrl

    String pdfBackBannerUrl

    List<Logo> logos = []
    String thumbnailUrl

    String colourTheme
    String issn
    @SanitizedHtml
    String shortLicense
    String pdfLicense

    static constraints = {
        opusBannerUrl nullable: true
        profileBannerUrl nullable: true
        pdfBannerUrl nullable: true
        pdfBackBannerUrl nullable: true
        thumbnailUrl nullable: true
        colourTheme nullable: true
        issn nullable: true, maxSize: 8, minSize: 8
        shortLicense nullable: true
        pdfLicense nullable: true, maxSize: 500
    }

    static embedded = ["logos"]
}
