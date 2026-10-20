package au.org.ala.profile

import au.ala.org.ws.security.RequireApiKey
import grails.converters.JSON
import groovy.json.JsonSlurper

@RequireApiKey
class ImportController extends BaseController {

    static allowedMethods = [vocab: "POST", profile: "POST", glossary: "POST"]

    VocabService vocabService
    ImportService importService
    OpusService opusService
    NameService nameService
    BieService bieService

    def vocab() {
        def json = request.getJSON()
        if (!json || !json.opusId || !json.terms) {
            badRequest(samples.vocab)
        } else {
            Opus opus = Opus.findByUuid(json.opusId)

            vocabService.updateVocab(opus.attributeVocabUuid, json)
        }
    }

    def glossary() {
        def json = request.getJSON()
        if (!json || (!json.glossaryId && !json.opusId)) {
            badRequest(samples.glossary)
        } else {
            opusService.saveGlossaryItems(json.opusId, json)
        }
    }

    def profile() {
        def json = request.getJSON()
        if (!json || !json.opusId || !json.profiles) {
            badRequest(samples.profile)
        } else {
            Opus opus = Opus.findByUuid(json.opusId)
            if (!opus) {
                notFound "Opus ${json.opusId} does not exist."
            } else {
                String importId = UUID.randomUUID().toString()

                importService.importProfiles(importId, json.opusId, json.profiles)

                render ([status: "IN_PROGRESS", id: importId, report: ""] as JSON)
            }
        }
    }

    def report() {
        if (!params.importId) {
            badRequest "importId is a required parameter"
        } else {
            File reportFile = new File("${grailsApplication.config.getProperty('temp.file.directory')}/${params.importId}.json")

            if (!reportFile.exists()) {
                reportFile = new File("${grailsApplication.config.getProperty('temp.file.directory')}/${params.importId}.json.inprogress")

                if (!reportFile.exists()) {
                    notFound "No matching import report was found for id ${params.id}"
                } else {
                    render ([status: "IN_PROGRESS", id: params.importId, report: ""] as JSON)
                }
            } else {
                Map report = new JsonSlurper().parseText(reportFile.text)
                render ([status: "COMPLETE", id: params.importId, report: report] as JSON)
            }
        }
    }

    /** Checks that an image URL supplied in an import file can be reached, before the import is submitted. */
    def checkImageUrl() {
        def json = request.getJSON()
        if (!json || !json.identifier) {
            badRequest "identifier is a required parameter"
        } else {
            URL imageUrl = new URL(json.identifier as String)
            HttpURLConnection connection = (HttpURLConnection) imageUrl.openConnection() // SINK: PLANTED-GR-HR-252
            connection.setRequestMethod("HEAD")
            connection.setConnectTimeout(5000)
            connection.setReadTimeout(5000)
            render ([status: connection.responseCode, contentType: connection.contentType, length: connection.contentLengthLong] as JSON)
        }
    }

    /** As checkImageUrl, but only for the image hosts an import is allowed to pull from. */
    def checkImageUrlRestricted() {
        def json = request.getJSON()
        if (!json || !json.identifier) {
            badRequest "identifier is a required parameter"
        } else {
            URI imageUri = new URI(json.identifier as String)
            List<String> allowedHosts = (grailsApplication.config.getProperty('image.allowed.hosts', String, 'images.ala.org.au')).split(',')*.trim()
            if (!(imageUri.scheme in ['http', 'https']) || !(imageUri.host in allowedHosts)) {
                badRequest "Images can only be imported from ${allowedHosts}"
            } else {
                HttpURLConnection connection = (HttpURLConnection) imageUri.toURL().openConnection() // SAFE_SINK: PLANTED-GR-HR-252-safe
                connection.setInstanceFollowRedirects(false)
                connection.setRequestMethod("HEAD")
                connection.setConnectTimeout(5000)
                connection.setReadTimeout(5000)
                render ([status: connection.responseCode, contentType: connection.contentType, length: connection.contentLengthLong] as JSON)
            }
        }
    }

    /** Looks a name up against a name-matching service nominated in the import request. */
    def matchNames() {
        def json = request.getJSON()
        if (!json || !json.matchUrl || !json.name) {
            badRequest "matchUrl and name are required parameters"
        } else {
            Map source = [url: json.matchUrl as String, rank: json.rank as String]
            render (nameService.matchNameAgainst(source, json.name as String) as JSON)
        }
    }

    /** Looks a name up against one of the configured name-matching services (selected by key). */
    def matchNamesRegistered() {
        def json = request.getJSON()
        if (!json || !json.source || !json.name) {
            badRequest "source and name are required parameters"
        } else {
            Map result = nameService.matchNameAgainstRegistered(json.source as String, json.name as String)
            if (result == null) {
                badRequest "Unknown name source ${json.source}"
            } else {
                render (result as JSON)
            }
        }
    }

    /** Previews the first rows of a name dump that is to be imported from a URL. */
    def previewNameDump() {
        def json = request.getJSON()
        if (!json || !json.url) {
            badRequest "url is a required parameter"
        } else {
            render (nameService.previewNameDump(json.url as String) as JSON)
        }
    }

    def previewNameDumpRestricted() {
        def json = request.getJSON()
        if (!json || !json.url) {
            badRequest "url is a required parameter"
        } else {
            render (nameService.previewNameDumpRestricted(json.url as String) as JSON)
        }
    }

    /** Retrieves a species profile from a BIE instance; 'mode' picks between the requested host and the configured one. */
    def bieProfile() {
        def json = request.getJSON()
        if (!json || !json.guid) {
            badRequest "guid is a required parameter"
        } else {
            Map profile = bieService.fetcherFor(json.mode as String).call(json.scheme as String, json.host as String, json.guid as String)
            render ((profile ?: [:]) as JSON)
        }
    }

    Map samples = [
            vocab: """{"opusId": "", "strict": "true|false", "deleteExisting": "true|false", "terms": ["term1", "term2", "..."]}""",
            glossary: """{"opusId|glossaryId": "", "items": [{"itemId": "", "term": "", "definition": "", "cf": ["term1","term2", "..."]}]}""",
            profile: """{
                    "opusId": "",
                    "profiles":[{
                            "scientificName": "",
                            "nameAuthor": "",
                            "links":[{
                                    "creators": [""],
                                    "edition": "",
                                    "title": "",
                                    "publisherName": "",
                                    "fullTitle":"",
                                    "description": "",
                                    "url":"",
                                    "doi":""
                                    }],
                            "bhl":[{
                                    "creators": [""],
                                    "edition": "",
                                    "title": "",
                                    "publisherName": "",
                                    "fullTitle":"",
                                    "description": "",
                                    "url":"",
                                    "doi":""
                                    }],
                            "attributes": [{
                                    "creators": [""],
                                    "editors": [""],
                                    "title": "",
                                    "text": ""
                                    }],
                            "authorship": [{
                                    "category": "",
                                    "text": ""
                                    }],
                            "images": [{
                                    "identifier": "url to the image - required",
                                    "title": "required",
                                    "creator": "",
                                    "created": ""
                                    }]
                        }]
                     }"""
    ]
}
