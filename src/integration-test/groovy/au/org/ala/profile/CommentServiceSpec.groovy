package au.org.ala.profile

import au.org.ala.profile.UserService
import au.org.ala.web.UserDetails
import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration

@Integration
@Rollback
class CommentServiceSpec extends BaseIntegrationSpec {

    CommentService service = new CommentService()

    def setup() {
        service.userService = Mock(UserService)
        service.userService.getUserForUserId(_) >> new UserDetails(userId: '1234', firstName: 'fred', lastName: 'fred')
        service.userService.getUserId() >> "1234"
    }

    def "createComment should fail if no profileId or json are provided"() {
        when:
        service.createComment(null, [a:"B"])

        then:
        thrown IllegalArgumentException

        when:
        service.createComment("bla", null)

        then:
        thrown IllegalArgumentException
    }

    def "createComment should fail if the profile does not exist"() {
        when:
        service.createComment("unknown", [a:"b"])

        then:
        thrown IllegalStateException
    }

    def "createComment should create a new comment record when the profile exists"() {
        given:
        Opus opus = new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")
        save opus
        Profile profile = new Profile(opus: opus, scientificName: "sciName")
        save profile

        when:
        Comment comment = service.createComment(profile.uuid, [text: "text", profileUuid: profile.uuid])

        then:
        Comment.count() == 1
        Comment.findAllByProfileUuid(profile.uuid).size() == 1
        comment.text == "text"
    }

    def "createComment should set the author of a new comment to the current user"() {
        given:
        Opus opus = new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")
        save opus
        Profile profile = new Profile(opus: opus, scientificName: "sciName")
        save profile

        when:
        Comment comment = service.createComment(profile.uuid, [text: "text", profileUuid: profile.uuid])

        then:
        comment.author.userId == "1234"
        comment.author.name == "fred fred"
    }

    def "createComment should fail it a parent comment id has been provided but no matching comment exists"() {
        given:
        Opus opus = new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")
        save opus
        Profile profile = new Profile(opus: opus, scientificName: "sciName")
        save profile

        when:
        service.createComment(profile.uuid, [text: "text", profileUuid: profile.uuid, parentCommentId: "unknown"])

        then:
        thrown IllegalStateException
    }

    def "createComment should associate the new comment with an existing one if the parent id has been provided"() {
        given:
        Opus opus = new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")
        save opus
        Profile profile = new Profile(opus: opus, scientificName: "sciName")
        save profile
        Comment parent = new Comment(text: "parentText", profileUuid: profile.uuid, author: new Contributor(userId: "111", name: "bob"))
        save parent

        expect:
        Comment.count() == 1

        when:
        Comment comment = service.createComment(profile.uuid, [text: "text", profileUuid: profile.uuid, parentCommentId: parent.uuid])
        parent = Comment.findByUuid(parent.uuid)

        then:
        Comment.count() == 2
        comment.parent.uuid == parent.uuid
        parent.children.size() == 1
    }

    def "updateComment should fail if no commentId or json are provided"() {
        when:
        service.updateComment(null, [a:"B"])

        then:
        thrown IllegalArgumentException

        when:
        service.updateComment("bla", null)

        then:
        thrown IllegalArgumentException
    }

    def "updateComment should fail if the specified comment does not exist"() {
        when:
        service.updateComment("unknown", [a:"b"])

        then:
        thrown IllegalStateException
    }

    def "updateComment should only update the text of the comment"() {
        given:
        Opus opus = new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")
        save opus
        Profile profile = new Profile(opus: opus, scientificName: "sciName")
        save profile
        Comment comment = new Comment(text: "initialText", profileUuid: profile.uuid, author: new Contributor(userId: "111", name: "bob"))
        save comment

        when:
        service.updateComment(comment.uuid, [text: "new text"])
        comment = Comment.findByUuid(comment.uuid)

        then:
        comment.text == "new text"
    }

    def "deleteComment should fail if the comment id is not provided"() {
        when:
        service.deleteComment(null)

        then:
        thrown IllegalArgumentException
    }

    def "deleteComment should fail if the specified comment does not exist"() {
        when:
        service.deleteComment("unknown")

        then:
        thrown IllegalStateException
    }

    def "deleteComment should remove the specified comment"() {
        given:
        Opus opus = new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")
        save opus
        Profile profile = new Profile(opus: opus, scientificName: "sciName")
        save profile
        Comment comment = new Comment(text: "initialText", profileUuid: profile.uuid, author: new Contributor(userId: "111", name: "bob"))
        save comment

        expect:
        Comment.count() == 1

        when:
        service.deleteComment(comment.uuid)

        then:
        Comment.count() == 0
    }

    def "getCommentsForProfile should fail if no profile id is provided"() {
        when:
        service.getCommentsForProfile(null)

        then:
        thrown IllegalArgumentException
    }

    def "getCommentsForProfile should only retrieve top-level (parent) comments for the specified profile"() {
        given:
        Opus opus = new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")
        save opus
        save new Profile(uuid: "profile1", opus: opus, scientificName: "sciName")
        save new Profile(uuid: "profile2", opus: opus, scientificName: "sciName")
        Comment parent1 = new Comment(text: "parent1", profileUuid: "profile1", author: new Contributor(userId: "111", name: "bob"))
        save parent1
        Comment child1 = new Comment(text: "child1", profileUuid: "profile1", author: new Contributor(userId: "111", name: "bob"), parent: parent1)
        save child1
        Comment parent2 = new Comment(text: "parent2", profileUuid: "profile1", author: new Contributor(userId: "111", name: "bob"))
        save parent2
        Comment parent3 = new Comment(text: "parent3", profileUuid: "profile2", author: new Contributor(userId: "111", name: "bob"))
        save parent3

        expect:
        Comment.count() == 4

        when:
        List<Comment> comments = service.getCommentsForProfile("profile1")

        then:
        comments.size() == 2
        comments.contains(parent1)
        comments.contains(parent2)
        !comments.contains(child1)
        !comments.contains(parent3)
    }

    def "comments should have HTML sanitized"() {
        given:
        Opus opus = new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")
        save opus
        Profile profile = new Profile(opus: opus, scientificName: "sciName")
        save profile

        when:
        Comment comment = service.createComment(profile.uuid, [text: "<p>text<script>alert('hi');</script></p>", profileUuid: profile.uuid])

        then:
        Comment.count() == 1
        Comment.findAllByProfileUuid(profile.uuid).size() == 1
        comment.text == "<p>text</p>"
    }
}
