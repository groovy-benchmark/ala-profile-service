package au.org.ala.profile

class CommentService extends BaseDataAccessService {

    UserService userService

    Comment createComment(String profileId, Map json) {
        checkArgument profileId
        checkArgument json

        checkState Profile.findByUuid(profileId)

        Comment comment = new Comment(json)
        comment.profileUuid = profileId

        comment.author = getOrCreateContributor(userService.getUserForUserId(userService.getUserId())?.displayName, userService.getUserId())

        boolean success
        if (json.parentCommentId) {
            Comment parent = Comment.findByUuid(json.parentCommentId);
            checkState parent

            parent.addToChildren(comment)
            comment.uuid = UUID.randomUUID().toString()
            success = save parent
        } else {
            success = save comment
        }

        if (!success) {
            comment = null
        }

        comment
    }

    boolean updateComment(String commentId, Map json) {
        checkArgument commentId
        checkArgument json

        Comment comment = Comment.findByUuid(commentId)
        checkState comment

        comment.text = json.text

        save comment
    }

    boolean deleteComment(String commentId) {
        checkArgument commentId

        Comment comment = Comment.findByUuid(commentId)
        checkState comment

        delete comment
    }

    List<Comment> getCommentsForProfile(String profileId) {
        checkArgument profileId

        // we only want top level comments (i.e. where the parent is empty) - child comments will be pulled in via the one to many relationship
        Comment.findAllByProfileUuidAndParentIsNull(profileId)
    }
}
