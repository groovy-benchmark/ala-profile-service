package au.org.ala.profile

import au.org.ala.profile.security.RequiresAccessToken
import grails.testing.gorm.DataTest
import grails.testing.web.interceptor.InterceptorUnitTest
import org.apache.http.HttpStatus
import org.grails.web.util.GrailsApplicationAttributes
import spock.lang.Specification

//import grails.test.mixin.TestFor
//import grails.test.mixin.TestMixin
//import grails.test.mixin.domain.DomainClassUnitTestMixin
//import grails.test.mixin.support.GrailsUnitTestMixin
//import grails.test.mixin.web.FiltersUnitTestMixin

import spock.lang.Unroll

@Unroll
class AccessTokenInterceptorSpec extends Specification implements InterceptorUnitTest<AccessTokenInterceptor>, DataTest {

    void "Test AccessTokenInterceptor matching"() {
        setup:
        grailsApplication.addArtefact("Controller", AnnotatedClassController)

        when:"A request matches the interceptor"
        withRequest(controller:"annotatedClass")

        then:"The interceptor does match"
        interceptor.doesMatch()
    }

    def "requests without an access token should be rejected when the controller class is annotated with RequiresAccessToken"() {
        setup:
        // need to do this because grailsApplication.controllerClasses is empty in the filter when run from the unit test
        // unless we manually add the dummy controller class used in this test
        AnnotatedClassController controller = new AnnotatedClassController()
        grailsApplication.addArtefact("Controller", AnnotatedClassController)

        mockDomain (Opus)
        Opus.findOrCreateWhere(uuid: 'abc', accessToken: "1234", glossary: new Glossary(), title: 'abc', dataResourceUid: 'abc').save(flush: true)

        when: "there is no access token in the header"
        params.opusId = "abc"
        request.setAttribute(GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE, 'annotatedClass')
        request.setAttribute(GrailsApplicationAttributes.ACTION_NAME_ATTRIBUTE, 'action1')
        withInterceptors(controller: "annotatedClass", action: "action1") {
            controller.action1()
        }

        then: "the request should be rejected"
        response.status == HttpStatus.SC_FORBIDDEN
    }

    def "requests with an invalid access token should be rejected when the controller class is annotated with RequiresAccessToken"() {
        setup:
        // need to do this because grailsApplication.controllerClasses is empty in the filter when run from the unit test
        // unless we manually add the dummy controller class used in this test
        grailsApplication.addArtefact("Controller", AnnotatedClassController)
        AnnotatedClassController controller = new AnnotatedClassController()

        mockDomain (Opus)
        Opus.findOrCreateWhere(uuid: 'abc', accessToken: "1234", glossary: new Glossary(), title: 'abc', dataResourceUid: 'abc').save(flush: true)

        when: "there is an invalid access token in the header"
        params.opusId = "abc"
        request.addHeader("ACCESS-TOKEN", "garbage")
        request.setAttribute(GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE, 'annotatedClass')
        request.setAttribute(GrailsApplicationAttributes.ACTION_NAME_ATTRIBUTE, 'action1')

        withInterceptors(controller: "annotatedClass", action: "action1") {
            controller.action1()
        }

        then: "the request should be rejected"
        response.status == HttpStatus.SC_FORBIDDEN
    }

    def "requests with a valid access token should be accepted when the controller class is annotated with RequiresAccessToken"() {
        setup:
        // need to do this because grailsApplication.controllerClasses is empty in the filter when run from the unit test
        // unless we manually add the dummy controller class used in this test
        grailsApplication.addArtefact("Controller", AnnotatedClassController)
        AnnotatedClassController controller = new AnnotatedClassController()

        mockDomain (Opus)
        Opus.findOrCreateWhere(uuid: 'abc', accessToken: "1234", glossary: new Glossary(), title: 'abc', dataResourceUid: 'abc').save(flush: true)

        when: "there is a valid access token in the header"
        params.opusId = "abc"
        request.addHeader("ACCESS-TOKEN", "1234")
        request.setAttribute(GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE, 'annotatedClass')
        request.setAttribute(GrailsApplicationAttributes.ACTION_NAME_ATTRIBUTE, 'action1')

        withInterceptors(controller: "annotatedClass", action: "action1") {
            controller.action1()
        }

        then: "the request should be accepted"
        response.status == HttpStatus.SC_OK
    }

    def "requests to a method annotated with RequiresAccessToken with a valid token should be accepted"() {
        setup:
        // need to do this because grailsApplication.controllerClasses is empty in the filter when run from the unit test
        // unless we manually add the dummy controller class used in this test
        grailsApplication.addArtefact("Controller", AnnotatedMethodController)
        AnnotatedMethodController controller = new AnnotatedMethodController()

        mockDomain (Opus)
        Opus.findOrCreateWhere(uuid: 'abc', accessToken: "1234", glossary: new Glossary(), title: 'abc', dataResourceUid: 'abc').save(flush: true)

        when: "there is a valid access token in the header"
        params.opusId = "abc"
        request.addHeader("ACCESS-TOKEN", "1234")
        request.setAttribute(GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE, 'annotatedClass')
        request.setAttribute(GrailsApplicationAttributes.ACTION_NAME_ATTRIBUTE, 'action1')

        withInterceptors(controller: "annotatedMethod", action: "securedAction") {
            controller.securedAction()
        }

        then: "the request should be accepted"
        response.status == HttpStatus.SC_OK
    }

    def "requests to a method annotated with RequiresAccessToken with an invalid token should be rejected"() {
        setup:
        // need to do this because grailsApplication.controllerClasses is empty in the filter when run from the unit test
        // unless we manually add the dummy controller class used in this test
        grailsApplication.addArtefact("Controller", AnnotatedMethodController)
        AnnotatedMethodController controller = new AnnotatedMethodController()

        mockDomain (Opus)
        Opus.findOrCreateWhere(uuid: 'abc', accessToken: "1234", glossary: new Glossary(), title: 'abc', dataResourceUid: 'abc').save(flush: true)

        when: "there is a valid access token in the header"
        params.opusId = "abc"
        request.addHeader("ACCESS-TOKEN", "garbage")
        request.setAttribute(GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE, 'annotatedMethod')
        request.setAttribute(GrailsApplicationAttributes.ACTION_NAME_ATTRIBUTE, 'securedAction')

        withInterceptors(controller: "annotatedMethod", action: "securedAction") {
            controller.securedAction()
        }

        then: "the request should be accepted"
        response.status == HttpStatus.SC_FORBIDDEN
    }

    def "requests to a method not annotated with RequiresAccessToken with an invalid token should be access"() {
        setup:
        // need to do this because grailsApplication.controllerClasses is empty in the filter when run from the unit test
        // unless we manually add the dummy controller class used in this test
        grailsApplication.addArtefact("Controller", AnnotatedMethodController)
        AnnotatedMethodController controller = new AnnotatedMethodController()

        mockDomain (Opus)
        Opus.findOrCreateWhere(uuid: 'abc', accessToken: "1234", glossary: new Glossary(), title: 'abc', dataResourceUid: 'abc').save(flush: true)

        when: "there is a valid access token in the header"
        params.opusId = "abc"
        request.addHeader("ACCESS-TOKEN", "garbage")
        request.setAttribute(GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE, 'annotatedMethod')
        request.setAttribute(GrailsApplicationAttributes.ACTION_NAME_ATTRIBUTE, 'publicAction')

        withInterceptors(controller: "annotatedMethod", action: "publicAction") {
            controller.publicAction()
        }

        then: "the request should be accepted"
        response.status == HttpStatus.SC_OK
    }

}

@RequiresAccessToken
class AnnotatedClassController {
    def action1() {
    }
}
class AnnotatedMethodController {
    @RequiresAccessToken
    def securedAction() {
    }
    def publicAction() {
    }
}