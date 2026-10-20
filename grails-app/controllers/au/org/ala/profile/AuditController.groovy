package au.org.ala.profile

import au.ala.org.ws.security.RequireApiKey
import grails.converters.JSON
import grails.gorm.PagedResultList

@RequireApiKey
class AuditController {

    static Integer DEFAULT_PAGE_SIZE = 100
    static Integer DEFAULT_OFFSET = 0

    def index() {}

    def auditTrailForObject() {
        log.debug("Finding audit trail for entity ${params.entityId}")
        Integer offset = params.getInt('offset', DEFAULT_OFFSET)
        Integer max = params.getInt('max', DEFAULT_PAGE_SIZE)

        PagedResultList results = AuditMessage.createCriteria().list(max: max, offset:offset) {
            eq('entityId', params.entityId)
            order("date", "desc")
        }
        Map response = [total: results.totalCount, items: results]
        render response as JSON
    }

    def auditTrailForUser() {
        def results = AuditMessage.findAllByUserId(params.userId, [sort: "date", order: "desc", offset: 0])
        render results as JSON
    }
}
