package au.org.ala.profile

import au.org.ala.profile.util.ProfileSortOption
import au.org.ala.profile.util.SearchOptions
import grails.gorm.transactions.Rollback
import grails.plugins.elasticsearch.ElasticSearchService
import grails.testing.mixin.integration.Integration
import org.grails.datastore.mapping.core.Datastore
import org.springframework.beans.factory.annotation.Autowired

@Integration
@Rollback
class SearchServiceSpec extends BaseIntegrationSpec {
    @Autowired
    SearchService service
    @Autowired
    Datastore datastore
    @Autowired
    ElasticSearchService elasticSearchService

    Closure doWithSpring () {
        System.println("Test")
        { ctx ->
           ctx
            System.println("Test")
        }
    }


    def "setup"() {
        service.userService = Mock(UserService)
        service.mongoClient = datastore.getMongoClient()
        def mls = Stub(MasterListService)
        def masterLists = [
                'test': [
                        ['name': 'name1', 'scientificName': 'name1'],
                        ['name': 'name2', 'scientificName': 'name2']
                ]
        ]
        mls.getCombinedLowerCaseNamesListForUser(_) >> { Opus opus ->
            def ml = masterLists[opus.masterListUid]
            return ml == null ? null : ml.collect { it.name.toLowerCase() }
        }
        mls.getCombinedNamesListForUser(_) >> { Opus opus ->
            def ml = masterLists[opus.masterListUid]
            return ml == null ? null : ml*.name
        }
        mls.getCombinedListForUser(_) >> { Opus opus ->
            masterLists[opus.masterListUid]
        }
        mls.getMasterList(_) >> { Opus opus ->
            masterLists[opus.masterListUid]
        }
        service.masterListService = mls
    }

    def "findByScientificName should fail when no scientific name is provided"() {
        when:
        service.findByScientificName(null, ["opus"])

        then:
        thrown IllegalArgumentException
    }

    def "findByScientificName should match any opus when no opusId list is provided"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")
        Opus opus2 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr2", title: "title2")
        Opus opus3 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr3", title: "title3")

        Profile profile1 = save new Profile(scientificName: "name1", fullName: "name1", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])
        Profile profile2 = save new Profile(scientificName: "name2", fullName: "name2", opus: opus2, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])
        Profile profile3 = save new Profile(scientificName: "name3", fullName: "name3", opus: opus3, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])

        when:
        List result = service.findByScientificName("name", null)

        then:
        result.size() == 3
        result.find { it.scientificName == profile1.scientificName } != null
        result.find { it.scientificName == profile2.scientificName } != null
        result.find { it.scientificName == profile3.scientificName } != null
    }

    def "findByScientificName should exclude matches from private collections when there is no logged in user"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")
        Opus opus2 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr2", title: "title2", privateCollection: true, authorities: [[uuid: "1", user: [userId: "9876", name: "name 1"], role: "ROLE_USER"]])
        Opus opus3 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr3", title: "title3", privateCollection: true, authorities: [[uuid: "2", user: [userId: "1234", name: "name 2"], role: "ROLE_USER"]])

        Profile profile1 = save new Profile(scientificName: "name1", fullName: "name1", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])
        Profile profile2 = save new Profile(scientificName: "name2", fullName: "name2", opus: opus2, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])
        Profile profile3 = save new Profile(scientificName: "name3", fullName: "name3", opus: opus3, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])

        service.userService.userInRole("ROLE_ADMIN") >> false
        service.userService.getCurrentUserDetails() >> [userId: null]

        when:
        List result = service.findByScientificName("name", null)

        then:
        result.size() == 1
        result.find { it.scientificName == profile1.scientificName } != null
        result.find { it.scientificName == profile2.scientificName } == null
        result.find { it.scientificName == profile3.scientificName } == null
    }

    def "findByScientificName should include matches from private collections when the logged in user is registered with that collection"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")
        Opus opus2 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr2", title: "title2", privateCollection: true, authorities: [[uuid: "1", user: [userId: "9876", name: "name 1"], role: "ROLE_USER"]])
        Opus opus3 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr3", title: "title3", privateCollection: true, authorities: [[uuid: "2", user: [userId: "1234", name: "name 2"], role: "ROLE_USER"]])

        Profile profile1 = save new Profile(scientificName: "name1", fullName: "name1", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])
        Profile profile2 = save new Profile(scientificName: "name2", fullName: "name2", opus: opus2, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])
        Profile profile3 = save new Profile(scientificName: "name3", fullName: "name3", opus: opus3, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])

        service.userService.userInRole("ROLE_ADMIN") >> false
        service.userService.getCurrentUserDetails() >> [userId: "1234"]

        when:
        List result = service.findByScientificName("name", null)

        then:
        result.size() == 2
        result.find { it.scientificName == profile1.scientificName } != null
        result.find { it.scientificName == profile2.scientificName } == null
        result.find { it.scientificName == profile3.scientificName } != null
    }

    def "findByScientificName should include matches from all private collections when the logged in user is an ALA admin"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")
        Opus opus2 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr2", title: "title2", privateCollection: true, authorities: [[uuid: "1", user: [userId: "9876", name: "name1"], role: "ROLE_USER"]])
        Opus opus3 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr3", title: "title3", privateCollection: true, authorities: [[uuid: "2", user: [userId: "1234", name: "name1"], role: "ROLE_USER"]])

        Profile profile1 = save new Profile(scientificName: "name1", fullName: "name1", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])
        Profile profile2 = save new Profile(scientificName: "name2", fullName: "name2", opus: opus2, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])
        Profile profile3 = save new Profile(scientificName: "name3", fullName: "name3", opus: opus3, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])

        service.userService.userInRole("ROLE_ADMIN") >> true
        service.userService.getCurrentUserDetails() >> [userId: "1234"]

        when:
        List result = service.findByScientificName("name", null)

        then:
        result.size() == 3
        result.find { it.scientificName == profile1.scientificName } != null
        result.find { it.scientificName == profile2.scientificName } != null
        result.find { it.scientificName == profile3.scientificName } != null
    }

    def "findByScientificName should match only the specified opus ids"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")
        Opus opus2 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr2", title: "title2")
        Opus opus3 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr3", title: "title3")

        Profile profile1 = save new Profile(scientificName: "name1", fullName: "name1", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])
        Profile profile2 = save new Profile(scientificName: "name2", fullName: "name2", opus: opus2, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])
        Profile profile3 = save new Profile(scientificName: "name3", fullName: "name3", opus: opus3, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])

        when:
        List result = service.findByScientificName("name", [opus1.uuid, opus3.uuid])

        then:
        result.size() == 2
        result.find { it.scientificName == profile1.scientificName } != null
        result.find { it.scientificName == profile2.scientificName } == null
        result.find { it.scientificName == profile3.scientificName } != null
    }

    def "findByScientificName should ignore unknown opus ids"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")
        Opus opus2 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr2", title: "title2")
        Opus opus3 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr3", title: "title3")

        Profile profile1 = save new Profile(scientificName: "name1", fullName: "name1", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])
        Profile profile2 = save new Profile(scientificName: "name2", fullName: "name2", opus: opus2, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])
        Profile profile3 = save new Profile(scientificName: "name3", fullName: "name3", opus: opus3, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])

        when:
        List result = service.findByScientificName("name", [opus1.uuid, "unknown"])

        then:
        result.size() == 1
        result.find { it.scientificName == profile1.scientificName } != null
        result.find { it.scientificName == profile2.scientificName } == null
        result.find { it.scientificName == profile3.scientificName } == null
    }

    def "findByScientificName should default to using a wildcard"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")
        Profile profile1 = save new Profile(scientificName: "name", fullName: "name", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])
        Profile profile2 = save new Profile(scientificName: "name two", fullName: "name two", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])
        Profile profile3 = save new Profile(scientificName: "nameThree", fullName: "nameThree", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])

        when:
        List result = service.findByScientificName("name", [opus1.uuid])

        then:
        result.size() == 3
        result.find { it.scientificName == profile1.scientificName } != null
        result.find { it.scientificName == profile2.scientificName } != null
        result.find { it.scientificName == profile3.scientificName } != null
    }

    def "findByScientificName should perform exact match (case-insensitive) when useWildcard is false"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")
        Profile profile1 = save new Profile(scientificName: "name", fullName: "name full", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])
        Profile profile2 = save new Profile(scientificName: "name two", fullName: "name two full", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])
        Profile profile3 = save new Profile(scientificName: "nameThree", fullName: "nameThree full", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])

        when:
        List result = service.findByScientificName("NAME", [opus1.uuid], ProfileSortOption.getDefault(), false)

        then:
        result.size() == 1
        result.find { it.scientificName == profile1.scientificName } != null
        result.find { it.scientificName == profile2.scientificName } == null
        result.find { it.scientificName == profile3.scientificName } == null
    }

    def "findByScientificName should match the fullname if there is no match on the scientificName"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")
        Profile profile1 = save new Profile(scientificName: "name", fullName: "name full", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])
        Profile profile2 = save new Profile(scientificName: "name two", fullName: "name two full", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])
        Profile profile3 = save new Profile(scientificName: "nameThree", fullName: "nameThree full", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])

        when:
        List result = service.findByScientificName("NAME full", [opus1.uuid], ProfileSortOption.getDefault(), false)

        then:
        result.size() == 1
        result.find { it.scientificName == profile1.scientificName } != null
        result.find { it.scientificName == profile2.scientificName } == null
        result.find { it.scientificName == profile3.scientificName } == null
    }

    def "findByScientificName should limit the results to the specified maximum (names sorted alphabetically"() {
        given:
        Opus opus = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")

        Profile profile1 = save new Profile(scientificName: "ab", fullName: "ab", opus: opus, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])
        Profile profile2 = save new Profile(scientificName: "ac", fullName: "ac", opus: opus, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])
        Profile profile3 = save new Profile(scientificName: "aa", fullName: "aa", opus: opus, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])
        Profile profile4 = save new Profile(scientificName: "ad", fullName: "ad", opus: opus, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])

        when:
        List result = service.findByScientificName("a", [opus.uuid], ProfileSortOption.getDefault(), true, 1)

        then:
        result.size() == 1
        result.find { it.scientificName == profile1.scientificName } == null
        result.find { it.scientificName == profile2.scientificName } == null
        result.find { it.scientificName == profile3.scientificName } != null
        result.find { it.scientificName == profile4.scientificName } == null

        when:
        result = service.findByScientificName("a", [opus.uuid], ProfileSortOption.getDefault(), true, 2)

        then:
        result.size() == 2
        result.find { it.scientificName == profile1.scientificName } != null
        result.find { it.scientificName == profile2.scientificName } == null
        result.find { it.scientificName == profile3.scientificName } != null
        result.find { it.scientificName == profile4.scientificName } == null
    }

    def "findByScientificName should start the result set at the specified offset"() {
        given:
        Opus opus = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")

        Profile profile1 = save new Profile(scientificName: "ab", fullName: "ab", opus: opus, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])
        Profile profile2 = save new Profile(scientificName: "ac", fullName: "ac", opus: opus, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])
        Profile profile3 = save new Profile(scientificName: "aa", fullName: "aa", opus: opus, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])
        Profile profile4 = save new Profile(scientificName: "ad", fullName: "ad", opus: opus, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])

        when:
        List result = service.findByScientificName("a", [opus.uuid], ProfileSortOption.getDefault(), true, 1, 1)

        then:
        result.size() == 1
        result.find { it.scientificName == profile1.scientificName } != null
        result.find { it.scientificName == profile2.scientificName } == null
        result.find { it.scientificName == profile3.scientificName } == null
        result.find { it.scientificName == profile4.scientificName } == null

        when:
        result = service.findByScientificName("a", [opus.uuid], ProfileSortOption.getDefault(), true, 2, 2)

        then:
        result.size() == 2
        result.find { it.scientificName == profile1.scientificName } == null
        result.find { it.scientificName == profile2.scientificName } != null
        result.find { it.scientificName == profile3.scientificName } == null
        result.find { it.scientificName == profile4.scientificName } != null
    }

    def "findByScientificName should exclude archived profiles"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")
        Profile profile1 = save new Profile(scientificName: "name", fullName: "name", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])
        Profile profile2 = save new Profile(scientificName: "name two", fullName: "name two", opus: opus1, archivedDate: new Date(), rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])
        Profile profile3 = save new Profile(scientificName: "nameThree", fullName: "nameThree", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Plantae")])

        when:
        List result = service.findByScientificName("NAME", [opus1.uuid], ProfileSortOption.getDefault(), true)

        then:
        result.size() == 2
        result.find { it.scientificName == profile1.scientificName } != null
        result.find { it.scientificName == profile2.scientificName } == null
        result.find { it.scientificName == profile3.scientificName } != null
    }

    def "findByScientificName should sort the results by taxonomic hierarchy when sortOrder = TAXONOMY"() {
        Opus opus = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")

        save new Profile(scientificName: "Austrobaileya", fullName: "Austrobaileya", opus:
                opus, rank: "genus", classification: [new Classification(rank: "kingdom", name: "plantae"),
                                                      new Classification(rank: "phylum", name: "Charophyta"),
                                                      new Classification(rank: "class", name: "Equisetopsida"),
                                                      new Classification(rank: "subclass", name: "Magnoliidae"),
                                                      new Classification(rank: "superorder", name: "Austrobaileyanae"),
                                                      new Classification(rank: "order", name: "Austrobaileyales"),
                                                      new Classification(rank: "family", name: "Austrobaileyaceae"),
                                                      new Classification(rank: "genus", name: "Austrobaileya")])

        save new Profile(scientificName: "Austrobaileyaceae Croizat", fullName: "Austrobaileyaceae Croizat",
                opus: opus)

        save new Profile(scientificName: "Austrobaileya scandens", fullName: "Austrobaileya scandens",
                opus: opus, rank: "species", classification: [new Classification(rank: "kingdom", name: "plantae"),
                                                              new Classification(rank: "phylum", name: "Charophyta"),
                                                              new Classification(rank: "class", name: "Equisetopsida"),
                                                              new Classification(rank: "subclass", name: "Magnoliidae"),
                                                              new Classification(rank: "superorder", name: "Austrobaileyanae"),
                                                              new Classification(rank: "order", name: "Austrobaileyales"),
                                                              new Classification(rank: "family", name: "Austrobaileyaceae"),
                                                              new Classification(rank: "genus", name: "Austrobaileya"),
                                                              new Classification(rank: "species", name: "Austrobaileya scandens")])

        save new Profile(scientificName: "Austromatthaea L.S.Sm.", fullName: "Austromatthaea L.S.Sm.",
                opus: opus, rank: "genus", classification: [new Classification(rank: "kingdom", name: "plantae"),
                                                              new Classification(rank: "phylum", name: "Charophyta"),
                                                              new Classification(rank: "class", name: "Equisetopsida"),
                                                              new Classification(rank: "subclass", name: "Magnoliidae"),
                                                              new Classification(rank: "superorder", name: "Austrobaileyanae"),
                                                              new Classification(rank: "order", name: "Laurales"),
                                                              new Classification(rank: "family", name: "Monimiaceae"),
                                                              new Classification(rank: "genus", name: "Austromatthaea")])

        save new Profile(scientificName: "Austrobaileyaceae", fullName: "Austrobaileyaceae",
                opus: opus, rank: "family", classification: [new Classification(rank: "kingdom", name: "plantae"),
                                                             new Classification(rank: "phylum", name: "Charophyta"),
                                                             new Classification(rank: "class", name: "Equisetopsida"),
                                                             new Classification(rank: "subclass", name: "Magnoliidae"),
                                                             new Classification(rank: "superorder", name: "Austrobaileyanae"),
                                                             new Classification(rank: "order", name: "Austrobaileyales"),
                                                             new Classification(rank: "family", name: "Austrobaileyaceae")])


        when:
        def result = service.findByScientificName("Austro", [opus.uuid], ProfileSortOption.TAXONOMY)

        then:
        result.size() == 5
        result[0].scientificName == "Austrobaileyaceae" // family
        result[1].scientificName == "Austrobaileya" // genus
        result[2].scientificName == "Austromatthaea L.S.Sm." // genus then classification name
        result[3].scientificName == "Austrobaileya scandens" // species
        result[4].scientificName == "Austrobaileyaceae Croizat" // No rank
    }

    def "findByScientificName should sort the results alphabetically by name when sortOrder = NAME"() {
        Opus opus = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")

        save new Profile(scientificName: "Austrobaileyaceae", fullName: "Austrobaileyaceae",
                opus: opus, rank: "family", classification: [new Classification(rank: "kingdom", name: "plantae"),
                                                             new Classification(rank: "phylum", name: "Charophyta"),
                                                             new Classification(rank: "class", name: "Equisetopsida"),
                                                             new Classification(rank: "subclass", name: "Magnoliidae"),
                                                             new Classification(rank: "superorder", name: "Austrobaileyanae"),
                                                             new Classification(rank: "order", name: "Austrobaileyales"),
                                                             new Classification(rank: "family", name: "Austrobaileyaceae")])

        save new Profile(scientificName: "Austrobaileya", fullName: "Austrobaileya",
                opus: opus, rank: "genus", classification: [new Classification(rank: "kingdom", name: "plantae"),
                                                            new Classification(rank: "phylum", name: "Charophyta"),
                                                            new Classification(rank: "class", name: "Equisetopsida"),
                                                            new Classification(rank: "subclass", name: "Magnoliidae"),
                                                            new Classification(rank: "superorder", name: "Austrobaileyanae"),
                                                            new Classification(rank: "order", name: "Austrobaileyales"),
                                                            new Classification(rank: "family", name: "Austrobaileyaceae"),
                                                            new Classification(rank: "genus", name: "Austrobaileya")])

        save new Profile(scientificName: "Austrobaileya scandens", fullName: "Austrobaileya scandens",
                opus: opus, rank: "species", classification: [new Classification(rank: "kingdom", name: "plantae"),
                                                              new Classification(rank: "phylum", name: "Charophyta"),
                                                              new Classification(rank: "class", name: "Equisetopsida"),
                                                              new Classification(rank: "subclass", name: "Magnoliidae"),
                                                              new Classification(rank: "superorder", name: "Austrobaileyanae"),
                                                              new Classification(rank: "order", name: "Austrobaileyales"),
                                                              new Classification(rank: "family", name: "Austrobaileyaceae"),
                                                              new Classification(rank: "genus", name: "Austrobaileya"),
                                                              new Classification(rank: "species", name: "Austrobaileya scandens")])

        when:
        def result = service.findByScientificName("Austro", [opus.uuid], ProfileSortOption.NAME)

        then:
        result.size() == 3
        result[0].scientificName == "Austrobaileya" // genus
        result[1].scientificName == "Austrobaileya scandens" // species
        result[2].scientificName == "Austrobaileyaceae" // family
    }

    def "findByClassificationNameAndRank should fail when no scientific name is provided"() {
        when:
        service.findByClassificationNameAndRank(null, "name", ["opus"])

        then:
        thrown IllegalArgumentException

        when:
        service.findByClassificationNameAndRank("taxon", null, ["opus"])

        then:
        thrown IllegalArgumentException
    }

    def "findByClassificationNameAndRank should match any opus when no opusId list is provided"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")
        Opus opus2 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr2", title: "title2")
        Opus opus3 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr3", title: "title3")

        Profile profile1 = save new Profile(scientificName: "name1", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])
        Profile profile2 = save new Profile(scientificName: "name2", opus: opus2, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])
        Profile profile3 = save new Profile(scientificName: "name3", opus: opus3, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])

        when:
        List result = service.findByClassificationNameAndRank("kingdom", "plantae", null)

        then:
        result.size() == 3
        result.find { it.scientificName == profile1.scientificName } != null
        result.find { it.scientificName == profile2.scientificName } != null
        result.find { it.scientificName == profile3.scientificName } != null
    }

    def "findByClassificationNameAndRank should respect all master lists when no opusId list is provided"() {
        given:

        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1", masterListUid: 'test')
        Opus opus2 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr2", title: "title2")

        Profile opus1name1 = save new Profile(scientificName: "name1", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])
        Profile opus1name2 = save new Profile(scientificName: "name2", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])
        Profile opus1name3 = save new Profile(scientificName: "name3", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")]) // name3 not on test masterlist
        Profile opus2name1 = save new Profile(scientificName: "name1", opus: opus2, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])
        Profile opus2name2 = save new Profile(scientificName: "name2", opus: opus2, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])
        Profile opus2name3 = save new Profile(scientificName: "name3", opus: opus2, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])

        when:
        List result = service.findByClassificationNameAndRank("kingdom", "plantae", null)

        then:
        result.size() == 5
        result.find { it.scientificName == opus1name1.scientificName && it.opus.uuid == opus1.uuid } != null
        result.find { it.scientificName == opus1name2.scientificName && it.opus.uuid == opus1.uuid } != null
        result.find { it.scientificName == opus1name3.scientificName && it.opus.uuid == opus1.uuid } == null
        result.find { it.scientificName == opus2name1.scientificName && it.opus.uuid == opus2.uuid } != null
        result.find { it.scientificName == opus2name2.scientificName && it.opus.uuid == opus2.uuid } != null
        result.find { it.scientificName == opus2name3.scientificName && it.opus.uuid == opus2.uuid } != null
    }

    def "findByClassificationNameAndRank should exclude archived profiles"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")
        Opus opus2 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr2", title: "title2")
        Opus opus3 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr3", title: "title3")

        Profile profile1 = save new Profile(scientificName: "name1", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])
        Profile profile2 = save new Profile(scientificName: "name2", opus: opus2, archivedDate: new Date(), rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])
        Profile profile3 = save new Profile(scientificName: "name3", opus: opus3, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])

        when:
        List result = service.findByClassificationNameAndRank("kingdom", "plantae", null)

        then:
        result.size() == 2
        result.find { it.scientificName == profile1.scientificName } != null
        result.find { it.scientificName == profile2.scientificName } == null
        result.find { it.scientificName == profile3.scientificName } != null
    }

    def "findByClassificationNameAndRank should exclude the specified name and rank, ignoring case"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")
        Opus opus2 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr2", title: "title2")
        Opus opus3 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr3", title: "title3")

        Profile profile1 = save new Profile(scientificName: "name1", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])
        Profile profile2 = save new Profile(scientificName: "name2", opus: opus2, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])
        Profile profile3 = save new Profile(scientificName: "plantae", opus: opus3, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])

        when:
        List result = service.findByClassificationNameAndRank("kingdom", "planTAE", null)

        then:
        result.size() == 2
        result.find { it.scientificName == profile1.scientificName } != null
        result.find { it.scientificName == profile2.scientificName } != null
        result.find { it.scientificName == profile3.scientificName } == null
    }

    def "findByClassificationNameAndRank should match only the specified opus ids"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")
        Opus opus2 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr2", title: "title2")
        Opus opus3 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr3", title: "title3")

        Profile profile1 = save new Profile(scientificName: "name1", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])
        Profile profile2 = save new Profile(scientificName: "name2", opus: opus2, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])
        Profile profile3 = save new Profile(scientificName: "name3", opus: opus3, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])

        when:
        List result = service.findByClassificationNameAndRank("kingdom", "plantae", [opus1.uuid, opus3.uuid])

        then:
        result.size() == 2
        result.find { it.scientificName == profile1.scientificName } != null
        result.find { it.scientificName == profile2.scientificName } == null
        result.find { it.scientificName == profile3.scientificName } != null
    }

    def "findByClassificationNameAndRank should respect a master list when an opus id is provided"() {
        given:

        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1", masterListUid: 'test')
        Opus opus2 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr2", title: "title2")
        Opus opus3 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr2", title: "title2")

        Profile opus1name1 = save new Profile(scientificName: "name1", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])
        Profile opus1name2 = save new Profile(scientificName: "name2", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])
        Profile opus1name3 = save new Profile(scientificName: "name3", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")]) // name3 not on test masterlist
        Profile opus2name1 = save new Profile(scientificName: "name1", opus: opus2, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])
        Profile opus2name2 = save new Profile(scientificName: "name2", opus: opus2, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])
        Profile opus2name3 = save new Profile(scientificName: "name3", opus: opus2, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])
        Profile opus3name1 = save new Profile(scientificName: "name1", opus: opus3, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])
        Profile opus3name2 = save new Profile(scientificName: "name2", opus: opus3, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])
        Profile opus3name3 = save new Profile(scientificName: "name3", opus: opus3, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])

        when:
        List result = service.findByClassificationNameAndRank("kingdom", "plantae", [opus1.uuid, opus2.uuid])

        then:
        result.size() == 5
        result.find { it.scientificName == opus1name1.scientificName && it.opus.uuid == opus1.uuid } != null
        result.find { it.scientificName == opus1name2.scientificName && it.opus.uuid == opus1.uuid } != null
        result.find { it.scientificName == opus1name3.scientificName && it.opus.uuid == opus1.uuid } == null
        result.find { it.scientificName == opus2name1.scientificName && it.opus.uuid == opus2.uuid } != null
        result.find { it.scientificName == opus2name2.scientificName && it.opus.uuid == opus2.uuid } != null
        result.find { it.scientificName == opus2name3.scientificName && it.opus.uuid == opus2.uuid } != null
    }

    def "findByClassificationNameAndRank should recognise 'unknown' classifications"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")
        Opus opus2 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr2", title: "title2")
        Opus opus3 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr3", title: "title3")

        Profile profile1 = save new Profile(scientificName: "name1", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])
        Profile profile2 = save new Profile(scientificName: "name2", opus: opus2)
        Profile profile3 = save new Profile(scientificName: "name3", opus: opus3)

        when:
        List result = service.findByClassificationNameAndRank(SearchService.UNKNOWN_RANK, "plantae", [opus1.uuid, opus2.uuid, opus3.uuid])

        then:
        result.size() == 2
        result.find { it.scientificName == profile1.scientificName } == null
        result.find { it.scientificName == profile2.scientificName } != null
        result.find { it.scientificName == profile3.scientificName } != null
    }

    def "findByClassificationNameAndRank should ignore unknown opus ids"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")
        Opus opus2 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr2", title: "title2")
        Opus opus3 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr3", title: "title3")

        Profile profile1 = save new Profile(scientificName: "name1", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])
        Profile profile2 = save new Profile(scientificName: "name2", opus: opus2, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])
        Profile profile3 = save new Profile(scientificName: "name3", opus: opus3, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])

        when:
        List result = service.findByClassificationNameAndRank("kingdom", "plantae", [opus1.uuid, "unknown"])

        then:
        result.size() == 1
        result.find { it.scientificName == profile1.scientificName } != null
        result.find { it.scientificName == profile2.scientificName } == null
        result.find { it.scientificName == profile3.scientificName } == null
    }

    def "findByClassificationNameAndRank should perform exact match (case-insensitive)"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")
        Profile profile1 = save new Profile(scientificName: "name1", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plANtae")])
        Profile profile2 = save new Profile(scientificName: "name2", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "fungi")])
        Profile profile3 = save new Profile(scientificName: "name3", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "Animalae")])

        when:
        List result = service.findByClassificationNameAndRank("kingdom", "PLANTAE", [opus1.uuid])

        then:
        result.size() == 1
        result.find { it.scientificName == profile1.scientificName } != null
        result.find { it.scientificName == profile2.scientificName } == null
        result.find { it.scientificName == profile3.scientificName } == null
    }

    def "findByClassificationNameAndRank should limit the results to the specified maximum (names sorted alphabetically"() {
        given:
        Opus opus = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")

        Profile profile1 = save new Profile(scientificName: "zab", opus: opus, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "aa")])
        save new Profile(scientificName: "zac", opus: opus, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "ac")])
        Profile profile3 = save new Profile(scientificName: "zaa", opus: opus, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "aa")])
        save new Profile(scientificName: "zad", opus: opus, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "ad")])

        when:
        List result = service.findByClassificationNameAndRank("kingdom", "aa", [opus.uuid], ProfileSortOption.NAME, 1)

        then:
        result[0].scientificName == profile3.scientificName

        when:
        result = service.findByClassificationNameAndRank("kingdom", "aa", [opus.uuid], ProfileSortOption.NAME, 2)

        then:
        result.size() == 2
        result.find { it.scientificName == profile1.scientificName } != null
        result.find { it.scientificName == profile3.scientificName } != null
    }

    def "findByClassificationNameAndRank should start the result set at the specified offset, sorted taxonomically"() {
        given:
        Opus opus = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")

        Profile profile1 = save new Profile(scientificName: "ab", opus: opus, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "a")])
        Profile profile2 = save new Profile(scientificName: "ac", opus: opus, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "a")])
        Profile profile3 = save new Profile(scientificName: "aa", opus: opus, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "a")])
        Profile profile4 = save new Profile(scientificName: "ad", opus: opus, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "a")])

        when:
        List result = service.findByClassificationNameAndRank("kingdom", "a", [opus.uuid], ProfileSortOption.TAXONOMY, 1, 1)

        then:
        result[0].scientificName == profile1.scientificName

        when:
        result = service.findByClassificationNameAndRank("kingdom", "a", [opus.uuid], ProfileSortOption.TAXONOMY, 2, 2)

        then: "the results should be [aa, ab] (i.e. [profile3, profile1]"
        result.size() == 2
        result.find { it.scientificName == profile1.scientificName } == null
        result.find { it.scientificName == profile2.scientificName } != null
        result.find { it.scientificName == profile3.scientificName } == null
        result.find { it.scientificName == profile4.scientificName } != null
    }

    def "findByClassificationNameAndRank should sort the results by taxonomic hierarchy when sortOrder = TAXONOMY"() {
        Opus opus = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")

        save new Profile(scientificName: "Trimeniaceae",
                opus: opus, rank: "family", classification: [new Classification(rank: "kingdom", name: "plantae"),
                                                             new Classification(rank: "phylum", name: "Charophyta"),
                                                             new Classification(rank: "class", name: "Equisetopsida"),
                                                             new Classification(rank: "subclass", name: "Magnoliidae"),
                                                             new Classification(rank: "superorder", name: "Austrobaileyanae"),
                                                             new Classification(rank: "order", name: "Austrobaileyales"),
                                                             new Classification(rank: "family", name: "Trimeniaceae")])

        save new Profile(scientificName: "Trimenia",
                opus: opus, rank: "genus", classification: [new Classification(rank: "kingdom", name: "plantae"),
                                                            new Classification(rank: "phylum", name: "Charophyta"),
                                                            new Classification(rank: "class", name: "Equisetopsida"),
                                                            new Classification(rank: "subclass", name: "Magnoliidae"),
                                                            new Classification(rank: "superorder", name: "Austrobaileyanae"),
                                                            new Classification(rank: "order", name: "Austrobaileyales"),
                                                            new Classification(rank: "family", name: "Trimeniaceae"),
                                                            new Classification(rank: "genus", name: "Trimenia")])

        save new Profile(scientificName: "Trimenia moorei",
                opus: opus, rank: "species", classification: [new Classification(rank: "kingdom", name: "plantae"),
                                                              new Classification(rank: "phylum", name: "Charophyta"),
                                                              new Classification(rank: "class", name: "Equisetopsida"),
                                                              new Classification(rank: "subclass", name: "Magnoliidae"),
                                                              new Classification(rank: "superorder", name: "Austrobaileyanae"),
                                                              new Classification(rank: "order", name: "Austrobaileyales"),
                                                              new Classification(rank: "family", name: "Trimeniaceae"),
                                                              new Classification(rank: "genus", name: "Trimenia"),
                                                              new Classification(rank: "species", name: "Trimenia moorei")])

        save new Profile(scientificName: "Austrobaileyaceae",
                opus: opus, rank: "family", classification: [new Classification(rank: "kingdom", name: "plantae"),
                                                             new Classification(rank: "phylum", name: "Charophyta"),
                                                             new Classification(rank: "class", name: "Equisetopsida"),
                                                             new Classification(rank: "subclass", name: "Magnoliidae"),
                                                             new Classification(rank: "superorder", name: "Austrobaileyanae"),
                                                             new Classification(rank: "order", name: "Austrobaileyales"),
                                                             new Classification(rank: "family", name: "Austrobaileyaceae")])

        save new Profile(scientificName: "Austrobaileya",
                opus: opus, rank: "genus", classification: [new Classification(rank: "kingdom", name: "plantae"),
                                                            new Classification(rank: "phylum", name: "Charophyta"),
                                                            new Classification(rank: "class", name: "Equisetopsida"),
                                                            new Classification(rank: "subclass", name: "Magnoliidae"),
                                                            new Classification(rank: "superorder", name: "Austrobaileyanae"),
                                                            new Classification(rank: "order", name: "Austrobaileyales"),
                                                            new Classification(rank: "family", name: "Austrobaileyaceae"),
                                                            new Classification(rank: "genus", name: "Austrobaileya")])

        save new Profile(scientificName: "Austrobaileya scandens",
                opus: opus, rank: "species", classification: [new Classification(rank: "kingdom", name: "plantae"),
                                                              new Classification(rank: "phylum", name: "Charophyta"),
                                                              new Classification(rank: "class", name: "Equisetopsida"),
                                                              new Classification(rank: "subclass", name: "Magnoliidae"),
                                                              new Classification(rank: "superorder", name: "Austrobaileyanae"),
                                                              new Classification(rank: "order", name: "Austrobaileyales"),
                                                              new Classification(rank: "family", name: "Austrobaileyaceae"),
                                                              new Classification(rank: "genus", name: "Austrobaileya"),
                                                              new Classification(rank: "species", name: "Austrobaileya scandens")])

        when:
        def result = service.findByClassificationNameAndRank("order", "Austrobaileyales", [opus.uuid], ProfileSortOption.TAXONOMY)

        then:
        result.size() == 6
        result[0].scientificName == "Austrobaileyaceae"
        result[1].scientificName == "Trimeniaceae"
        result[2].scientificName == "Austrobaileya"
        result[3].scientificName == "Trimenia"
        result[4].scientificName == "Austrobaileya scandens"
        result[5].scientificName == "Trimenia moorei"
    }

    def "findByClassificationNameAndRank should sort the results alphabetically by name when sortOrder = NAME"() {
        Opus opus = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")

        save new Profile(scientificName: "Trimeniaceae",
                opus: opus, rank: "family", classification: [new Classification(rank: "kingdom", name: "plantae"),
                                                             new Classification(rank: "phylum", name: "Charophyta"),
                                                             new Classification(rank: "class", name: "Equisetopsida"),
                                                             new Classification(rank: "subclass", name: "Magnoliidae"),
                                                             new Classification(rank: "superorder", name: "Austrobaileyanae"),
                                                             new Classification(rank: "order", name: "Austrobaileyales"),
                                                             new Classification(rank: "family", name: "Trimeniaceae")])

        save new Profile(scientificName: "Trimenia",
                opus: opus, rank: "genus", classification: [new Classification(rank: "kingdom", name: "plantae"),
                                                            new Classification(rank: "phylum", name: "Charophyta"),
                                                            new Classification(rank: "class", name: "Equisetopsida"),
                                                            new Classification(rank: "subclass", name: "Magnoliidae"),
                                                            new Classification(rank: "superorder", name: "Austrobaileyanae"),
                                                            new Classification(rank: "order", name: "Austrobaileyales"),
                                                            new Classification(rank: "family", name: "Trimeniaceae"),
                                                            new Classification(rank: "genus", name: "Trimenia")])

        save new Profile(scientificName: "Trimenia moorei",
                opus: opus, rank: "species", classification: [new Classification(rank: "kingdom", name: "plantae"),
                                                              new Classification(rank: "phylum", name: "Charophyta"),
                                                              new Classification(rank: "class", name: "Equisetopsida"),
                                                              new Classification(rank: "subclass", name: "Magnoliidae"),
                                                              new Classification(rank: "superorder", name: "Austrobaileyanae"),
                                                              new Classification(rank: "order", name: "Austrobaileyales"),
                                                              new Classification(rank: "family", name: "Trimeniaceae"),
                                                              new Classification(rank: "genus", name: "Trimenia"),
                                                              new Classification(rank: "species", name: "Trimenia moorei")])

        save new Profile(scientificName: "Austrobaileyaceae",
                opus: opus, rank: "family", classification: [new Classification(rank: "kingdom", name: "plantae"),
                                                             new Classification(rank: "phylum", name: "Charophyta"),
                                                             new Classification(rank: "class", name: "Equisetopsida"),
                                                             new Classification(rank: "subclass", name: "Magnoliidae"),
                                                             new Classification(rank: "superorder", name: "Austrobaileyanae"),
                                                             new Classification(rank: "order", name: "Austrobaileyales"),
                                                             new Classification(rank: "family", name: "Austrobaileyaceae")])

        save new Profile(scientificName: "Austrobaileya",
                opus: opus, rank: "genus", classification: [new Classification(rank: "kingdom", name: "plantae"),
                                                            new Classification(rank: "phylum", name: "Charophyta"),
                                                            new Classification(rank: "class", name: "Equisetopsida"),
                                                            new Classification(rank: "subclass", name: "Magnoliidae"),
                                                            new Classification(rank: "superorder", name: "Austrobaileyanae"),
                                                            new Classification(rank: "order", name: "Austrobaileyales"),
                                                            new Classification(rank: "family", name: "Austrobaileyaceae"),
                                                            new Classification(rank: "genus", name: "Austrobaileya")])

        save new Profile(scientificName: "Austrobaileya scandens",
                opus: opus, rank: "species", classification: [new Classification(rank: "kingdom", name: "plantae"),
                                                              new Classification(rank: "phylum", name: "Charophyta"),
                                                              new Classification(rank: "class", name: "Equisetopsida"),
                                                              new Classification(rank: "subclass", name: "Magnoliidae"),
                                                              new Classification(rank: "superorder", name: "Austrobaileyanae"),
                                                              new Classification(rank: "order", name: "Austrobaileyales"),
                                                              new Classification(rank: "family", name: "Austrobaileyaceae"),
                                                              new Classification(rank: "genus", name: "Austrobaileya"),
                                                              new Classification(rank: "species", name: "Austrobaileya scandens")])

        when:
        def result = service.findByClassificationNameAndRank("order", "Austrobaileyales", [opus.uuid], ProfileSortOption.NAME)

        then:
        result.size() == 6
        result[0].scientificName == "Austrobaileya"
        result[1].scientificName == "Austrobaileya scandens"
        result[2].scientificName == "Austrobaileyaceae"
        result[3].scientificName == "Trimenia"
        result[4].scientificName == "Trimenia moorei"
        result[5].scientificName == "Trimeniaceae"
    }

    def "findByClassificationNameAndRank should sort the results alphabetically within the same rank"() {
        Opus opus = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")

        save new Profile(scientificName: "b",
                opus: opus, rank: "family", classification: [new Classification(rank: "kingdom", name: "plantae"),
                                                             new Classification(rank: "phylum", name: "Charophyta"),
                                                             new Classification(rank: "class", name: "Equisetopsida"),
                                                             new Classification(rank: "subclass", name: "Magnoliidae"),
                                                             new Classification(rank: "superorder", name: "Austrobaileyanae"),
                                                             new Classification(rank: "order", name: "Austrobaileyales"),
                                                             new Classification(rank: "family", name: "Trimeniaceae")])

        save new Profile(scientificName: "a",
                opus: opus, rank: "family", classification: [new Classification(rank: "kingdom", name: "plantae"),
                                                             new Classification(rank: "phylum", name: "Charophyta"),
                                                             new Classification(rank: "class", name: "Equisetopsida"),
                                                             new Classification(rank: "subclass", name: "Magnoliidae"),
                                                             new Classification(rank: "superorder", name: "Austrobaileyanae"),
                                                             new Classification(rank: "order", name: "Austrobaileyales"),
                                                             new Classification(rank: "family", name: "Trimeniaceae")])

        save new Profile(scientificName: "c",
                opus: opus, rank: "family", classification: [new Classification(rank: "kingdom", name: "plantae"),
                                                             new Classification(rank: "phylum", name: "Charophyta"),
                                                             new Classification(rank: "class", name: "Equisetopsida"),
                                                             new Classification(rank: "subclass", name: "Magnoliidae"),
                                                             new Classification(rank: "superorder", name: "Austrobaileyanae"),
                                                             new Classification(rank: "order", name: "Austrobaileyales"),
                                                             new Classification(rank: "family", name: "Trimeniaceae")])

        when:
        def result = service.findByClassificationNameAndRank("family", "Trimeniaceae", [opus.uuid])

        then:
        result.size() == 3
        result[0].scientificName == "a"
        result[1].scientificName == "b"
        result[2].scientificName == "c"
    }

    def "findByClassificationNameAndRank should only provide immediate children when asked"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")
        Opus opus2 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr2", title: "title2")
        Opus opus3 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr3", title: "title3")

        Profile profile1 = save new Profile(scientificName: "name1", opus: opus1, rank: "phylum", classification: [new Classification(rank: "kingdom", name: "plantae"), new Classification(rank: "phylum", name: "name1")])
        Profile profile2 = save new Profile(scientificName: "name2", opus: opus2, rank: "class", classification: [new Classification(rank: "kingdom", name: "plantae"), new Classification(rank: "phylum", name: "name1"), new Classification(rank: "class", name: "name2")])
        Profile profile3 = save new Profile(scientificName: "name3", opus: opus3, rank: "class", classification: [new Classification(rank: "kingdom", name: "plantae"), new Classification(rank: "class", name: "name3")])

        when:
        List result = service.findByClassificationNameAndRank("kingdom", "plantae", null, ProfileSortOption.default, -1, 0, true)

        then:
        result.size() == 2
        result.find { it.scientificName == profile1.scientificName } != null
        result.find { it.scientificName == profile2.scientificName } == null
        result.find { it.scientificName == profile3.scientificName } != null
    }

    def "findByClassificationNameAndRank should only provide search taxon when asked"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")
        Opus opus2 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr2", title: "title2")
        Opus opus3 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr3", title: "title3")

        Profile profile1 = save new Profile(scientificName: "plantae", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])
        Profile profile2 = save new Profile(scientificName: "name1", opus: opus1, rank: "phylum", classification: [new Classification(rank: "kingdom", name: "plantae"), new Classification(rank: "phylum", name: "name1")])
        Profile profile3 = save new Profile(scientificName: "name2", opus: opus2, rank: "class", classification: [new Classification(rank: "kingdom", name: "plantae"), new Classification(rank: "phylum", name: "name1"), new Classification(rank: "class", name: "name2")])
        Profile profile4 = save new Profile(scientificName: "name3", opus: opus3, rank: "class", classification: [new Classification(rank: "kingdom", name: "plantae"), new Classification(rank: "class", name: "name3")])

        when:
        List result = service.findByClassificationNameAndRank("kingdom", "plantae", null, ProfileSortOption.default, -1, 0, false, true)

        then:
        result.size() == 4
        result.find { it.scientificName == 'plantae' } != null
    }

    def "findByClassificationNameAndRank should filter by rank when asked"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")
        Opus opus2 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr2", title: "title2")
        Opus opus3 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr3", title: "title3")

        Profile profile1 = save new Profile(scientificName: "plantae", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])
        Profile profile2 = save new Profile(scientificName: "name1", opus: opus1, rank: "phylum", classification: [new Classification(rank: "kingdom", name: "plantae"), new Classification(rank: "phylum", name: "name1")])
        Profile profile3 = save new Profile(scientificName: "name2", opus: opus2, rank: "class", classification: [new Classification(rank: "kingdom", name: "plantae"), new Classification(rank: "phylum", name: "name1"), new Classification(rank: "class", name: "name2")])
        Profile profile4 = save new Profile(scientificName: "name3", opus: opus3, rank: "class", classification: [new Classification(rank: "kingdom", name: "plantae"), new Classification(rank: "class", name: "name3")])

        when:
        List result = service.findByClassificationNameAndRank("kingdom", "plantae", null, ProfileSortOption.default, -1, 0, false, true, 'phylum')

        then:
        result.size() == 1
        result.find { it.scientificName == 'name1' } != null
    }

    def "totalByClassificationNameAndRank should exclude archived profiles"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")
        Opus opus2 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr2", title: "title2")
        Opus opus3 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr3", title: "title3")

        Profile profile1 = save new Profile(scientificName: "name1", opus: opus1, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])
        Profile profile2 = save new Profile(scientificName: "name2", opus: opus2, archivedDate: new Date(), rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])
        Profile profile3 = save new Profile(scientificName: "name3", opus: opus3, rank: "kingdom", classification: [new Classification(rank: "kingdom", name: "plantae")])

        when:
        int result = service.totalDescendantsByClassificationAndRank("kingdom", "plantae", null)

        then:
        result == 2
    }

    def "totalByClassificationNameAndRank should only provide immediate children when asked"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")
        Opus opus2 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr2", title: "title2")
        Opus opus3 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr3", title: "title3")

        Profile profile1 = save new Profile(scientificName: "name1", opus: opus1, rank: "phylum", classification: [new Classification(rank: "kingdom", name: "plantae"), new Classification(rank: "phylum", name: "name1")])
        Profile profile2 = save new Profile(scientificName: "name2", opus: opus2, rank: "class", classification: [new Classification(rank: "kingdom", name: "plantae"), new Classification(rank: "phylum", name: "name1"), new Classification(rank: "class", name: "name2")])
        Profile profile3 = save new Profile(scientificName: "name3", opus: opus3, rank: "class", classification: [new Classification(rank: "kingdom", name: "plantae"), new Classification(rank: "class", name: "name3")])

        when:
        int result = service.totalDescendantsByClassificationAndRank("kingdom", "plantae", null, true)

        then:
        result == 2
    }

    def "totalByClassificationNameAndRank should include searched taxon when asked"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")
        Opus opus2 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr2", title: "title2")
        Opus opus3 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr3", title: "title3")

        Profile profile1 = save new Profile(scientificName: "name1", opus: opus1, rank: "phylum", classification: [new Classification(rank: "kingdom", name: "plantae"), new Classification(rank: "phylum", name: "name1")])
        Profile profile2 = save new Profile(scientificName: "name2", opus: opus2, rank: "class", classification: [new Classification(rank: "kingdom", name: "plantae"), new Classification(rank: "phylum", name: "name1"), new Classification(rank: "class", name: "name2")])
        Profile profile3 = save new Profile(scientificName: "name3", opus: opus3, rank: "class", classification: [new Classification(rank: "kingdom", name: "plantae"), new Classification(rank: "class", name: "name3")])

        when:
        int result = service.totalDescendantsByClassificationAndRank("phylum", "name1", null, false, true)

        then:
        result == 2

        when:
        result = service.totalDescendantsByClassificationAndRank("phylum", "name1", null, false, false)

        then:
        result == 1
    }

    def "totalByClassificationNameAndRank should only provide rank filtered results when asked"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")
        Opus opus2 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr2", title: "title2")
        Opus opus3 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr3", title: "title3")

        Profile profile1 = save new Profile(scientificName: "name1", opus: opus1, rank: "phylum", classification: [new Classification(rank: "kingdom", name: "plantae"), new Classification(rank: "phylum", name: "name1")])
        Profile profile2 = save new Profile(scientificName: "name2", opus: opus2, rank: "class", classification: [new Classification(rank: "kingdom", name: "plantae"), new Classification(rank: "phylum", name: "name1"), new Classification(rank: "class", name: "name2")])
        Profile profile3 = save new Profile(scientificName: "name3", opus: opus3, rank: "class", classification: [new Classification(rank: "kingdom", name: "plantae"), new Classification(rank: "class", name: "name3")])

        when:
        int result = service.totalDescendantsByClassificationAndRank("kingdom", "plantae", null, false, false, 'phylum')

        then:
        result == 1

        when:
        result = service.totalDescendantsByClassificationAndRank("kingdom", "plantae", null, false, false, null)

        then:
        result == 3

    }

    def "hasDescendantsByClassificationAndRank should return true when there is at least one child"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")

        Profile profile1 = save new Profile(scientificName: "class1", opus: opus1, rank: "class", classification: [new Classification(rank: "kingdom", name: "plantae"), new Classification(rank: "phylum", name: "name1"), new Classification(rank: 'class', name: 'class1')])

        when:
        boolean result = service.hasDescendantsByClassificationAndRank("kingdom", "plantae", opus1, false)

        then:
        result == true
    }

    def "hasDescendantsByClassificationAndRank should return true when there is at least one immediate child"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")

        Profile profile1 = save new Profile(scientificName: "name1", opus: opus1, rank: "phylum", classification: [new Classification(rank: "kingdom", name: "plantae"), new Classification(rank: "phylum", name: "name1")])

        when:
        boolean result = service.hasDescendantsByClassificationAndRank("kingdom", "plantae", opus1, true)

        then:
        result == true
    }

    def "hasDescendantsByClassificationAndRank should exclude archived profiles"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")

        Profile profile1 = save new Profile(scientificName: "name1", opus: opus1, rank: "phylum", archivedDate: new Date(), classification: [new Classification(rank: "kingdom", name: "plantae"), new Classification(rank: "phylum", name: "name1")])
        Profile profile2 = save new Profile(scientificName: "name2", opus: opus1, rank: "class", archivedDate: new Date(), classification: [new Classification(rank: "kingdom", name: "plantae"), new Classification(rank: "phylum", name: "name1"), new Classification(rank: "class", name: "name2")])
        Profile profile3 = save new Profile(scientificName: "name3", opus: opus1, rank: "class", archivedDate: new Date(), classification: [new Classification(rank: "kingdom", name: "plantae"), new Classification(rank: "class", name: "name3")])

        when:
        boolean result = service.hasDescendantsByClassificationAndRank("kingdom", "plantae", opus1, false)

        then:
        result == false
    }

    def "hasDescendantsByClassificationAndRank should obey immediate children"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")

        Profile profile1 = save new Profile(scientificName: "name2", opus: opus1, rank: "class", archivedDate: new Date(), classification: [new Classification(rank: "kingdom", name: "plantae"), new Classification(rank: "phylum", name: "name1"), new Classification(rank: "class", name: "name2")])

        when:
        boolean result = service.hasDescendantsByClassificationAndRank("kingdom", "plantae", opus1, true)

        then:
        result == false
    }

    def "getRanks should fail if no opus id is provided"() {
        when:
        service.getRanks(null)

        then:
        thrown IllegalArgumentException
    }

    def "getRanks should return an empty Map the opus does not exist"() {
        when:
        Map result = service.getRanks("unknown")

        then:
        result == [:]
    }

    def "getRanks should group and count all unique classification levels, including unknown classifications"() {
        given:
        Opus opus = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")

        save new Profile(scientificName: "unknown1", opus: opus)
        save new Profile(scientificName: "unknown2", opus: opus)

        save new Profile(scientificName: "kingdom1", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae")])
        save new Profile(scientificName: "kingdom2", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae")])
        save new Profile(scientificName: "kingdom3", opus: opus, classification: [new Classification(rank: "kingdom", name: "Animalia")])

        save new Profile(scientificName: "phylum1", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                 new Classification(rank: "phylum", name: "Charophyta")])
        save new Profile(scientificName: "phylum2", opus: opus, classification: [new Classification(rank: "kingdom", name: "Animalia"),
                                                                                 new Classification(rank: "phylum", name: "Hygrophila")])
        save new Profile(scientificName: "phylum3", opus: opus, classification: [new Classification(rank: "kingdom", name: "Animalia"),
                                                                                 new Classification(rank: "phylum", name: "Arthropoda")])

        save new Profile(scientificName: "class1", opus: opus, classification: [new Classification(kingdom: "Plantae"),
                                                                                new Classification(rank: "phylum", name: "Charophyta"),
                                                                                new Classification(rank: "class", name: "Equisetopsida")])

        save new Profile(scientificName: "subclass1", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                   new Classification(rank: "phylum", name: "Charophyta"),
                                                                                   new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                   new Classification(rank: "subclazz", name: "Cycadidae")])
        save new Profile(scientificName: "subclass2", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                   new Classification(rank: "phylum", name: "Charophyta"),
                                                                                   new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                   new Classification(rank: "subclazz", name: "Magnoliidae")])
        save new Profile(scientificName: "subclass3", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                   new Classification(rank: "phylum", name: "Charophyta"),
                                                                                   new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                   new Classification(rank: "subclazz", name: "Marattiidae")])
        save new Profile(scientificName: "subclass4", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                   new Classification(rank: "phylum", name: "Charophyta"),
                                                                                   new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                   new Classification(rank: "subclazz", name: "Ophioglossidae")])

        save new Profile(scientificName: "order1", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                new Classification(rank: "phylum", name: "Charophyta"),
                                                                                new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                new Classification(rank: "subclazz", name: "Ophioglossidae"),
                                                                                new Classification(rank: "order", name: "Apiales")])
        save new Profile(scientificName: "order2", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                new Classification(rank: "phylum", name: "Charophyta"),
                                                                                new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                new Classification(rank: "subclazz", name: "Ophioglossidae"),
                                                                                new Classification(rank: "order", name: "Arecales")])

        save new Profile(scientificName: "family1", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                 new Classification(rank: "phylum", name: "Charophyta"),
                                                                                 new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                 new Classification(rank: "subclazz", name: "Ophioglossidae"),
                                                                                 new Classification(rank: "order", name: "Apiales"),
                                                                                 new Classification(rank: "family", name: "Aphididae")])
        save new Profile(scientificName: "family2", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                 new Classification(rank: "phylum", name: "Charophyta"),
                                                                                 new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                 new Classification(rank: "subclazz", name: "Ophioglossidae"),
                                                                                 new Classification(rank: "order", name: "Apiales"),
                                                                                 new Classification(rank: "family", name: "Acanthaceae")])
        save new Profile(scientificName: "family3", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                 new Classification(rank: "phylum", name: "Charophyta"),
                                                                                 new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                 new Classification(rank: "subclazz", name: "Ophioglossidae"),
                                                                                 new Classification(rank: "order", name: "Apiales"),
                                                                                 new Classification(rank: "family", name: "Adoxaceae")])
        save new Profile(scientificName: "family4", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                 new Classification(rank: "phylum", name: "Charophyta"),
                                                                                 new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                 new Classification(rank: "subclazz", name: "Ophioglossidae"),
                                                                                 new Classification(rank: "order", name: "Apiales"),
                                                                                 new Classification(rank: "family", name: "Agapanthaceae")])

        save new Profile(scientificName: "genus1", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                new Classification(rank: "phylum", name: "Charophyta"),
                                                                                new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                new Classification(rank: "subclazz", name: "Ophioglossidae"),
                                                                                new Classification(rank: "order", name: "Apiales"),
                                                                                new Classification(rank: "family", name: "Agapanthaceae"),
                                                                                new Classification(rank: "genus", name: "Abildgaardia")])
        save new Profile(scientificName: "genus2", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                new Classification(rank: "phylum", name: "Charophyta"),
                                                                                new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                new Classification(rank: "subclazz", name: "Ophioglossidae"),
                                                                                new Classification(rank: "order", name: "Asterales"),
                                                                                new Classification(rank: "family", name: "Agapanthaceae"),
                                                                                new Classification(rank: "genus", name: "Abrophyllum")])

        save new Profile(scientificName: "species1", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                  new Classification(rank: "phylum", name: "Charophyta"),
                                                                                  new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                  new Classification(rank: "subclazz", name: "Ophioglossidae"),
                                                                                  new Classification(rank: "order", name: "Agapanthaceae"),
                                                                                  new Classification(rank: "genus", name: "Abrophyllum"),
                                                                                  new Classification(rank: "species", name: "bla")])


        when:
        Map levels = service.getRanks(opus.uuid)

        then:
        levels.kingdom == 2
        levels.phylum == 3
        levels.clazz == 1
        levels.subclazz == 4
        levels.order == 4
        levels.family == 4
        levels.genus == 2
        levels[SearchService.UNKNOWN_RANK] == 2
    }

    def "groupByRank should fail if no opusId or taxon are provided"() {
        when:
        service.groupByRank(null, "taxon")

        then:
        thrown IllegalArgumentException

        when:
        service.groupByRank("opus", null)

        then:
        thrown IllegalArgumentException
    }

    def "groupByRank should return an empty map when the opus does not exist"() {
        when:
        Map result = service.groupByRank("unknown", "kingdom")

        then:
        result == [:]
    }

    def "groupByRank should return a count of profiles with no rank/classification when the requested rank is 'unknown'"() {
        given:
        Opus opus = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")

        save new Profile(scientificName: "kingdom1", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae")])
        save new Profile(scientificName: "kingdom2", opus: opus)
        save new Profile(scientificName: "kingdom3", opus: opus)

        when:
        Map result = service.groupByRank(opus.uuid, SearchService.UNKNOWN_RANK)

        then:
        result == [(SearchService.UNKNOWN_RANK): 2]

    }

    def "groupByRank should return a map of names of the requested level, with their counts"() {
        given:
        Opus opus = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")

        save new Profile(scientificName: "kingdom1", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae")])
        save new Profile(scientificName: "kingdom2", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae")])
        save new Profile(scientificName: "kingdom3", opus: opus, classification: [new Classification(rank: "kingdom", name: "Animalia")])

        save new Profile(scientificName: "phylum1", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                 new Classification(rank: "phylum", name: "Charophyta")])
        save new Profile(scientificName: "phylum2", opus: opus, classification: [new Classification(rank: "kingdom", name: "Animalia"),
                                                                                 new Classification(rank: "phylum", name: "Hygrophila")])
        save new Profile(scientificName: "phylum3", opus: opus, classification: [new Classification(rank: "kingdom", name: "Animalia"),
                                                                                 new Classification(rank: "phylum", name: "Arthropoda")])

        save new Profile(scientificName: "class1", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                new Classification(rank: "phylum", name: "Charophyta"),
                                                                                new Classification(rank: "class", name: "Equisetopsida")])

        save new Profile(scientificName: "subclass1", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                   new Classification(rank: "phylum", name: "Charophyta"),
                                                                                   new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                   new Classification(rank: "subclazz", name: "Cycadidae")])
        save new Profile(scientificName: "subclass2", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                   new Classification(rank: "phylum", name: "Charophyta"),
                                                                                   new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                   new Classification(rank: "subclazz", name: "Magnoliidae")])
        save new Profile(scientificName: "subclass3", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                   new Classification(rank: "phylum", name: "Charophyta"),
                                                                                   new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                   new Classification(rank: "subclazz", name: "Marattiidae")])
        save new Profile(scientificName: "subclass4", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                   new Classification(rank: "phylum", name: "Charophyta"),
                                                                                   new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                   new Classification(rank: "subclazz", name: "Ophioglossidae")])

        save new Profile(scientificName: "order1", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                new Classification(rank: "phylum", name: "Charophyta"),
                                                                                new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                new Classification(rank: "subclazz", name: "Ophioglossidae"),
                                                                                new Classification(rank: "order", name: "Apiales")])
        save new Profile(scientificName: "order2", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                new Classification(rank: "phylum", name: "Charophyta"),
                                                                                new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                new Classification(rank: "subclazz", name: "Ophioglossidae"),
                                                                                new Classification(rank: "order", name: "Arecales")])

        save new Profile(scientificName: "family1", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                 new Classification(rank: "phylum", name: "Charophyta"),
                                                                                 new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                 new Classification(rank: "subclazz", name: "Ophioglossidae"),
                                                                                 new Classification(rank: "order", name: "Apiales"),
                                                                                 new Classification(rank: "family", name: "Aphididae")])
        save new Profile(scientificName: "family2", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                 new Classification(rank: "phylum", name: "Charophyta"),
                                                                                 new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                 new Classification(rank: "subclazz", name: "Ophioglossidae"),
                                                                                 new Classification(rank: "order", name: "Apiales"),
                                                                                 new Classification(rank: "family", name: "Acanthaceae")])
        save new Profile(scientificName: "family3", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                 new Classification(rank: "phylum", name: "Charophyta"),
                                                                                 new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                 new Classification(rank: "subclazz", name: "Ophioglossidae"),
                                                                                 new Classification(rank: "order", name: "Apiales"),
                                                                                 new Classification(rank: "family", name: "Adoxaceae")])
        save new Profile(scientificName: "family4", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                 new Classification(rank: "phylum", name: "Charophyta"),
                                                                                 new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                 new Classification(rank: "subclazz", name: "Ophioglossidae"),
                                                                                 new Classification(rank: "order", name: "Apiales"),
                                                                                 new Classification(rank: "family", name: "Agapanthaceae")])

        save new Profile(scientificName: "genus1", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                new Classification(rank: "phylum", name: "Charophyta"),
                                                                                new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                new Classification(rank: "subclazz", name: "Ophioglossidae"),
                                                                                new Classification(rank: "order", name: "Apiales"),
                                                                                new Classification(rank: "family", name: "Agapanthaceae"),
                                                                                new Classification(rank: "genus", name: "Abildgaardia")])
        save new Profile(scientificName: "genus2", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                new Classification(rank: "phylum", name: "Charophyta"),
                                                                                new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                new Classification(rank: "subclazz", name: "Ophioglossidae"),
                                                                                new Classification(rank: "order", name: "Asterales"),
                                                                                new Classification(rank: "family", name: "Agapanthaceae"),
                                                                                new Classification(rank: "genus", name: "Abrophyllum")])

        save new Profile(scientificName: "species1", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                  new Classification(rank: "phylum", name: "Charophyta"),
                                                                                  new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                  new Classification(rank: "subclazz", name: "Ophioglossidae"),
                                                                                  new Classification(rank: "order", name: "Agapanthaceae"),
                                                                                  new Classification(rank: "genus", name: "Abrophyllum"),
                                                                                  new Classification(rank: "species", name: "bla")])


        when:
        Map result = service.groupByRank(opus.uuid, "kingdom")

        then:
        result == [Animalia: 3, Plantae: 17]

        when:
        result = service.groupByRank(opus.uuid, "phylum")

        then:
        result == [Charophyta: 15, Hygrophila: 1, Arthropoda: 1]

        when:
        result = service.groupByRank(opus.uuid, "genus")

        then:
        result == [Abildgaardia: 1, Abrophyllum: 2]
    }



    def "groupByRank should exclude profiles at the specified rank (we only want a count of the descendants of the rank)"() {
        given:
        Opus opus = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")

        save new Profile(scientificName: "phylum", rank: "phylum", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                   new Classification(rank: "phylum", name: "Charophyta")])
        save new Profile(scientificName: "subclass1", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                   new Classification(rank: "phylum", name: "Charophyta"),
                                                                                   new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                   new Classification(rank: "subclazz", name: "Cycadidae")])
        save new Profile(scientificName: "subclass2", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                   new Classification(rank: "phylum", name: "Charophyta"),
                                                                                   new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                   new Classification(rank: "subclazz", name: "Magnoliidae")])


        when:
        Map result = service.groupByRank(opus.uuid, "phylum")

        then: "the count should exclude the first profile because it has the same rank we are searching for (phylum)"
        result == [Charophyta:2]
    }

    def "groupByRank should limit the results to the specified maximum (names sorted alphabetically"() {
        given:
        Opus opus = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")

        save new Profile(scientificName: "subclass1", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                   new Classification(rank: "phylum", name: "Charophyta"),
                                                                                   new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                   new Classification(rank: "subclazz", name: "Cycadidae")])
        save new Profile(scientificName: "subclass2", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                   new Classification(rank: "phylum", name: "Charophyta"),
                                                                                   new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                   new Classification(rank: "subclazz", name: "Magnoliidae")])
        save new Profile(scientificName: "subclass3", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                   new Classification(rank: "phylum", name: "Arthropoda"),
                                                                                   new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                   new Classification(rank: "subclazz", name: "Marattiidae")])
        save new Profile(scientificName: "subclass4", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                   new Classification(rank: "phylum", name: "Hygrophila"),
                                                                                   new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                   new Classification(rank: "subclazz", name: "Ophioglossidae")])

        when:
        Map result = service.groupByRank(opus.uuid, "phylum", null, 1)

        then:
        result == [Arthropoda: 1]

        when:
        result = service.groupByRank(opus.uuid, "phylum", null, 2)

        then:
        result == [Arthropoda: 1, Charophyta: 2]
    }

    def "groupByRank should start the result set at the specified offset"() {
        given:
        Opus opus = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")

        save new Profile(scientificName: "subclass1", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                   new Classification(rank: "phylum", name: "Charophyta"),
                                                                                   new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                   new Classification(rank: "subclazz", name: "Cycadidae")])
        save new Profile(scientificName: "subclass2", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                   new Classification(rank: "phylum", name: "Charophyta"),
                                                                                   new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                   new Classification(rank: "subclazz", name: "Magnoliidae")])
        save new Profile(scientificName: "subclass3", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                   new Classification(rank: "phylum", name: "Arthropoda"),
                                                                                   new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                   new Classification(rank: "subclazz", name: "Marattiidae")])
        save new Profile(scientificName: "subclass4", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                   new Classification(rank: "phylum", name: "Hygrophila"),
                                                                                   new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                   new Classification(rank: "subclazz", name: "Ophioglossidae")])

        when:
        Map result = service.groupByRank(opus.uuid, "phylum", null, 1, 1)

        then:
        result == [Charophyta: 2]

        when:
        result = service.groupByRank(opus.uuid, "phylum", null, 2, 2)

        then:
        result == [Hygrophila: 1]
    }



    def "groupByRank should filter the result set by name starting with [filter]"() {
        given:
        Opus opus = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")

        save new Profile(scientificName: "subclass1", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                   new Classification(rank: "phylum", name: "Charophyta"),
                                                                                   new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                   new Classification(rank: "subclazz", name: "Cycadidae")])
        save new Profile(scientificName: "subclass2", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                   new Classification(rank: "phylum", name: "Charophyta"),
                                                                                   new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                   new Classification(rank: "subclazz", name: "Magnoliidae")])
        save new Profile(scientificName: "subclass3", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                   new Classification(rank: "phylum", name: "Arthropoda"),
                                                                                   new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                   new Classification(rank: "subclazz", name: "Marattiidae")])
        save new Profile(scientificName: "subclass4", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                   new Classification(rank: "phylum", name: "Hygrophila"),
                                                                                   new Classification(rank: "clazz", name: "Equisetopsida"),
                                                                                   new Classification(rank: "subclazz", name: "Ophioglossidae")])

        when:
        Map result = service.groupByRank(opus.uuid, "phylum", "c")

        then:
        result == [Charophyta: 2]

        when:
        result = service.groupByRank(opus.uuid, "phylum", "hy")

        then:
        result == [Hygrophila: 1]
    }

    def "getImmediateChildren should list the next rank after the specified for all profiles"() {
        given:
        Opus opus = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")

        save new Profile(scientificName: "profile1", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                  new Classification(rank: "phylum", name: "Charophyta"),
                                                                                  new Classification(rank: "class", name: "Equisetopsida"),
                                                                                  new Classification(rank: "subclass", name: "Cycadidae"),
                                                                                  new Classification(rank: "order", name: "Cycadales")])

        save new Profile(scientificName: "profile2", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                  new Classification(rank: "phylum", name: "Charophyta"),
                                                                                  new Classification(rank: "class", name: "Equisetopsida"),
                                                                                  new Classification(rank: "subclass", name: "Lycopodiidae"),
                                                                                  new Classification(rank: "order", name: "Isoetales"),
                                                                                  new Classification(rank: "family", name: "Isoetaceae")])

        save new Profile(scientificName: "profile3", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                  new Classification(rank: "phylum", name: "Arthropoda"),
                                                                                  new Classification(rank: "class", name: "Equisetopsida"),
                                                                                  new Classification(rank: "subclass", name: "Lycopodiidae"),
                                                                                  new Classification(rank: "order", name: "Lycopodiales")])

        save new Profile(scientificName: "profile4", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                  new Classification(rank: "phylum", name: "Hygrophila"),
                                                                                  new Classification(rank: "class", name: "Equisetopsida"),
                                                                                  new Classification(rank: "subclass", name: "Magnoliidae"),
                                                                                  new Classification(rank: "order", name: "Fabales")])

        when: "asked for the children of subclass Lycopodiidae"
        List result = service.getImmediateChildren(opus, "subCLass", "LycoPODiidAE") // matches should be case insensitive

        then: "it should return the 2 orders directly below Lycopodiidae (in alphabetic order), but not the family below Lycopodiidae"
        result.size() == 2
        result[0].name == "Isoetales"
        result[0].rank == "order"
        result[1].name == "Lycopodiales"
        result[1].rank == "order"
    }

    def "getImmediateChildren should page the results"() {
        given:
        Opus opus = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")

        save new Profile(scientificName: "profile1", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                  new Classification(rank: "phylum", name: "Charophyta"),
                                                                                  new Classification(rank: "class", name: "Equisetopsida"),
                                                                                  new Classification(rank: "subclass", name: "Cycadidae"),
                                                                                  new Classification(rank: "order", name: "Cycadales")])

        save new Profile(scientificName: "profile2", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                  new Classification(rank: "phylum", name: "Charophyta"),
                                                                                  new Classification(rank: "class", name: "Equisetopsida"),
                                                                                  new Classification(rank: "subclass", name: "Lycopodiidae"),
                                                                                  new Classification(rank: "order", name: "Isoetales"),
                                                                                  new Classification(rank: "family", name: "Isoetaceae")])

        save new Profile(scientificName: "profile3", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                  new Classification(rank: "phylum", name: "Arthropoda"),
                                                                                  new Classification(rank: "class", name: "Equisetopsida"),
                                                                                  new Classification(rank: "subclass", name: "Lycopodiidae"),
                                                                                  new Classification(rank: "order", name: "Lycopodiales")])

        save new Profile(scientificName: "profile4", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                  new Classification(rank: "phylum", name: "Hygrophila"),
                                                                                  new Classification(rank: "class", name: "Equisetopsida"),
                                                                                  new Classification(rank: "subclass", name: "Magnoliidae"),
                                                                                  new Classification(rank: "order", name: "Fabales")])

        when: "asked for the children of subclass Lycopodiidae, with offset 1 and page size 1"
        List result = service.getImmediateChildren(opus, "subclass", "Lycopodiidae", null, 1, 1)

        then: "it should return the 2nd of the 2 orders directly below Lycopodiida"
        result.size() == 1
        result[0].name == "Lycopodiales"
        result[0].rank == "order"
    }

    def "getImmediateChildren should handle mixed ranks below the specified rank"() {
        given:
        Opus opus = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")

        save new Profile(scientificName: "profile1", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                  new Classification(rank: "phylum", name: "Charophyta"),
                                                                                  new Classification(rank: "class", name: "Equisetopsida"),
                                                                                  new Classification(rank: "subclass", name: "Cycadidae"),
                                                                                  new Classification(rank: "order", name: "Cycadales")])

        save new Profile(scientificName: "profile2", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                  new Classification(rank: "phylum", name: "Charophyta"),
                                                                                  new Classification(rank: "class", name: "Equisetopsida"),
                                                                                  new Classification(rank: "subclass", name: "Magnoliidae"),
                                                                                  new Classification(rank: "order", name: "Ranunculales")])

        save new Profile(scientificName: "profile3", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                  new Classification(rank: "phylum", name: "Charophyta"),
                                                                                  new Classification(rank: "class", name: "Equisetopsida"),
                                                                                  new Classification(rank: "subclass", name: "Magnoliidae"),
                                                                                  new Classification(rank: "superorder", name: "Asteranae")])

        save new Profile(scientificName: "profile4", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                  new Classification(rank: "phylum", name: "Charophyta"),
                                                                                  new Classification(rank: "class", name: "Equisetopsida"),
                                                                                  new Classification(rank: "subclass", name: "Magnoliidae"),
                                                                                  new Classification(rank: "order", name: "Fabales")])

        when: "asked for the children of subclass Magnoliidae"
        List result = service.getImmediateChildren(opus, "subclass", "Magnoliidae")

        then: "it should return the superorder Asteranae, and the two orders Fabales and Ranunculales, sorted by rank order then name alphabetically"
        result.size() == 3
        result[0].name == "Asteranae"
        result[0].rank == "superorder" // superorder is higher up the taxonomy than order, so must come first
        result[1].name == "Fabales"
        result[1].rank == "order"
        result[2].name == "Ranunculales"
        result[2].rank == "order"
    }

    def "getImmediateChildren should only return results from the specified Opus"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title1")
        Opus opus2 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title2")

        save new Profile(scientificName: "profile1", opus: opus1, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                   new Classification(rank: "phylum", name: "Charophyta"),
                                                                                   new Classification(rank: "class", name: "Equisetopsida"),
                                                                                   new Classification(rank: "subclass", name: "Cycadidae"),
                                                                                   new Classification(rank: "order", name: "Cycadales")])

        save new Profile(scientificName: "profile1", opus: opus2, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                   new Classification(rank: "phylum", name: "Charophyta"),
                                                                                   new Classification(rank: "class", name: "Equisetopsida"),
                                                                                   new Classification(rank: "subclass", name: "Magnoliidae"),
                                                                                   new Classification(rank: "order", name: "Ranunculales")])

        when: "asked for the children of class Equisetopsida in Opus1"
        List result = service.getImmediateChildren(opus1, "class", "Equisetopsida")

        then: "it should return not return Magnoliidae from Opus2"
        result.size() == 1
        result[0].name == "Cycadidae"
        result[0].rank == "subclass"
    }

    def "getImmediateChildren should filter results by the immediate child's name (case insensitive) when provided with a filter"() {
        given:
        Opus opus = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")

        save new Profile(scientificName: "profile1", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                  new Classification(rank: "phylum", name: "Charophyta"),
                                                                                  new Classification(rank: "class", name: "Equisetopsida"),
                                                                                  new Classification(rank: "subclass", name: "Cycadidae"),
                                                                                  new Classification(rank: "order", name: "Cycadales")])

        save new Profile(scientificName: "profile2", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                  new Classification(rank: "phylum", name: "Charophyta"),
                                                                                  new Classification(rank: "class", name: "Equisetopsida"),
                                                                                  new Classification(rank: "subclass", name: "Magnoliidae"),
                                                                                  new Classification(rank: "superorder", name: "Austrobaileyanae")])

        save new Profile(scientificName: "profile3", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                  new Classification(rank: "phylum", name: "Charophyta"),
                                                                                  new Classification(rank: "class", name: "Equisetopsida"),
                                                                                  new Classification(rank: "subclass", name: "Magnoliidae"),
                                                                                  new Classification(rank: "superorder", name: "Asteranae")])

        save new Profile(scientificName: "profile4", opus: opus, classification: [new Classification(rank: "kingdom", name: "Plantae"),
                                                                                  new Classification(rank: "phylum", name: "Charophyta"),
                                                                                  new Classification(rank: "class", name: "Equisetopsida"),
                                                                                  new Classification(rank: "subclass", name: "Magnoliidae"),
                                                                                  new Classification(rank: "superorder", name: "Lilianae")])

        when: "asked for the children of subclass Magnoliidae containing 'Aster'"
        List result = service.getImmediateChildren(opus, "subclass", "Magnoliidae", "aster")

        then: "it should return Asteranae"
        result.size() == 1
        result[0].name == "Asteranae"

        when: "asked for the children of subclass Magnoliidae containing 'anae'"
        result = service.getImmediateChildren(opus, "subclass", "Magnoliidae", "anae")

        then: "it should return Austrobaileyanae, Asteranae and Lilianae"
        result.size() == 3
        result[0].name == "Asteranae"
        result[1].name == "Austrobaileyanae"
        result[2].name == "Lilianae"
    }

    def "findProfilesForImmediateChildren should return a count of children, excluding the profile itself - zero count"() {
        given:
        Opus opus = save new Opus(uuid: "opusId", shortName: "opusid", title: "opusName", glossary: new Glossary(), dataResourceUid: "dr1")
        save new Profile(opus: opus, uuid: "profile1", scientificName: "Bleasdalea", rank: "genus", guid: "name1", classification: [
                new Classification(rank: "genus", name: "Bleasdalea")
        ])
        save new Profile(opus: opus, uuid: "profile2", scientificName: "Bleasdalea bleasdalei", rank: "species", guid: "name2", classification: [
                new Classification(rank: "genus", name: "Bleasdalea"),
                new Classification(rank: "species", name: "Bleasdalea bleasdalei")
        ])

        def list = [[name: "Bleasdalea bleasdalei", rank: "species", guid: "name2"]]

        when: "asked for the children of a genus where there is one species with no subspecies"
        def result = service.findProfilesForImmediateChildren(opus, list)

        then: "the result list should contain one profile (the species), and the child count of that profile should be 0 (no subsp.)"
        result.size() == 1
        result[0].childCount == 0
    }

    def "findProfilesForImmediateChildren should return a count of children, excluding the profile itself - non-zero count"() {
        given:
        Opus opus = save new Opus(uuid: "opusId", shortName: "opusid", title: "opusName", glossary: new Glossary(), dataResourceUid: "dr1")
        save new Profile(opus: opus, uuid: "profile3", scientificName: "Acacia", rank: "genus", guid: "name3", classification: [
                new Classification(rank: "genus", name: "Acacia")
        ])
        save new Profile(opus: opus, uuid: "profile4", scientificName: "Acacia dealbata", rank: "species", guid: "name4", classification: [
                new Classification(rank: "genus", name: "Acacia"),
                new Classification(rank: "species", name: "Acacia dealbata")
        ])
        save new Profile(opus: opus, uuid: "profile5", scientificName: "Acacia dealbata subsp. subalpina", rank: "subspecies", guid: "name5", classification: [
                new Classification(rank: "genus", name: "Acacia"),
                new Classification(rank: "species", name: "Acacia dealbata"),
                new Classification(rank: "subspecies", name: "Acacia dealbata subsp. subalpina")
        ])

        def list = [[name: "Acacia dealbata", rank: "species", guid: "name4"]]

        when: "asked for the children of a genus where there is one species with a subspecies"
        def result = service.findProfilesForImmediateChildren(opus, list)

        then: "the result list should contain one profile (the species), and the child count of that profile should be 1 (for the subsp.)"
        result.size() == 1
        result[0].childCount == 1
    }

    def "findProfilesForImmediateChildren should return a count of children, excluding the profile itself even when the taxonomy name != the profile name - zero count"() {
        given:
        Opus opus = save new Opus(uuid: "opusId", shortName: "opusid", title: "opusName", glossary: new Glossary(), dataResourceUid: "dr1")
        save new Profile(opus: opus, uuid: "profile1", scientificName: "Bleasdalea", rank: "genus", guid: "name1", classification: [
                new Classification(rank: "genus", name: "Bleasdalea")
        ])
        save new Profile(opus: opus, uuid: "profile2", scientificName: "Gevuina bleasdalei", rank: "species", guid: "name2", classification: [
                new Classification(rank: "genus", name: "Bleasdalea"),
                new Classification(rank: "species", name: "Bleasdalea bleasdalei")
        ])

        def list = [[name: "Bleasdalea bleasdalei", rank: "species", guid: "name2"]]

        when: "asked for the children of a genus where the taxonomy name (Bleasdalea bleasdalei) does not match the profile name (Gevuina bleasdalei)"
        def result = service.findProfilesForImmediateChildren(opus, list)

        then: "the result list should contain one profile (the species), and the child count of that profile should be 0 (no subsp.)"
        result.size() == 1
        result[0].childCount == 0
    }

    def "findProfilesForImmediateChildren should return a count of children, excluding the profile itself even when the taxonomy name != the profile name - non-zero count"() {
        given:
        Opus opus = save new Opus(uuid: "opusId", shortName: "opusid", title: "opusName", glossary: new Glossary(), dataResourceUid: "dr1")
        save new Profile(opus: opus, uuid: "profile3", scientificName: "Acacia", rank: "genus", guid: "name3", classification: [
                new Classification(rank: "genus", name: "Acacia")
        ])
        save new Profile(opus: opus, uuid: "profile4", scientificName: "Acacia dealbata", rank: "species", guid: "name4", classification: [
                new Classification(rank: "genus", name: "Acacia"),
                new Classification(rank: "species", name: "Acacia dealbata")
        ])
        save new Profile(opus: opus, uuid: "profile5", scientificName: "Racosperma dealbata subsp. subalpina", rank: "subspecies", guid: "name5", classification: [
                new Classification(rank: "genus", name: "Acacia"),
                new Classification(rank: "species", name: "Acacia dealbata"),
                new Classification(rank: "subspecies", name: "Acacia dealbata subsp. subalpina")
        ])

        def list = [[name: "Acacia dealbata", rank: "species", guid: "name4"]]

        when: "asked for the children of a genus where there is one species with a subspecies"
        def result = service.findProfilesForImmediateChildren(opus, list)

        then: "the result list should contain one profile (the species), and the child count of that profile should be 1 (for the subsp.)"
        result.size() == 1
        result[0].childCount == 1
    }

    def "buildTextSearch should include partial single matches from certain collection"() {
        given:
        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")
        Profile profile1 = save new Profile(scientificName: "Dilany", fullName: "name1", opus: opus1, rank: "species", classification: [new Classification(rank: "kingdom", name: "Plantae")])

        SearchOptions options = new SearchOptions()
        options.setNameOnly(false)
        options.setMatchAll(true)
        options.setIncludeArchived(false)
        options.setSearchAla(true)
        options.setSearchNsl(true)
        options.setIncludeNameAttributes(false)
        options.setHideStubs(true)

        when:
        Map qMap = service.buildTextSearch("dilan", options)

        then:
        qMap.findAll(it -> it.toString().contains(profile1.scientificName)) != null
    }

    def "buildTextSearch should include partial multiple matches from certain collection"() {
        given:
        String searchItem = "BURDAL TOTEM"

        Opus opus1 = save new Opus(glossary: new Glossary(), dataResourceUid: "dr1", title: "title1")
        Profile profile1 = save new Profile(scientificName: "Salt Water Crocodile", fullName: searchItem, opus: opus1, rank: "species", classification: [new Classification(rank: "kingdom", name: "Plantae")])

        SearchOptions options = new SearchOptions()
        options.setNameOnly(false)
        options.setMatchAll(true)
        options.setIncludeArchived(false)
        options.setSearchAla(true)
        options.setSearchNsl(true)
        options.setIncludeNameAttributes(false)
        options.setHideStubs(true)

        when:
        Map qMap = service.buildTextSearch(searchItem, options)

        then:
        qMap.findAll(it -> it.toString().contains(profile1.toString())) != null
    }
}
