package au.org.ala.profile

import au.org.ala.profile.util.DataResourceOption
import grails.testing.web.controllers.ControllerUnitTest
import spock.lang.Specification

import static au.org.ala.profile.util.ImageOption.EXCLUDE

class BaseControllerSpec extends Specification implements ControllerUnitTest<BaseController>{
    Profile profile
    BaseController controller
    def setup() {
        controller = new BaseController()
        profile = new Profile(
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
                attributes: [],
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
                        attributes: [],
                        attachments: [],
                        dateCreated: new Date(),
                        isCustomMapConfig: false,
                        occurrenceQuery: ""
                ),
                opus: new Opus(
                        dataResourceConfig: new DataResourceConfig(recordResourceOption: DataResourceOption.ALL)
                )
        )
    }

    def cleanup() {
    }

    void "createOccurrenceQuery constructs map query from profile"() {
        when:
        def query = controller.createOccurrenceQuery(profile, false)

        then:
        assert query == "q=lsid%3Aguid"
    }

    void "createOccurrenceQuery constructs map query from profile draft"() {
        when:
        def query = controller.createOccurrenceQuery(profile, true)

        then:
        assert query == "q=lsid%3Adraftguid"

    }
}
