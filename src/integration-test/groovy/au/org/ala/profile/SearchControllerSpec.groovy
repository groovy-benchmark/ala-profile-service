package au.org.ala.profile


import au.org.ala.profile.util.ProfileSortOption
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
class SearchControllerSpec extends BaseIntegrationSpec {

    @Autowired
    SearchController controller

    @Autowired
    WebApplicationContext ctx

    SearchService searchService

    def setup() {
        GrailsMockHttpServletRequest grailsMockHttpServletRequest = new GrailsMockHttpServletRequest()
        GrailsMockHttpServletResponse grailsMockHttpServletResponse = new GrailsMockHttpServletResponse()
        GrailsWebMockUtil.bindMockWebRequest(ctx, grailsMockHttpServletRequest, grailsMockHttpServletResponse)

        searchService = Mock(SearchService)
        controller.searchService = searchService
    }

    def "findByScientificName should return 400 BAD REQUEST if no scientificName was provided"() {
        when:
        controller.findByScientificName()

        then:
        controller.response.status == HttpStatus.SC_BAD_REQUEST
    }

    def "findByScientificName should default the opus list([]), wildcard (false), max (-1) and offset (0) parameters"() {
        when:
        controller.params.scientificName = "sciName"
        controller.findByScientificName()

        then:
        1 * searchService.findByScientificName("sciName", [], ProfileSortOption.getDefault(), false, -1, 0, false)
    }

    def "findByScientificName should use the provided values for the opus list, wildcard, max and offset parameters"() {
        when:
        controller.params.useWildcard = "true"
        controller.params.offset = 10
        controller.params.opusId = "one,two"
        controller.params.max = 666
        controller.params.scientificName = "sciName"
        controller.params.sortBy = "name"
        controller.params.autoCompleteScientificName = "true"
        controller.findByScientificName()

        then:
        1 * searchService.findByScientificName("sciName", ["one", "two"], ProfileSortOption.NAME, true, 666, 10, true)
    }

    def "findByClassificationNameAndRank should return 400 BAD REQUEST if no scientificName or taxon are provided"() {
        when:
        controller.params.scientificName = "sciName"
        controller.findByClassificationNameAndRank()

        then:
        controller.response.status == HttpStatus.SC_BAD_REQUEST


        when:
        controller.params.taxon = "taxon"
        controller.findByClassificationNameAndRank()

        then:
        controller.response.status == HttpStatus.SC_BAD_REQUEST
    }

    def "findByNameAndTaxonLevel should default the opus list([]), wildcard (false), max (-1) and offset (0) parameters"() {
        when:
        controller.params.scientificName = "sciName"
        controller.params.taxon = "taxon"
        controller.findByClassificationNameAndRank()

        then:
        1 * searchService.findByClassificationNameAndRank("taxon", "sciName", [], ProfileSortOption.getDefault(), -1, 0, false, false)
    }

    def "findByClassificationNameAndRank should use the provided values for the opus list, wildcard, max and offset parameters"() {
        when:
        controller.params.useWildcard = "true"
        controller.params.offset = 10
        controller.params.opusId = "one,two"
        controller.params.max = 666
        controller.params.scientificName = "sciName"
        controller.params.taxon = "taxon"
        controller.params.immediateChildrenOnly = 'true'
        controller.params.includeTaxon = 'false'
        controller.findByClassificationNameAndRank()

        then:
        1 * searchService.findByClassificationNameAndRank("taxon", "sciName", ["one", "two"], ProfileSortOption.getDefault(), 666, 10, true, false)
    }

    def "getRanks should return a 400 BAD REQUEST if no opus id was provided"() {
        when:
        controller.getRanks()

        then:
        controller.response.status == HttpStatus.SC_BAD_REQUEST
    }

    def "groupByRank should return 400 BAD REQUEST if no opusId or taxon are provided"() {
        when:
        controller.params.opusId = "opus1"
        controller.groupByRank()

        then:
        controller.response.status == HttpStatus.SC_BAD_REQUEST
    }

    def "groupByRank should return 400 BAD REQUEST if no opus id is provided"() {
        when:
        controller.params.taxon = "taxon"
        controller.groupByRank()

        then:
        controller.response.status == HttpStatus.SC_BAD_REQUEST
    }

    def "groupByRank should default the max (-1) and offset (0) parameters"() {
        given:
        save new Opus(uuid: "opusId", shortName: "opusid", title: "opusName", glossary: new Glossary(), dataResourceUid: "dr1")

        when:
        controller.params.opusId = "opusId"
        controller.params.taxon = "taxon"
        controller.groupByRank()

        then:
        1 * searchService.groupByRank("opusId", "taxon", null, -1, 0) >> [:]
    }

    def "groupByRank should use the provided values for the opus list, wildcard, max and offset parameters"() {
        given:
        save new Opus(uuid: "opusId", shortName: "opusid", title: "opusName", glossary: new Glossary(), dataResourceUid: "dr1")

        when:
        controller.params.offset = 10
        controller.params.opusId = "opusId"
        controller.params.max = 666
        controller.params.taxon = "taxon"
        controller.groupByRank()

        then:
        1 * searchService.groupByRank("opusId", "taxon", null, 666, 10) >> [:]
    }

}
