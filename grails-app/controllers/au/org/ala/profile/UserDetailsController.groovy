package au.org.ala.profile

import au.ala.org.ws.security.RequireApiKey
import au.org.ala.web.UserDetails
import grails.converters.JSON

class UserDetailsController extends BaseController {

    UserService userService
    OpusService opusService

    @RequireApiKey
    def getUserDetails() {
        if (!params.userId && !userService.getCurrentUserDetails()) {
            badRequest "No userId has been provided, and there is no authenticated user"
        } else {
            String userId = params.userId ?: userService.getCurrentUserDetails().userId
            UserDetails userDetails = userService.getUserForUserId(userId)

            Map user = [
                    userId: userId,
                    email: userDetails.userName,
                    displayName: userDetails.displayName,
                    roles: opusService.getAuthoritiesForUser(userId, getOpus()?.uuid).collect { it.role.name() }
            ]

            render user as JSON
        }
    }
}
