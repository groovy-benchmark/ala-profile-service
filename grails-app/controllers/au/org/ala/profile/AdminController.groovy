package au.org.ala.profile

import au.ala.org.ws.security.RequireApiKey
import grails.converters.JSON

import jakarta.validation.constraints.NotNull

@RequireApiKey
class AdminController extends BaseController {
    AdminService adminService
    BackupRestoreService backupRestoreService

    def rematchNames() {
        List opusIds = request.getJSON()?.opusIds ?: []
        NameRematch results = adminService.rematchAllNames(opusIds)

        success (results?:[:])
    }

    def getTag(String tagId) {
        if (tagId) {
            Tag tag = Tag.findByUuid(tagId)

            if (tag) {
                render tag as JSON
            } else {
                notFound "No matching tag was found for id ${tagId}"
            }
        } else {
            List<Tag> tags = Tag.list()

            render tags as JSON
        }
    }

    def createTag() {
        Map json = request.getJSON()
        if (!json) {
            badRequest "A json body is required"
        } else {
            Tag tag = adminService.createTag(json)

            render tag as JSON
        }
    }

    def deleteTag(@NotNull String tagId) {
        adminService.deleteTag(tagId)

        success [:]
    }

    def updateTag(@NotNull String tagId) {
        Map json = request.getJSON()
        if (!json) {
            badRequest "A json body is required"
        } else {
            Tag tag = adminService.updateTag(tagId, json)

            render tag as JSON
        }
    }

    def backupCollections () {
        Map json = request.getJSON()
        if (!json) {
            badRequest "A json body is required"
        } else {
            backupRestoreService.backupCollections(json.opusUuids, json.backupFolder, json.backupName)
            success [:]
        }
    }

    def restoreCollections () {
        Map json = request.getJSON()
        if (!json) {
            badRequest "A json body is required"
        } else {
            backupRestoreService.restoreCollections(json.backupFolder, json.backupNames, json.restoreDB)
            success [:]
        }
    }

}
