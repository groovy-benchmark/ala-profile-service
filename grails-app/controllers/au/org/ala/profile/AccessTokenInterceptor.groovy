package au.org.ala.profile

import au.org.ala.profile.security.RequiresAccessToken
import org.apache.http.HttpStatus

class AccessTokenInterceptor {

    static final String ACCESS_TOKEN_HEADER = 'ACCESS-TOKEN'

    AccessTokenInterceptor() {
        matchAll()
    }

    boolean before () {
        log.debug("Access token interceptor")
        def controller = grailsApplication.getArtefactByLogicalPropertyName("Controller", controllerName)
        Class controllerClass = controller?.clazz
        def method = controllerClass?.getMethod(actionName ?: "index", [] as Class[])

        if (controllerClass?.isAnnotationPresent(RequiresAccessToken) || method?.isAnnotationPresent(RequiresAccessToken)) {
            String token = request.getHeader(ACCESS_TOKEN_HEADER)
            String opusId = params.opusId

            if (!token || !opusId || Opus.findByUuid(opusId)?.accessToken != token) {
                log.warn("No valid access token for opus ${opusId} when calling ${controllerName}/${actionName}")
                response.status = HttpStatus.SC_FORBIDDEN
                response.sendError(HttpStatus.SC_FORBIDDEN, "Forbidden")
                return false
            }
        }

        true
    }

    boolean after () {
        true
    }


    void afterView () {
    }
}
