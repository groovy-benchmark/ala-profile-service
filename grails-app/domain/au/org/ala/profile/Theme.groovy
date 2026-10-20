package au.org.ala.profile

class Theme {

    String mainBackgroundColour
    String mainTextColour
    String callToActionHoverColour
    String callToActionColour
    String callToActionTextColour
    String footerBackgroundColour
    String footerTextColour
    String footerBorderColour
    String headerTextColour
    String headerBorderColour

    static constraints = {
         mainBackgroundColour nullable: true
         mainTextColour nullable: true
         callToActionHoverColour nullable: true
         callToActionColour nullable: true
         callToActionTextColour nullable: true
         footerBackgroundColour nullable: true
         footerTextColour nullable: true
         footerBorderColour nullable: true
         headerTextColour nullable: true
         headerBorderColour nullable: true
    }
}
