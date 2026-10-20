package au.org.ala.profile

import au.org.ala.web.AuthService
import au.org.ala.web.UserDetails

class UserService extends AuthService {
// by default service is not transactional from 3.1 onwards

    def authService

    private static ThreadLocal<UserDetails> _currentUser = new ThreadLocal<UserDetails>()

    def getCurrentUserDisplayName() {
        UserDetails currentUser = _currentUser.get()
        currentUser ? currentUser.displayName : ""
    }

    def getCurrentUserDetails() {
        _currentUser.get();
    }

    /**
     * This method gets called by a filter at the beginning of the request (if a userId paramter is on the URL)
     * It sets the user details in a thread local for extraction by the audit service.
     * @param userId
     */
    def setCurrentUser(String userId) {

        def userDetails = authService.getUserForUserId(userId)
        if (userDetails) {
            _currentUser.set(userDetails)
        } else {
            log.warn("Failed to lookup user details for user id ${userId}! No details set on thread local.")
        }
        userDetails
    }

    def clearCurrentUser() {
        if (_currentUser) {
            _currentUser.remove()
        }
    }

    @Override
    String getEmail() {
        _currentUser?.get()?.getEmail()
    }

    @Override
    String getUserName() {
        _currentUser?.get()?.getUserName()
    }

    @Override
    String getUserId() {
        _currentUser?.get()?.getUserId()
    }

    @Override
    String getDisplayName() {
        _currentUser?.get()?.getDisplayName()
    }

    @Override
    String getFirstName() {
        _currentUser?.get()?.getFirstName()
    }

    @Override
    String getLastName() {
        _currentUser?.get()?.getLastName()
    }

    @Override
    boolean userInRole(String role) {
        _currentUser?.get()?.hasRole(role)
    }

    @Override
    UserDetails userDetails() {
        _currentUser?.get()
    }
}