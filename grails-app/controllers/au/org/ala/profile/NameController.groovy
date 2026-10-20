package au.org.ala.profile

class NameController {

    def nameService

    def clearCache() {
        nameService.clearNSLNameDumpCache()
    }
}
