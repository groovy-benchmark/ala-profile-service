package au.org.ala.profile

import au.org.ala.ws.service.WebService
import grails.converters.JSON
import org.apache.http.entity.ContentType

import java.text.SimpleDateFormat

/**
 * Access to Fathom Analytics API for a collection.
 */
class AnalyticsService {

    // All time (the oldest date available is 2005-01-01, which suits us just fine)
    public static final String ALL_TIME = "2005-01-01"

    def grailsApplication
    WebService webService

    boolean enabled() {
        return !"${grailsApplication.config.getProperty('fathom.api.url')}".isEmpty() &&
            !"${grailsApplication.config.getProperty('fathom.site-id')}".isEmpty() &&
            !"${grailsApplication.config.getProperty('fathom.hostname')}".isEmpty() &&
            !"${grailsApplication.config.getProperty('fathom.api.key')}".isEmpty()
    }

    /**
     * Query Fathom Analytics for the most interesting summary statistics on the opus
     * @param opus the opus of interest
     * @return a map containing the keys {@code pagePath}, {@code sessions} and
     * {@code pageviews} representing those statistics for the most viewed profile in this
     * opus; <em>falsy</em> values are provided when no data could be found.
     */
    Map analyticsByOpus(Opus opus) {
        Map ret = [:]

        Calendar from = Calendar.getInstance()
        from.set(Calendar.DAY_OF_MONTH, 1)
        from.set(Calendar.HOUR_OF_DAY, 0)
        from.set(Calendar.MINUTE, 0)
        from.set(Calendar.SECOND, 0)
        from.set(Calendar.MILLISECOND, 0)

        String fromStr = from ? new SimpleDateFormat("yyyy-MM-dd").format(from.getTime()) : ALL_TIME

        ret.mostViewedProfile = queryMostViewedProfile(opus, ALL_TIME)
        ret.totalVisitorCount = queryVisitorCount(opus, ALL_TIME)
        ret.totalDownloadCount = queryDownloadCount(opus, ALL_TIME)
        ret.monthlyVisitorCount = queryVisitorCount(opus, fromStr)
        ret.monthlyDownloadCount = queryDownloadCount(opus, fromStr)

        return ret
    }

    /**
     * Perform a Fathom Analytics query for the most viewed profile.
     * @return the data, with caveats as noted in {@link #extractData(List)}
     */
    private Map queryMostViewedProfile(Opus opus, String from) {
        String url = grailsApplication.config.getProperty('fathom.api.url')
        String siteId = grailsApplication.config.getProperty('fathom.site-id')
        String hostName = grailsApplication.config.getProperty('fathom.hostname')
        String pathPattern = "/opus/${opus.shortName ?: opus.uuid}/profile/%"

        Map query = [
                entity:"pageview",
                entity_id: siteId,
                aggregates: "pageviews",
                field_grouping: "pathname",
                filters: ([["property": "hostname", "operator": "is", "value": hostName],["property": "pathname", "operator": "is like", "value": pathPattern]] as JSON).toString(),
                date_from: from,
                sort_by: "pageviews:desc"
        ]
        Map result = webService.get(url, query, ContentType.APPLICATION_JSON, false, false, getAuthorization())
        extractData(result.resp)
    }

    /**
     * Get authorization header for Fathom Analytics API.
     * @return
     */
    private Map getAuthorization() {
        ["Authorization": "Bearer ${grailsApplication.config.getProperty('fathom.api.key')}"]
    }

    /**
     * Perform a Fathom Analytics query for the number of unique visitors to the opus.
     */
    private Map queryVisitorCount(Opus opus, String from) {
        String url = grailsApplication.config.getProperty('fathom.api.url')
        String siteId = grailsApplication.config.getProperty('fathom.site-id')
        String hostName = grailsApplication.config.getProperty('fathom.hostname')
        String pathPattern = "/opus/${opus.shortName ?: opus.uuid}%"

        Map query = [
                entity: "pageview",
                entity_id: siteId,
                aggregates: "visits",
                filters: ([["property": "hostname", "operator": "is", "value": hostName],["property": "pathname", "operator": "is like", "value": pathPattern]] as JSON).toString(),
                date_from: from
        ]

        Map result = webService.get(url, query, ContentType.APPLICATION_JSON, false, false, getAuthorization())
        extractData(result.resp)
    }

    /**
     * Perform a Fathom Analytics query for the number of hits to PDF download urls (ad-hoc PDFs or publications).
     * @return the data, with caveats as noted in {@link #extractData(List)}
     */
    private Map queryDownloadCount(Opus opus, String from) {
        //  get the number of hits to ad-hoc PDFs generated
        String url = grailsApplication.config.getProperty('fathom.api.url')
        String siteId = grailsApplication.config.getProperty('fathom.site-id')
        String hostName = grailsApplication.config.getProperty('fathom.hostname')
        String pathPattern = "/opus/${opus.shortName ?: opus.uuid}/profile/%/pdf"
        List filters = [["property": "hostname", "operator": "is", "value": hostName],["property": "pathname", "operator": "is like", "value": pathPattern]]

        Map query = [
                entity: "pageview",
                entity_id: siteId,
                aggregates: "pageviews",
                filters: (filters as JSON).toString(),
                date_from: from
        ]


        Map result = webService.get(url, query, ContentType.APPLICATION_JSON, false, false, getAuthorization())
        Map part1 = extractData(result.resp)

        //  get the number of hits to publications in this opus
        filters[1].value = "/opus/${opus.shortName ?: opus.uuid}/profile/%/publication/%/file"
        query.filters = (filters as JSON).toString()
        result = webService.get(url, query, ContentType.APPLICATION_JSON, false, false, getAuthorization())
        Map part2 = extractData(result.resp)
        [pageviews: Long.parseLong(part1.pageviews?:"0") + Long.parseLong(part2.pageviews?:"0")]
    }

    /**
     * Extracts metric and dimension values from the result of a Fathom Analytics query.
     *
     * @param result the query result
     * @return a map of the results containing the name of the metric or dimension as entry key (e.g. "sessions",
     * "pageviews", "pagePath", "users"), and the content of the metric or dimension as entry value;
     * a map is returned with {@code null}s or String "0" as entry value when no data was returned in the query,
     * {@code null} is used when the data type of the column was "STRING" (usually for
     * dimensions), and "0" is used in all other cases (usually for metrics).
     */
    private static Map extractData(List result) {
        Map ret = [:]
        if (result) {
            Map topHit = result?.first()
            if (topHit) {
                return [
                        pagePath: topHit.pathname ?: null,
                        sessions: topHit.pageviews ?: "0",
                        pageviews: topHit.pageviews ?: "0",
                        users: topHit.visits ?: "0"
                ]
            }
        }

        return ret
    }
}
