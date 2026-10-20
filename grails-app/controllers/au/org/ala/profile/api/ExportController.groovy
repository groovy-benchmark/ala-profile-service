package au.org.ala.profile.api

import au.org.ala.profile.BaseController
import au.org.ala.profile.ExportService
import au.org.ala.profile.Opus
import au.org.ala.profile.security.RequiresAccessToken
import grails.converters.JSON

class ExportController extends BaseController {

    static final int DEFAULT_MAXIMUM_PAGE_SIZE = 500

    ExportService exportService

    def countProfiles() {
        if (!params.opusId) {
            badRequest "opusId is a required parameter"
        } else {
            Opus opus = getOpus()

            if (!opus) {
                notFound "No matching collection was found for opus id ${params.opusId}"
            } else {
                boolean includeArchived = params.includeArchived?.toBoolean()

                int count = exportService.getProfileCount(opus, includeArchived)

                render([profiles: count] as JSON)
            }
        }
    }

    @RequiresAccessToken
    def exportCollection() {
        if (!params.opusId) {
            badRequest "opusId is a required parameter"
        } else {
            Opus opus = getOpus()

            if (!opus) {
                notFound "No matching collection was found for opus id ${params.opusId}"
            } else {
                int max = params.max ? params.max as int : DEFAULT_MAXIMUM_PAGE_SIZE
                int offset = params.offset ? params.offset as int : 0
                boolean includeArchived = params.includeArchived?.toBoolean()
                boolean summary = params.summary?.toBoolean()

                response.contentType = 'application/json'
                exportService.exportCollection(response.outputStream, opus, max, offset, summary, includeArchived)
            }
        }
    }

    def getProfiles() {
        if (!params.profileNames && !params.guids) {
            badRequest "At least 1 profileNames or GUID must be provided"
        } else {
            List<String> opusIds = params.opusIds?.split(",") ?: []
            List<String> tags = params.tags?.split(",") ?: []
            List<String> profileNames = params.profileNames?.split(",") ?: []
            List<String> guids = params.guids?.split(",") ?: []
            boolean summary = params.summary?.toBoolean()

            response.contentType = 'application/json'
            exportService.exportProfiles(response.outputStream, opusIds, tags, profileNames, guids, summary)
        }
    }


}
