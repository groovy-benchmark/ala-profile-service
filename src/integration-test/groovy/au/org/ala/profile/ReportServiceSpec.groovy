package au.org.ala.profile

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration

@Integration
@Rollback
class ReportServiceSpec extends BaseIntegrationSpec {

    ReportService service = new ReportService()

    def "setup"() {
    }

    def "a recent comments report should show latest comments in date range for each profile"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")

        Profile profile1 = save new Profile(scientificName: "name1", fullName: "name1", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])
        Profile profile2 = save new Profile(scientificName: "name2", fullName: "name2", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])

        Contributor contributor = save new Contributor(name: 'Agent Smith')

        Comment comment1 = save new Comment(text: "hello1", profileUuid: profile1.uuid, author: contributor, parent: null)
        Comment comment2 = save new Comment(text: "hello2", profileUuid: profile2.uuid, author: contributor, parent: null)
        Comment comment3 = save new Comment(text: "hello3", profileUuid: profile1.uuid, author: contributor, parent: null)
        Comment comment4 = save new Comment(text: "hello4", profileUuid: profile2.uuid, author: contributor, parent: null)
        Comment comment5 = save new Comment(text: "hello5", profileUuid: profile1.uuid, author: contributor, parent: null)
        Comment comment6 = save new Comment(text: "hello6", profileUuid: profile2.uuid, author: contributor, parent: null)
        Comment comment7 = save new Comment(text: "hello7", profileUuid: profile1.uuid, author: contributor, parent: null)
        Comment comment8 = save new Comment(text: "hello8", profileUuid: profile2.uuid, author: contributor, parent: null)

        when:
        Map result = service.recentComments(opus1, comment3.dateCreated, comment6.dateCreated, -1, 0, false)

        then:
        result.recordCount == 2
        result.records.size() == 2
        result.records*.comment.containsAll([comment6, comment5]*.text) == true
    }

    def "a recent comments report should exclude comments from other Opuses"() {
        given:

        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")
        Opus opus2 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")

        Profile profile1 = save new Profile(scientificName: "name1", fullName: "name1", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])
        Profile profile2 = save new Profile(scientificName: "name2", fullName: "name2", opus: opus2, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])

        Contributor contributor = save new Contributor(name: 'Agent Smith')

        Comment comment1 = save new Comment(text: "hello1", profileUuid: profile1.uuid, author: contributor, parent: null)
        Comment comment2 = save new Comment(text: "hello2", profileUuid: profile2.uuid, author: contributor, parent: null)

        when:
        Map result = service.recentComments(opus1, comment1.dateCreated, comment2.dateCreated, -1, 0, false)

        then:
        result.recordCount == 1
        result.records.size() == 1
        result.records*.comment.containsAll([comment1]*.text) == true
    }

    def "a count only recent comments report should only return the count"() {
        given:

        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")

        Profile profile1 = save new Profile(scientificName: "name1", fullName: "name1", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])
        Profile profile2 = save new Profile(scientificName: "name1", fullName: "name1", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])
        Profile profile3 = save new Profile(scientificName: "name1", fullName: "name1", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])

        Contributor contributor = save new Contributor(name: 'Agent Smith')

        Comment comment1 = save new Comment(text: "hello1", profileUuid: profile1.uuid, author: contributor, parent: null)
        Comment comment2 = save new Comment(text: "hello2", profileUuid: profile2.uuid, author: contributor, parent: null)
        Comment comment3 = save new Comment(text: "hello3", profileUuid: profile3.uuid, author: contributor, parent: null)

        when:
        Map result = service.recentComments(opus1, comment1.dateCreated, comment3.dateCreated, -1, 0, true)

        then:
        result.recordCount == 3
        result.records == null
    }

    def "a paged recent comments report should report the correct total but only the page size in results"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")

        Profile profile1 = save new Profile(scientificName: "name1", fullName: "name1", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])
        Profile profile2 = save new Profile(scientificName: "name2", fullName: "name2", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])
        Profile profile3 = save new Profile(scientificName: "name3", fullName: "name3", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])
        Profile profile4 = save new Profile(scientificName: "name4", fullName: "name4", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])
        Profile profile5 = save new Profile(scientificName: "name5", fullName: "name5", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])


        Contributor contributor = save new Contributor(name: 'Agent Smith')

        Comment comment1 = save new Comment(text: "hello1", profileUuid: profile1.uuid, author: contributor, parent: null)
        Comment comment2 = save new Comment(text: "hello2", profileUuid: profile2.uuid, author: contributor, parent: null)
        Comment comment3 = save new Comment(text: "hello3", profileUuid: profile3.uuid, author: contributor, parent: null)
        Comment comment4 = save new Comment(text: "hello4", profileUuid: profile4.uuid, author: contributor, parent: null)
        Comment comment5 = save new Comment(text: "hello5", profileUuid: profile5.uuid, author: contributor, parent: null)

        when:
        Map result = service.recentComments(opus1, comment1.dateCreated, comment5.dateCreated, 2, 0, false)

        then:
        result.recordCount == 5
        result.records.size() == 2
        result.records*.comment.containsAll([comment5, comment4]*.text) == true

        when: "the second page is requested"
        result = service.recentComments(opus1, comment1.dateCreated, comment5.dateCreated, 2, 2, false)

        then:
        result.recordCount == 5
        result.records.size() == 2
        result.records*.comment.containsAll([comment3, comment2]*.text) == true

        when: "the third page is requested"
        result = service.recentComments(opus1, comment1.dateCreated, comment5.dateCreated, 2, 4, false)

        then:
        result.recordCount == 5
        result.records.size() == 1
        result.records*.comment.containsAll([comment1]*.text) == true
    }
}
