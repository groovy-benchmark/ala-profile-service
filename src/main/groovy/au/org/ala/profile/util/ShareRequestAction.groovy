package au.org.ala.profile.util

enum ShareRequestAction {
    ACCEPT(ShareRequestStatus.ACCEPTED),
    DECLINE(ShareRequestStatus.REJECTED),
    REQUEST(ShareRequestStatus.REJECTED),
    REVOKE(ShareRequestStatus.REVOKED)

    ShareRequestStatus resultingStatus

    ShareRequestAction(ShareRequestStatus resultingStatus) {
        this.resultingStatus = resultingStatus
    }

}