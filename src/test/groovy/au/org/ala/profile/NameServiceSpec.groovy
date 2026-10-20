package au.org.ala.profile

import grails.testing.gorm.DataTest
import grails.testing.services.ServiceUnitTest
import spock.lang.Specification

class NameServiceSpec extends Specification implements ServiceUnitTest<NameService>, DataTest{

    def "extractAuthorsFromNameHtml should extract the author name from the html"() {
        when:
        String author = service.extractAuthorsFromNameHtml("<scientific><name id='89770'><scientific><name id='72805'><element><i>Poa</i></element></name></scientific> <element><i>fax</i></element> <authors><author id='8322' title='Willis, J.H. &amp; Court, A.B.'>J.H.Willis & Court</author></authors></name></scientific>")

        then:
        author == "J.H.Willis & Court"
    }

    def "extractAuthorsFromNameHtml should extract multiple author names from the html"() {
        when:
        String author = service.extractAuthorsFromNameHtml("<scientific><name id='89770'><scientific><name id='72805'><element><i>Poa</i></element></name></scientific> <element><i>fax</i></element> <authors><author id='8322' title='Willis, J.H. &amp; Court, A.B.'>J.H.Willis & Court</author><author id='8322' title='Willis, J.H. &amp; Court, A.B.'>Smith</author></authors></name></scientific>")

        then:
        author == "J.H.Willis & Court, Smith"
    }
}
