package au.org.ala.profile

import org.springframework.web.context.request.RequestContextHolder

class AuditInterceptor {

    UserService userService
    public static final String REQUEST_USER_DETAILS_KEY = 'request.user.details'

    AuditInterceptor () {
        matchAll()
    }

    boolean before () {
        // userId is set from either the request param userId or failing that it tries to get it from
        // the UserPrincipal
        def userId = request.getHeader(grailsApplication.config.getProperty('app.http.header.userId'))

        // decode the ALA Auth cookie value to avoid double-encoding in the ala-auth plugin (yet to be fixed)
        request.getCookies()?.each {
            if (it.getName() == "ALA-Auth") {
                it.setValue(URLDecoder.decode(it.getValue(), "UTF-8"))
            }
        }
        if (userId) {
            def userDetails = userService.setCurrentUser(userId)
            if (userDetails) {
                // We set the current user details in the request scope because
                // the 'afterView' hook can be called prior to the actual rendering (despite the name)
                // and the thread local can get clobbered before it is actually required.
                // Consumers who have access to the request can simply extract current user details
                // from there rather than use the service.
                request.setAttribute(REQUEST_USER_DETAILS_KEY, userDetails)
            }
        }

        true
    }

    boolean  after() {
        true
    }

    void afterView () {
        userService.clearCurrentUser()
    }
}
