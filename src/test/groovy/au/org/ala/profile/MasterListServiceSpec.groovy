package au.org.ala.profile

import au.org.ala.ws.service.WebService
import com.codahale.metrics.MetricRegistry
import grails.testing.services.ServiceUnitTest
import spock.lang.Specification


class MasterListServiceSpec extends Specification implements ServiceUnitTest<MasterListService>{

    Closure doWithSpring() {{->
        metricRegistry(MetricRegistry)
    }}

    def setup() {
        grailsApplication.config.lists.items.cacheSpec = 'maximumSize=0' // disable cache for tests
        service.init()
    }

    def 'getMasterList always trims names'() {
        given:
        service.webService = Stub(WebService)
        service.webService.get(_) >> [
                status: 200,
                resp: [
                        [name: ' a '],
                        [name: null],
                        [name: 'b'],
                        [name: ' c'],
                        [name: 'd ']
                ]
        ]
        def opus = new Opus(masterListUid: 'test')

        when:
        def results = service.getMasterList(opus)

        then:

        results.each {
            it?.name?.trim() == it?.name
        }

    }
}
