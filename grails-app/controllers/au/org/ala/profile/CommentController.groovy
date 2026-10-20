package au.org.ala.profile

import au.ala.org.ws.security.RequireApiKey
import grails.converters.JSON

@RequireApiKey
class CommentController extends BaseController {

    CommentService commentService

    def addComment() {
        def json = request.getJSON()

        if (!params.profileId || !json) {
            badRequest()
        } else {
            Profile profile = getProfile()

            if (!profile) {
                notFound "Profile ${params.profileId} not found"
            } else {
                Comment comment = commentService.createComment(profile.uuid, json)

                render comment as JSON
            }
        }
    }

    def updateComment() {
        def json = request.getJSON()

        if (!params.commentId || !json) {
            badRequest()
        } else {
            Comment comment = Comment.findByUuid(params.commentId)

            if (!comment) {
                notFound()
            } else {
                boolean updated = commentService.updateComment(params.commentId, json)

                success([updated: updated])
            }
        }
    }

    def getComments() {
        if (!params.profileId) {
            badRequest()
        } else {
            Profile profile = getProfile()

            if (!profile) {
                notFound "Profile ${params.profileId} not found"
            } else {
                List<Comment> comments = commentService.getCommentsForProfile(profile.uuid)

                render comments as JSON
            }
        }
    }

    def getComment() {
        if (!params.profileId || !params.commentId) {
            badRequest()
        } else {
            Profile profile = getProfile()

            if (!profile) {
                notFound "Profile ${params.profileId} not found"
            } else {
                Comment comment = Comment.findByUuid(params.commentId)

                render comment as JSON
            }
        }
    }

    def deleteComment() {
        if (!params.commentId) {
            badRequest()
        } else {
            boolean deleted = commentService.deleteComment(params.commentId)

            success([deleted: deleted])
        }
    }
}
