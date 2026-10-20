package au.org.ala.profile

class NameRematch {

    String uuid
    Map results
    Date startDate
    Date endDate
    List<String> opusIds
    Integer numberOfProfilesChanged
    Integer numberOfProfilesChecked

    static constraints = {
        results nullable: true
        endDate nullable: true
        numberOfProfilesChanged nullable: true
        numberOfProfilesChecked nullable: true
    }

}
