package au.org.ala.profile

import com.codahale.metrics.MetricRegistry
import grails.gorm.transactions.Rollback
import grails.plugins.elasticsearch.ElasticSearchService
import grails.testing.mixin.integration.Integration
import org.springframework.beans.factory.annotation.Autowired

@Integration
@Rollback
class ImportServiceSpec extends BaseIntegrationSpec {

    @Autowired
    ImportService importService
    MasterListService masterListService
    NameService nameService
    ProfileService profileService
    ElasticSearchService elasticSearchService

    Closure doWithSpring() {{->
        metricRegistry(MetricRegistry)
    }}

    def setup() {
        masterListService = Stub(MasterListService)
        nameService = Stub(NameService)
        profileService = Stub(ProfileService)
        elasticSearchService = Stub(ElasticSearchService)
//        importService = new ImportService()
        importService.masterListService = masterListService
        importService.nameService = nameService
        importService.profileService = profileService
        importService.elasticSearchService = elasticSearchService

        masterListService.getMasterList(_) >> { opus -> [['name': 'a', 'scientificName': 'a'], ['name': 'b', 'scientificName': 'b'], ['name': 'C', 'scientificName': 'C']]}

        def nsldump = [
                [scientificName    : 'a',
                 scientificNameHtml: '<common><name id=\'1\'><element class=\'a\'>a</element></name></common>',
                 fullName          : 'a author',
                 fullNameHtml      : '<common><name id=\'1\'><element class=\'a\'>a</element></name></common>',
                 url               : "http://name/a",
                 nslIdentifier     : 'a',
                 rank              : 'genus',
                 nameAuthor        : 'author',
                 nslProtologue     : "Ching, R.C. (1963), Acta Phytotaxonomica Sinica 8 1963".trim(),
                 valid             : true,
                 status            : 'legitimate']
        ]

        def nslDumpResult = [
                bySimpleName: nsldump.groupBy { it.scientificName },
                byFullName: nsldump.groupBy { it.fullName }
        ]

        nameService.loadNSLSimpleNameDump() >> nslDumpResult
    }


    def 'test sync master list'() {
        given:
        def opus = save new Opus(title: "opus", shortName: 'opus', dataResourceUid: "123", glossary: new Glossary(), masterListUid: 'a')
        def a = save new Profile(opus: opus, scientificName: 'a', profileStatus: Profile.STATUS_EMPTY, emptyProfileVersion: ImportService.EMPTY_PROFILE_VERSION)
        def b = save new Profile(opus: opus, scientificName: 'b', profileStatus: Profile.STATUS_PARTIAL)
        def d = save new Profile(opus: opus, scientificName: 'd', profileStatus: Profile.STATUS_EMPTY, emptyProfileVersion: ImportService.EMPTY_PROFILE_VERSION)
        def e = save new Profile(opus: opus, scientificName: 'e', profileStatus: Profile.STATUS_PARTIAL)

        when:
        importService.syncroniseMasterList(opus.uuid)
        def a1 = Profile.findAllByOpusAndScientificName(opus,'a')
        def b1 = Profile.findAllByOpusAndScientificName(opus, 'b')
        def c1 = Profile.findAllByOpusAndScientificName(opus, 'C')
        def d1 = Profile.findAllByOpusAndScientificName(opus, 'd')
        def e1 = Profile.findAllByOpusAndScientificName(opus, 'e')

        then:
        a1.size() == 1
        a1[0].profileStatus == Profile.STATUS_EMPTY

        b1.size() == 1
        b1[0].profileStatus == Profile.STATUS_PARTIAL

        c1.size() == 1
        c1[0].profileStatus == Profile.STATUS_EMPTY
        c1[0].uuid != null
        c1[0].uuid != ''

        d1.size() == 0

        e1.size() == 1
        e1[0].profileStatus == Profile.STATUS_PARTIAL
    }

    def 'test empty profile version upgrade on sync'() {
        given:
        def opus = save new Opus(title: "opus1", shortName: 'opus1', dataResourceUid: "123", glossary: new Glossary(), masterListUid: 'a')

        def a = save new Profile(opus: opus, scientificName: 'a', profileStatus: Profile.STATUS_EMPTY, emptyProfileVersion: ImportService.EMPTY_PROFILE_VERSION - 1)
        def b = save new Profile(opus: opus, scientificName: 'b', profileStatus: Profile.STATUS_EMPTY, emptyProfileVersion: ImportService.EMPTY_PROFILE_VERSION)

        when:
        importService.syncroniseMasterList(opus.uuid)
        def a1 = Profile.findAllByScientificName('a')
        def b1 = Profile.findAllByScientificName('b')

        then:
        a1.size() == 1
        a1[0].emptyProfileVersion == ImportService.EMPTY_PROFILE_VERSION

        b1.size() == 1
        b1[0].uuid == b.uuid
    }

    def 'test master list profiles get a nsl nomenclature id'() {
        given:
        nameService.matchName(_) >> { String name ->
            [
                    guid          : "urn:lsid:$name",
                    scientificName: name,
                    nameAuthor    : "author",
                    rank          : null,
                    fullName      : "$name author"
            ]
        }
        nameService.matchCachedNSLName(_, _, _, _) >> { nslNamesCached, scientificName, nameAuthor, fullName ->
            def result = (nslNamesCached.byFullName[fullName] ?: nslNamesCached.bySimpleName[scientificName])
            result ? result[0] : [:]
        }
        nameService.findNomenclature(_, _) >> { nslIdentifier, strategy ->
            [
                    id         : "1234",
                    url        : "http://example.com/1234",
                    name       : "name",
                    nameHtml   : "name",
                    apcAccepted: true,
                    citations  : [
                                [
                                        relationship: "rel",
                                        nameType    : "nametype",
                                        fullName    : "fullName",
                                        simpleName  : "simpleName"
                                ]
                            ]
            ]
        }

        def opus = save new Opus(title: "opus1", shortName: 'opus1', dataResourceUid: "123", glossary: new Glossary(), masterListUid: 'a')

        when:
        importService.syncroniseMasterList(opus.uuid)
        def a1 = Profile.findAllByScientificName('a')

        then:
        a1.size() == 1
        a1[0].nslNameIdentifier != null
        a1[0].nslNomenclatureIdentifier != null
    }

    def 'test case sensitive sync master list'() {
        given:
        def opus = save new Opus(title: "opus", shortName: 'opus', dataResourceUid: "123", glossary: new Glossary(), masterListUid: 'a')
        def a = save new Profile(opus: opus, scientificName: 'A', profileStatus: Profile.STATUS_PARTIAL)
        def b = save new Profile(opus: opus, scientificName: 'b', profileStatus: Profile.STATUS_PARTIAL)

        when:
        importService.syncroniseMasterList(opus.uuid)
        def a1 = Profile.findAllByOpusAndScientificName(opus,'A')
        def a2 = Profile.findAllByOpusAndScientificName(opus,'a')
        def b1 = Profile.findAllByOpusAndScientificName(opus, 'b')
        def c1 = Profile.findAllByOpusAndScientificName(opus, 'C')

        then:
        a1.size() == 1
        a1[0].profileStatus == Profile.STATUS_PARTIAL

        a2.size() == 0

        b1.size() == 1
        b1[0].profileStatus == Profile.STATUS_PARTIAL

        c1.size() == 1
        c1[0].profileStatus == Profile.STATUS_EMPTY
    }

}
