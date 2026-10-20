package au.org.ala.profile

import au.org.ala.profile.security.Role
import au.org.ala.profile.util.ShareRequestStatus
import au.org.ala.web.AuthService
import au.org.ala.web.UserDetails
import grails.gorm.transactions.Rollback
import grails.gsp.PageRenderer
import grails.testing.mixin.integration.Integration
import org.grails.web.json.JSONObject
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.multipart.MultipartFile

@Integration
@Rollback
class OpusServiceSpec extends BaseIntegrationSpec {
    @Autowired
    OpusService service
    EmailService emailService = Mock(EmailService)

    def setup() {
        service.userService = Mock(UserService)
        service.userService.getUserForUserId(_) >> new UserDetails(userId: "123", firstName: "fred", lastName: "fred")
        service.userService.getUserId() >> "123"
        service.groovyPageRenderer = Mock(PageRenderer)
        service.groovyPageRenderer.render(_, _) >> "bla"
        service.emailService = emailService
        service.attachmentService = Mock(AttachmentService)
    }

    def "updateUserAccess should throw an IllegalArgumentException if no opus id or data are provided"() {
        when:
        service.updateUserAccess(null, [a: "b"])

        then:
        thrown IllegalArgumentException

        when:
        service.updateUserAccess("a", [:])

        then:
        thrown IllegalArgumentException
    }

    def "updateUserAccess should throw IllegalStateException if the opus does not exist"() {
        when:
        service.updateUserAccess("unknown", [a:"b"])

        then:
        thrown IllegalStateException
    }

    def "updateUserAccess should update the opus' authorities list"() {
        given:
        Opus opus = new Opus(title: "opus", dataResourceUid: "123", glossary: new Glossary())
        save opus

        when:
        service.updateUserAccess(opus.uuid, [authorities: [[userId: "user1", name: "Fred", role: "role_profile_admin", notes: "first note"],
                                                      [userId: "user2", name: "Bob", role: "role_profile_reviewer", notes: "second note"]]])
        opus = Opus.findByUuid(opus.uuid)

        then:
        Authority.count() == 2
        Contributor.count() == 2
        opus.authorities.size() == 2
        opus.authorities.find({it.user.userId == "user1"}).role == Role.ROLE_PROFILE_ADMIN
        opus.authorities.find({it.user.userId == "user2"}).role == Role.ROLE_PROFILE_REVIEWER
    }

    def "updateUserAccess should replace existing authorities list"() {
        given:
        Contributor user = new Contributor(userId: "user1", name: "jill")
        save user
        Authority auth = new Authority(user: user, role: Role.ROLE_PROFILE_ADMIN)
        save auth
        Opus opus = new Opus(title: "opus", dataResourceUid: "123", glossary: new Glossary(), authorities: [auth])
        save opus

        when:
        service.updateUserAccess(opus.uuid, [authorities: [[userId: "user2", name: "Fred", role: "role_profile_admin", notes: "first note"],
                                                      [userId: "user3", name: "Bob", role: "role_profile_reviewer", notes: "second note"]]])
        opus = Opus.findByUuid(opus.uuid)

        then:
        Authority.count() == 2
        Contributor.count() == 3
        opus.authorities.size() == 2
        opus.authorities.find({it.user.userId == "user2"}).role == Role.ROLE_PROFILE_ADMIN
        opus.authorities.find({it.user.userId == "user3"}).role == Role.ROLE_PROFILE_REVIEWER
    }

    def "updateUserAccess should fail if the name is not provided for a new user"() {
        given:
        Opus opus = new Opus(title: "opus", dataResourceUid: "123", glossary: new Glossary())
        save opus

        when:
        service.updateUserAccess(opus.uuid, [authorities: [[userId: "user1", role: "role_profile_admin", notes: "first note"]]])

        then:
        thrown IllegalArgumentException
    }

    def "updateUserAccess should fail if the userId is not provided for a new user"() {
        given:
        Opus opus = new Opus(title: "opus", dataResourceUid: "123", glossary: new Glossary())
        save opus

        when:
        service.updateUserAccess(opus.uuid, [authorities: [[name: "fred", role: "role_profile_admin", notes: "first note"]]])

        then:
        thrown IllegalArgumentException
    }

    def "updateUserAccess should fail if the role is not recognised"() {
        given:
        Opus opus = new Opus(title: "opus", dataResourceUid: "123", glossary: new Glossary())
        save opus

        when:
        service.updateUserAccess(opus.uuid, [authorities: [[userId: "user1", name: "fred", role: "unknown", notes: "first note"]]])

        then:
        thrown IllegalArgumentException
    }
    
    def "updateUserAccess should revoke all data sharing requests when the collection becomes private"() {
        given:
        Opus opus1 = new Opus(title: "opus", dataResourceUid: "123", glossary: new Glossary())
        save opus1
        Opus opus2 = new Opus(title: "opus", dataResourceUid: "123", glossary: new Glossary(), sharingDataWith: [new SupportingOpus(uuid: opus1.uuid, requestStatus: ShareRequestStatus.ACCEPTED)])
        save opus2

        opus1.supportingOpuses = [new SupportingOpus(uuid: opus2.uuid, requestStatus: ShareRequestStatus.ACCEPTED)]
        save opus1

        expect:
        opus2.sharingDataWith.size() == 1
        opus2.sharingDataWith.each {
            assert it.dateRejected == null
            assert it.requestStatus == ShareRequestStatus.ACCEPTED
        }

        when:
        service.updateUserAccess(opus2.uuid, [privateCollection: true])
        opus2 = Opus.findByUuid(opus2.uuid)
        opus1 = Opus.findByUuid(opus1.uuid)

        then:
        opus2.sharingDataWith.size() == 0
        opus1.supportingOpuses.size() == 1
        opus1.supportingOpuses.each {
            assert it.requestStatus == ShareRequestStatus.REVOKED
        }
    }

    def "updateUserAccess should not revoke all data sharing requests when the collection has not been changed to private"() {
        given:
        Opus opus1 = new Opus(title: "opus", dataResourceUid: "123", glossary: new Glossary())
        save opus1
        Opus opus2 = new Opus(title: "opus", dataResourceUid: "123", glossary: new Glossary(), sharingDataWith: [new SupportingOpus(uuid: opus1.uuid, requestStatus: ShareRequestStatus.ACCEPTED)])
        save opus2

        opus1.supportingOpuses = [new SupportingOpus(uuid: opus2.uuid, requestStatus: ShareRequestStatus.ACCEPTED)]
        save opus1

        expect:
        opus2.sharingDataWith.size() == 1
        opus2.sharingDataWith.each {
            assert it.requestStatus == ShareRequestStatus.ACCEPTED
        }

        when:
        service.updateUserAccess(opus2.uuid, [privateCollection: false])

        then:
        opus2.sharingDataWith.size() == 1
        opus1.supportingOpuses.size() == 1
        opus1.supportingOpuses.each {
            assert it.requestStatus == ShareRequestStatus.ACCEPTED
        }
    }

    def "Opus HTML properties should be sanitized on save"() {
        given:
        Opus opus1 = new Opus(title: "opus", dataResourceUid: "123", glossary: new Glossary(), citationHtml: '<p><script>alert</script>hi</p>', aboutHtml: '<p><script>alert</script>hi</p>')

        when:
        save opus1

        then:
        opus1.aboutHtml == '<p>hi</p>'
        opus1.citationHtml == '<p>hi</p>'
    }

    def "deleteAttachment should remove the attachment entity and the file when there is a filename"() {
        given:
        Opus opus1 = new Opus(title: "opus1", dataResourceUid: "123", glossary: new Glossary())
        opus1.attachments = [new Attachment(uuid: "1234", filename: "file1")]
        save opus1

        when:
        service.deleteAttachment(opus1.uuid, "1234")
        opus1 = Opus.findByUuid(opus1.uuid)

        then:
        opus1.attachments.isEmpty()
        1 * service.attachmentService.deleteAttachment(opus1.uuid, null, "1234", _)
    }

    def "deleteAttachment should not attempt to remove a file when there is no filename"() {
        given:
        Opus opus1 = new Opus(title: "opus1", dataResourceUid: "123", glossary: new Glossary())
        opus1.attachments = [new Attachment(uuid: "1234", url: "url")]
        save opus1

        when:
        service.deleteAttachment(opus1.uuid, "1234")
        opus1 = Opus.findByUuid(opus1.uuid)

        then:
        opus1.attachments.isEmpty()
        0 * service.attachmentService.deleteAttachment(opus1.uuid, null, "1234", _)
    }

    def "deleteAttachment should do nothing if there are no attachments"() {
        given:
        Opus opus1 = new Opus(title: "opus1", dataResourceUid: "123", glossary: new Glossary())
        save opus1

        when:
        service.deleteAttachment(opus1.uuid, "1234")

        then:
        !opus1.attachments
        0 * service.attachmentService.deleteAttachment(_, _, _, _)
    }

    def "saveAttachment should update an existing attachment if there is uuid"() {
        given:
        Opus opus1 = new Opus(title: "opus1", dataResourceUid: "123", glossary: new Glossary())
        opus1.attachments = [new Attachment(uuid: "1234", title: "oldTitle", description: "oldDesc")]
        save opus1

        when:
        service.saveAttachment(opus1.uuid, [uuid: "1234", title: "newTitle"], null)
        opus1 = Opus.findByUuid(opus1.uuid)

        then:
        opus1.attachments.size() == 1
        opus1.attachments[0].title == "newTitle"
        0 * service.attachmentService.saveAttachment(_, _, _, _, _)
        0 * service.attachmentService.deleteAttachment(_, _, _, _)
    }

    def "saveAttachment should create a new attachment if there is no uuid"() {
        given:
        Opus opus1 = new Opus(title: "opus1", dataResourceUid: "123", glossary: new Glossary())
        opus1.attachments = [new Attachment(uuid: "1234", title: "oldTitle", description: "oldDesc")]
        save opus1

        when:
        service.saveAttachment(opus1.uuid, [title: "newTitle"], Mock(MultipartFile))
        opus1 = Opus.findByUuid(opus1.uuid)

        then:
        opus1.attachments.size() == 2
        opus1.attachments[0].title == "oldTitle"
        opus1.attachments[1].title == "newTitle"
        1 * service.attachmentService.saveAttachment(_, _, _, _, _)
        0 * service.attachmentService.deleteAttachment(_, _, _, _)
    }

    def "update opus for approved lists"() {
        given:
        Opus opus1 = new Opus(title: "opus1", dataResourceUid: "123", glossary: new Glossary(), approvedLists: ["dr123", "dr345"])

        def opus2 = [
                title         : "opus1",
                dataResourceUid      : "123",
                approvedLists        : ["dr123"],
        ]


        JSONObject json1 = new JSONObject(opus2)
        save opus1

        when:
        Opus updateOpus = service.updateOpus(opus1.uuid, json1)

        then:
        updateOpus.approvedLists.size() == json1.approvedLists.size()
    }

    def "update opus for featured lists"() {
        given:
        Opus opus1 = new Opus(title: "opus1", dataResourceUid: "123", glossary: new Glossary(), featureLists: ["dr123", "dr345", "dr678"])

        def opus2 = [
                title         : "opus1",
                dataResourceUid      : "123",
                featureLists        : ["dr345"],
        ]


        JSONObject json1 = new JSONObject(opus2)
        save opus1

        when:
        Opus updateOpus = service.updateOpus(opus1.uuid, json1)

        then:
        updateOpus.featureLists.size() == json1.featureLists.size()
    }

    def "update opus for approved lists and feature lists"() {
        given:
        Opus opus1 = new Opus(title: "opus1", dataResourceUid: "123", glossary: new Glossary(), approvedLists: ["dr123", "dr345"], featureLists: ["fl123","fl456","fl789"])

        def opus2 = [
                title         : "opus1",
                dataResourceUid      : "123",
                approvedLists        : ["dr123"],
                featureLists         : ["fl123", "fl456"]
         ]


        JSONObject json1 = new JSONObject(opus2)
        save opus1

        when:
        Opus updateOpus = service.updateOpus(opus1.uuid, json1)

        then:
        updateOpus.approvedLists.size() == json1.approvedLists.size()
        updateOpus.featureLists.size() == json1.featureLists.size()
    }

    def "update opus for approved lists and feature lists are empty"() {
        given:
        Opus opus1 = new Opus(title: "opus1", dataResourceUid: "123", glossary: new Glossary(), approvedLists: [], featureLists: [])

        def opus2 = [
                title         : "opus1",
                dataResourceUid      : "123",
                approvedLists        : [],
                featureLists         : []
        ]


        JSONObject json1 = new JSONObject(opus2)
        save opus1

        when:
        Opus updateOpus = service.updateOpus(opus1.uuid, json1)

        then:
        updateOpus.approvedLists.size() == json1.approvedLists.size()
        updateOpus.featureLists.size() == json1.featureLists.size()
    }
}