package au.org.ala.profile

import au.org.ala.profile.api.ExportController
import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import grails.util.GrailsWebMockUtil
import org.apache.http.HttpStatus
import org.grails.plugins.testing.GrailsMockHttpServletRequest
import org.grails.plugins.testing.GrailsMockHttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.context.WebApplicationContext

@Integration
@Rollback
class ExportControllerSpec extends BaseIntegrationSpec {

    @Autowired
    ExportController controller

    @Autowired
    WebApplicationContext ctx

    def setup() {
        GrailsMockHttpServletRequest grailsMockHttpServletRequest = new GrailsMockHttpServletRequest()
        GrailsMockHttpServletResponse grailsMockHttpServletResponse = new GrailsMockHttpServletResponse()
        GrailsWebMockUtil.bindMockWebRequest(ctx, grailsMockHttpServletRequest, grailsMockHttpServletResponse)
    }

    def "exportCollection should only export profiles from the specified collection"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")
        Opus opus2 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")

        save new Profile(scientificName: "a", opus: opus1)
        save new Profile(scientificName: "b", opus: opus2)

        when: "asking for 2 records with an offset of 1"
        controller.params.opusId = opus2.uuid
        controller.params.includeArchived = true
        controller.exportCollection()

        then: "the results should be [b, c] (i.e. [profile2, profile3]"
        controller.response.status == HttpStatus.SC_OK
        controller.response.json.total == 1
        controller.response.json.profiles.size() == 1
        controller.response.json.profiles[0].name == "b"
    }

    def "exportCollection should not include archived profiles by default"() {
        given:
        Opus opus = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")

        save new Profile(scientificName: "a", opus: opus, archivedDate: new Date())
        save new Profile(scientificName: "b", opus: opus)

        when: "asking for 2 records with an offset of 1"
        controller.params.opusId = opus.uuid
        controller.exportCollection()

        then: "the results should be [b, c] (i.e. [profile2, profile3]"
        controller.response.status == HttpStatus.SC_OK
        controller.response.json.total == 1
        controller.response.json.profiles.size() == 1
        controller.response.json.profiles[0].name == "b"
    }

    def "exportCollection should include archived profiles when includeArchived = true"() {
        given:
        Opus opus = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")

        save new Profile(scientificName: "a", opus: opus, archivedDate: new Date())
        save new Profile(scientificName: "b", opus: opus)

        when: "asking for 2 records with an offset of 1"
        controller.params.opusId = opus.uuid
        controller.params.includeArchived = true
        controller.exportCollection()

        then: "the results should be [b, c] (i.e. [profile2, profile3]"
        controller.response.status == HttpStatus.SC_OK
        controller.response.json.total == 2
        controller.response.json.profiles.size() == 2
        controller.response.json.profiles[0].name == "a"
        controller.response.json.profiles[1].name == "b"
    }

    def "exportCollection should only retrieve the max number of profiles, starting from the specified offset"() {
        given:
        Opus opus = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")

        save new Profile(scientificName: "a", opus: opus)
        save new Profile(scientificName: "b", opus: opus)
        save new Profile(scientificName: "c", opus: opus)
        save new Profile(scientificName: "d", opus: opus)

        when: "asking for 2 records with an offset of 1"
        controller.params.opusId = opus.uuid
        controller.params.max = 2
        controller.params.offset = 1
        controller.exportCollection()

        then: "the results should be [b, c] (i.e. [profile2, profile3]"
        controller.response.status == HttpStatus.SC_OK
        controller.response.json.total == 4
        controller.response.json.profiles.size() == 2
        controller.response.json.profiles[0].name == "b"
        controller.response.json.profiles[1].name == "c"
    }

    def "getProfiles should retrieve profiles by scientificName OR by guid"() {
        given:
        Opus opus = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")

        save new Profile(scientificName: "a", guid: "g1", opus: opus)
        save new Profile(scientificName: "b", guid: "g2", opus: opus)
        save new Profile(scientificName: "c", guid: "g2", opus: opus)
        save new Profile(scientificName: "d", guid: "g3", opus: opus)

        when: "asked for profiles with name 'a' or guid 'g2'"
        controller.params.opusIds = opus.uuid
        controller.params.profileNames = "a"
        controller.params.guids = "g2"
        controller.getProfiles()

        then: "three matches should be returned, 1 from the name, 2 from the guid"
        controller.response.status == HttpStatus.SC_OK
        controller.response.json.total == 3
        controller.response.json.profiles[0].name == "a"
        controller.response.json.profiles[1].name == "b"
        controller.response.json.profiles[2].name == "c"
    }

    def "getProfiles should handle multiple profileNames"() {
        given:
        Opus opus = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")

        save new Profile(scientificName: "a", guid: "g1", opus: opus)
        save new Profile(scientificName: "b", guid: "g2", opus: opus)
        save new Profile(scientificName: "c", guid: "g2", opus: opus)
        save new Profile(scientificName: "d", guid: "g3", opus: opus)

        when:
        controller.params.opusIds = opus.uuid
        controller.params.profileNames = "a,d"
        controller.getProfiles()

        then:
        controller.response.status == HttpStatus.SC_OK
        controller.response.json.total == 2
        controller.response.json.profiles[0].name == "a"
        controller.response.json.profiles[1].name == "d"
    }

    def "getProfiles should handle multiple guids"() {
        given:
        Opus opus = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")

        save new Profile(scientificName: "a", guid: "g1", opus: opus)
        save new Profile(scientificName: "b", guid: "g2", opus: opus)
        save new Profile(scientificName: "c", guid: "g2", opus: opus)
        save new Profile(scientificName: "d", guid: "g3", opus: opus)

        when:
        controller.params.opusIds = opus.uuid
        controller.params.guids = "g1,g3"
        controller.getProfiles()

        then:
        controller.response.status == HttpStatus.SC_OK
        controller.response.json.total == 2
        controller.response.json.profiles[0].name == "a"
        controller.response.json.profiles[1].name == "d"
    }

    def "getProfiles should limit results to the specified opus, by opusId"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", shortName: "op1", title: "title")
        Opus opus2 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr0987", shortName: "op2", title: "title")

        save new Profile(scientificName: "a", guid: "g1", opus: opus1)
        save new Profile(scientificName: "a", guid: "g2", opus: opus2)

        when:
        controller.params.opusIds = opus1.uuid
        controller.params.profileNames = "a"
        controller.getProfiles()

        then:
        controller.response.status == HttpStatus.SC_OK
        controller.response.json.total == 1
        controller.response.json.profiles[0].collection.id == opus1.uuid
    }

    def "getProfiles should limit results to the specified opus, by shortName"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", shortName: "op1", title: "title")
        Opus opus2 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr0987", shortName: "op2", title: "title")

        save new Profile(scientificName: "a", guid: "g1", opus: opus1)
        save new Profile(scientificName: "a", guid: "g2", opus: opus2)

        when:
        controller.params.opusIds = opus2.shortName
        controller.params.profileNames = "a"
        controller.getProfiles()

        then:
        controller.response.status == HttpStatus.SC_OK
        controller.response.json.total == 1
        controller.response.json.profiles[0].collection.id == opus2.uuid
    }

    def "getProfiles should limit results to the specified opus, by dataResourceUid"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", shortName: "op1", title: "title")
        Opus opus2 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr0987", shortName: "op2", title: "title")

        save new Profile(scientificName: "a", guid: "g1", opus: opus1)
        save new Profile(scientificName: "a", guid: "g2", opus: opus2)

        when:
        controller.params.opusIds = opus2.dataResourceUid
        controller.params.profileNames = "a"
        controller.getProfiles()

        then:
        controller.response.status == HttpStatus.SC_OK
        controller.response.json.total == 1
        controller.response.json.profiles[0].collection.id == opus2.uuid
    }

    def "getProfiles should limit results to the specified opus, by tag"() {
        given:
        Tag tag1 = save new Tag(uuid: "tag1", abbrev: "TAG1", name: "Tag 1", colour: "white")
        Tag tag2 = save new Tag(uuid: "tag2", abbrev: "TAG2", name: "Tag 2", colour: "white")
        Tag tag3 = save new Tag(uuid: "tag3", abbrev: "TAG3", name: "Tag 3", colour: "white")

        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", shortName: "op1", title: "title1", tags: [tag1])
        Opus opus2 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr0987", shortName: "op2", title: "title2", tags: [tag3])
        Opus opus3 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr666", shortName: "op3", title: "title3", tags: [tag3, tag2])

        save new Profile(scientificName: "a", guid: "g1", opus: opus1)
        save new Profile(scientificName: "a", guid: "g1", opus: opus2)
        save new Profile(scientificName: "a", guid: "g1", opus: opus3)

        when:
        controller.params.tags = "tag1,tag2"
        controller.params.profileNames = "a"
        controller.getProfiles()

        then:
        controller.response.status == HttpStatus.SC_OK
        controller.response.json.total == 2
        controller.response.json.profiles[0].collection.id == opus1.uuid
        controller.response.json.profiles[1].collection.id == opus3.uuid
    }

    def "getProfiles should fetch results from all public collection when no opusIds or tags are provided"() {
        given:
        Tag tag1 = save new Tag(uuid: "tag1", abbrev: "TAG1", name: "Tag 1", colour: "white")
        Tag tag2 = save new Tag(uuid: "tag2", abbrev: "TAG2", name: "Tag 2", colour: "white")
        Tag tag3 = save new Tag(uuid: "tag3", abbrev: "TAG3", name: "Tag 3", colour: "white")

        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", shortName: "op1", title: "title1", tags: [tag1])
        Opus opus2 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr0987", shortName: "op2", title: "title2", tags: [tag3])
        Opus opus3 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr666", shortName: "op3", title: "title3", tags: [tag3, tag2])
        Opus opus4 = save new Opus(privateCollection: true, glossary: new Glossary(), dataResourceUid: "dr666", shortName: "op3", title: "title3", tags: [tag3, tag2])

        save new Profile(scientificName: "a", guid: "g1", opus: opus1)
        save new Profile(scientificName: "a", guid: "g1", opus: opus2)
        save new Profile(scientificName: "a", guid: "g1", opus: opus3)
        save new Profile(scientificName: "a", guid: "g1", opus: opus4)

        when:
        controller.params.profileNames = "a"
        controller.getProfiles()

        then:
        controller.response.status == HttpStatus.SC_OK
        controller.response.json.total == 3
        controller.response.json.profiles[0].collection.id == opus1.uuid
        controller.response.json.profiles[1].collection.id == opus2.uuid
        controller.response.json.profiles[2].collection.id == opus3.uuid
    }

    def "getProfiles should return an empty response if the specified collection is private"() {
        given:
        Opus opus1 = save new Opus(privateCollection: true, glossary: new Glossary(), dataResourceUid: "dr1234", shortName: "op1", title: "title1")

        save new Profile(scientificName: "a", guid: "g1", opus: opus1)

        when:
        controller.params.profileNames = "a"
        controller.getProfiles()

        then:
        controller.response.text.isEmpty()
    }
}
