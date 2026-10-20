package au.org.ala.profile

import au.org.ala.profile.util.DataResourceOption
import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import grails.util.GrailsWebMockUtil
import org.grails.plugins.testing.GrailsMockHttpServletRequest
import org.grails.plugins.testing.GrailsMockHttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.context.WebApplicationContext
import static au.org.ala.profile.util.ImageOption.EXCLUDE

@Integration
@Rollback
class ProfileControllerSpec extends BaseIntegrationSpec {

    @Autowired
    ProfileController controller

    @Autowired
    WebApplicationContext ctx

    ProfileService profileService

    def setup() {
        GrailsMockHttpServletRequest grailsMockHttpServletRequest = new GrailsMockHttpServletRequest()
        GrailsMockHttpServletResponse grailsMockHttpServletResponse = new GrailsMockHttpServletResponse()
        GrailsWebMockUtil.bindMockWebRequest(ctx, grailsMockHttpServletRequest, grailsMockHttpServletResponse)

        profileService = Mock(ProfileService)
        controller.profileService = profileService
        controller.attachmentService = Mock(AttachmentService)
    }

    def "updateProfile should return the draft profile if it exists and the 'latest' query param is 'true'"() {
        given:
        Opus opus = save new Opus(uuid: "opusId", shortName: "opusid", title: "opusName", glossary: new Glossary(), dataResourceUid: "dr1")
        Profile profile = save new Profile(scientificName: "sciName", opus: opus, draft: [uuid: "123", scientificName: "draftSciName"])

        when: "the latest param is 'true'"
        controller.request.json = "{'a': 'b'}"
        controller.params.profileId = profile.uuid
        controller.params.opusId = "opusId"
        controller.params.latest = "true"
        controller.updateProfile()

        then: "return the draft version"
        controller.response.status == 200
        controller.response.json.uuid == "123"
        controller.response.json.scientificName == "draftSciName"
    }

    def "updateProfile should return the public version of the profile if a draft exists but the 'latest' query param is not 'true'"() {
        given:
        Opus opus = save new Opus(uuid: "opusId", shortName: "opusid", title: "opusName", glossary: new Glossary(), dataResourceUid: "dr1")
        Profile profile = save new Profile(scientificName: "sciName", opus: opus, draft: [uuid: "123", scientificName: "draftSciName"])

        when: "the latest param is 'true'"
        controller.request.json = "{'a': 'b'}"
        controller.params.profileId = profile.uuid
        controller.params.opusId = "opusId"
        controller.updateProfile()

        then: "return the draft version"
        controller.response.status == 200
        controller.response.json.uuid == profile.uuid
        controller.response.json.scientificName == "sciName"
    }

    def "updateProfile should return the public profile if no draft exists, even if the 'latest' query param is 'true'"() {
        given:
        Opus opus = save new Opus(uuid: "opusId", shortName: "opusid", title: "opusName", glossary: new Glossary(), dataResourceUid: "dr1")
        Profile profile = save new Profile(scientificName: "sciName", opus: opus)

        when: "the latest param is 'true'"
        controller.request.json = "{'a': 'b'}"
        controller.params.profileId = profile.uuid
        controller.params.opusId = "opusId"
        controller.params.latest = "true"
        controller.updateProfile()

        then: "return the draft version"
        controller.response.status == 200
        controller.response.json.uuid == profile.uuid
        controller.response.json.scientificName == "sciName"
    }

    def "getByUuid should return the draft profile if it exists and the 'latest' query param is 'true'"() {
        given:
        Opus opus = save new Opus(uuid: "opusId", shortName: "opusid", title: "opusName", glossary: new Glossary(), dataResourceUid: "dr1")
        Profile profile = save new Profile(scientificName: "sciName", opus: opus, draft: [uuid: "123", scientificName: "draftSciName"])

        when: "the latest param is 'true'"
        controller.request.json = "{'a': 'b'}"
        controller.params.profileId = profile.uuid
        controller.params.opusId = "opusId"
        controller.params.latest = "true"
        controller.getByUuid()

        then: "return the draft version"
        controller.response.status == 200
        controller.response.json.uuid == "123"
        controller.response.json.scientificName == "draftSciName"
    }

    def "getByUuid should return the public version of the profile if a draft exists but the 'latest' query param is not 'true'"() {
        given:
        Opus opus = save new Opus(uuid: "opusId", shortName: "opusid", title: "opusName", glossary: new Glossary(), dataResourceUid: "dr1")
        Profile profile = save new Profile(scientificName: "sciName", opus: opus, draft: [uuid: "123", scientificName: "draftSciName"])

        when: "the latest param is 'true'"
        controller.request.json = "{'a': 'b'}"
        controller.params.profileId = profile.uuid
        controller.params.opusId = "opusId"
        controller.getByUuid()

        then: "return the draft version"
        controller.response.status == 200
        controller.response.json.uuid == profile.uuid
        controller.response.json.scientificName == "sciName"
    }

    def "getByUuid should return the public profile if no draft exists, even if the 'latest' query param is 'true'"() {
        given:
        Opus opus = save new Opus(uuid: "opusId", shortName: "opusid", title: "opusName", glossary: new Glossary(), dataResourceUid: "dr1")
        Profile profile = save new Profile(scientificName: "sciName", opus: opus)

        when: "the latest param is 'true'"
        controller.request.json = "{'a': 'b'}"
        controller.params.profileId = profile.uuid
        controller.params.opusId = "opusId"
        controller.params.latest = "true"
        controller.getByUuid()

        then: "return the draft version"
        controller.response.status == 200
        controller.response.json.uuid == profile.uuid
        controller.response.json.scientificName == "sciName"
    }

    def "downloadAttachment should use the draft if there is one and latest = true"() {
        Opus opus = save new Opus(uuid: "opusId", shortName: "opusid", title: "opusName", glossary: new Glossary(), dataResourceUid: "dr1")
        Profile profile = save new Profile(scientificName: "sciName", opus: opus)
        profile.draft = new DraftProfile(uuid: profile.uuid, scientificName: profile.scientificName, attachments: [new Attachment(uuid: "1234")])
        save profile

        when:
        controller.params.latest = "true"
        controller.params.profileId = profile.uuid
        controller.params.opusId = "opusId"
        controller.params.attachmentId = "1234"
        controller.downloadAttachment()

        then:
        1 * controller.attachmentService.getAttachment("opusId", profile.uuid, "1234", _)
    }

    def "downloadAttachment should use the profile when latest = false even if there is a draft"() {
        Opus opus = save new Opus(uuid: "opusId", shortName: "opusid", title: "opusName", glossary: new Glossary(), dataResourceUid: "dr1")
        Profile profile = save new Profile(scientificName: "sciName", opus: opus, attachments: [new Attachment(uuid: "1234")])
        profile.draft = new DraftProfile(uuid: profile.uuid, scientificName: profile.scientificName, attachments: [])

        when:
        controller.params.latest = "false"
        controller.params.profileId = profile.uuid
        controller.params.opusId = "opusId"
        controller.params.attachmentId = "1234"
        controller.downloadAttachment()

        then:
        1 * controller.attachmentService.getAttachment("opusId", profile.uuid, "1234", _)
    }

    def "getProfiles should get all profiles in an opus"() {
        Opus opus = save new Opus(uuid: "opusId", shortName: "opusid", title: "opusName", glossary: new Glossary(), dataResourceUid: "dr1")
        Profile profile = save new Profile(scientificName: "sciName", opus: opus, rank: "subspecies", attachments: [new Attachment(uuid: "1234")])

        when:
        controller.params.opusId = "opusId"
        controller.params.pageSize = "10"
        controller.params.startIndex = "0"
        controller.params.sort = "scientificName"
        controller.params.order = "desc"
        controller.params.rankFilter = "species"
        controller.getProfiles()

        then:
        1 * controller.profileService.getProfiles(_, 10, 0, "scientificName", "desc", "species") >> [count: 1, profiles: [scientificName: "sciName", rank: "subspecies"]]
        controller.response.status == 200
        controller.response.json.count == 1
    }

    def "profile attribute should have constraintListExpanded if constraintList is present"() {
        def profile
        setup:
        def opus = save new Opus(
                uuid: "opus1",
                shortName: 'opus-short',
                dataResourceConfig: new DataResourceConfig(recordResourceOption: DataResourceOption.ALL),
                glossary: new Glossary(),
                title: 'opus1',
                dataResourceUid: 'dataResourceUid1'
        )
        def vocab = save new Vocab(uuid: 'vocab1', name: 'vocab1')
        def term1 = save new Term(uuid: 'term1', name: 'title1', vocab: vocab, dataType: 'list')
        def range = save new Term(uuid: 'term2', name: 'numberRange1', vocab: vocab, dataType: 'range')
        def number = save new Term(uuid: 'term1', name: 'number1', vocab: vocab, dataType: 'number')

        save new Term(uuid: 'constraint1', name: 'term1', vocab: vocab)
        save new Term(uuid:  'constraint2', name: 'term2', vocab: vocab)
        save new Profile(
                uuid: "uuid",
                scientificName: "sciName",
                nameAuthor: "nameAuthor",
                guid: "guid",
                rank: "rank",
                taxonomyTree: "taxonomyTree",
                nslNameIdentifier: "nslId",
                primaryImage: "primaryImage",
                showLinkedOpusAttributes: true,
                profileStatus: Profile.STATUS_PARTIAL,
                imageSettings: [image1: new ImageSettings(imageDisplayOption: EXCLUDE), image2: new ImageSettings(imageDisplayOption: EXCLUDE)],
                specimenIds: ["spec1", "spec2"],
                authorship: [],
                classification: [new Classification(rank: "kingdom", name: "Plantae"), new Classification(rank: "family", name: "Acacia")],
                links: [new Link(title: "link1"), new Link(title: "link2")],
                bhlLinks: [new Link(title: "bhl1"), new Link(title: "bhl2")],
                bibliography: [new Bibliography(text: "bib1"), new Bibliography(text: "bib2")],
                publications: [new Publication(title: "pub1"), new Publication(title: "pub2")],
                attributes: [
                        new Attribute(uuid: 'attr1', title: term1, constraintList: ['constraint1', 'constraint2']),
                        new Attribute(uuid: 'attr2', title: range, numberRange: new NumberRange(from: 1, to: 2)),
                        new Attribute(uuid: 'attr3', title: number, numbers: [3,4,5])
                ],
                attachments: [new Attachment(title: "doc1"), new Attachment(title: "doc2")],
                dateCreated: new Date(),
                isCustomMapConfig: false,
                occurrenceQuery: "",
                draft: new DraftProfile(
                        uuid: "uuid",
                        scientificName: "sciName",
                        nameAuthor: "nameAuthor",
                        guid: "draftguid",
                        rank: "rank",
                        taxonomyTree: "taxonomyTree2",
                        nslNameIdentifier: "nslId2",
                        primaryImage: "primaryImage2",
                        showLinkedOpusAttributes: false,
                        profileStatus: Profile.STATUS_LEGACY,
                        imageSettings: [],
                        specimenIds: ["spec3", "spec4"],
                        authorship: [],
                        classification: [new Classification(rank: "kingdom", name: "Plantae"), new Classification(rank: "family", name: "Acacia")],
                        links: [],
                        bhlLinks: [],
                        bibliography: [],
                        attributes: [new Attribute(uuid: 'attr2', title: term1, constraintList: ['constraint1', 'constraint2'])],
                        attachments: [],
                        dateCreated: new Date(),
                        isCustomMapConfig: false,
                        occurrenceQuery: ""
                ),
                opus: opus
        )

        when:
        controller.params.opusId = 'opus-short'
        controller.params.profileId = 'sciName'
        profile = controller.getProfile()

        then:
        def constraintListAttribute = profile.attributes.find { it.title.dataType == 'list'}
        constraintListAttribute.constraintListExpanded.size() == 2
        constraintListAttribute.constraintListExpanded.collect { it.uuid } == ['constraint1', 'constraint2']
        constraintListAttribute.constraintListExpanded.collect { it.uuid } == ['constraint1', 'constraint2']
        def rangeAttribute = profile.attributes.find { it.title.dataType == 'range'}
        rangeAttribute.numberRange.from == 1
        rangeAttribute.numberRange.to == 2
        def numberAttribute = profile.attributes.find { it.title.dataType == 'number'}
        numberAttribute.numbers == [3,4,5]
    }
}
