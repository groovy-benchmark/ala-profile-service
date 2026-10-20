package au.org.ala.profile

import com.google.common.cache.CacheBuilder
import com.google.common.cache.LoadingCache
import com.google.common.collect.Sets
import com.google.common.util.concurrent.UncheckedExecutionException
import org.grails.web.util.WebUtils
import grails.plugin.dropwizard.metrics.meters.Metered
import grails.plugin.dropwizard.metrics.timers.Timed

import jakarta.annotation.PostConstruct
import jakarta.servlet.http.HttpServletRequest
import java.util.concurrent.ExecutionException

import static au.org.ala.profile.util.Utils.closureCacheLoader
import static au.org.ala.profile.util.Utils.encPath

class MasterListService {

    static final String MASTER_LIST_OVERRIDE_PARAM = 'florulaOverrideId'
    static final String DEFAULT_CACHE_CONFIG = 'maximumSize=100,expireAfterWrite=1m'

    def grailsApplication
    def webService
    def userService
    def userSettingsService

    // 100 cached lists ought to be enough for anyone
    private LoadingCache<String, List<Map<String,String>>> listCache

    @PostConstruct
    def init() {
        listCache = CacheBuilder.from(grailsApplication.config.getProperty('lists.items.cacheSpec') ?: DEFAULT_CACHE_CONFIG).build(closureCacheLoader(String, this.&_getProfileList))
    }

    /**
     * Return the master list for a given collection or null if no master list is set.
     * @param opus The collection to get the master list for
     * @return A list of objects that probably have name and scientificName properties.
     * @throws ProfileListUnavailableException
     */
    @Timed(value = 'Timed: get master list', useClassPrefix = true)
    @Metered(value = 'Metered: get master list', useClassPrefix = true)
    List<Map<String, String>> getMasterList(Opus opus) throws ProfileListUnavailableException {
        if (!opus.masterListUid) return null
        def listId = opus.masterListUid
        try {
            return getProfileList(listId)
        } catch (e) {
            log.error("Can't get master list for ${opus.shortName}")
            throw e
        }
    }

    private List<Map<String, String>> getProfileList(String listId) throws ProfileListUnavailableException {
        try {
            listCache.get(listId)
        } catch (ExecutionException e) {
            throw e.cause
        } catch (UncheckedExecutionException e) {
            throw e.cause
        }
    }

    private List<Map<String, String>> _getProfileList(String listId) throws ProfileListUnavailableException {
        def baseUrl = grailsApplication.config.getProperty('lists.base.url') ?: 'https://lists.ala.org.au'
        def response = webService.get("$baseUrl/ws/speciesListItems/${encPath(listId)}")
        if (response.statusCode >= 400) {
            log.error("Can't get master list for ${listId}")
            throw new ProfileListUnavailableException()
        } else {
            return response.resp?.each { entry ->
                if (entry.name) entry.name = entry.name.trim()
            }
        }
    }

    /**
     * Preserves null responses (which indicates no filter) when converting from a list
     * to a list of lower case names.
     * @param opus The opus to get lists for
     * @return The list of lower case names or null if no filter applies
     */
    // TODO replace use of this method with extracting the user and override id from all
    // relevant controller actions and service calls
    @Timed(value = 'Timed: get combined lower case names list for user', useClassPrefix = true)
    @Metered(value = 'Metered: get combined lower case names list for user', useClassPrefix = true)
    List<String> getCombinedLowerCaseNamesListForUser(Opus opus) {
        def list = getCombinedListForUser(opus)
        if (list == null) return null
        else return list.collect { it.name.toLowerCase() }
    }

    /**
     * Preserves null responses (which indicates no filter) when converting from a list
     * to a list of just names.
     * @param opus The opus to get lists for
     * @return The list of names or null if no filter applies
     */
    // TODO replace use of this method with extracting the user and override id from all
    // relevant controller actions and service calls
    @Timed(value = 'Timed: get combined names list for user', useClassPrefix = true)
    @Metered(value = 'Metered: get combined names list for user', useClassPrefix = true)
    List<String> getCombinedNamesListForUser(Opus opus) {
        def list = getCombinedListForUser(opus)
        if (list == null) return null
        else return list*.name
    }

    /**
     * Gets a filter list from the combination of an Opus's master list and from the user's
     * florula setting for that opus.
     *
     * @param opus The opus to get lists for
     * @return The combined filter list or null if no filter applies
     */
    // TODO replace use of this method with extracting the user and override id from all
    // relevant controller actions and service calls
    @Timed(value = 'Timed: get combined list for user', useClassPrefix = true)
    @Metered(value = 'Metered: get combined list for user', useClassPrefix = true)
    List<Map<String,String>> getCombinedListForUser(Opus opus) {
        String florulaId = getFlorulaListIdForUser(opus.uuid)
        logUri() // Get an idea which urls this is called from
        return getCombinedListForUser(opus, florulaId)
    }

    String getFlorulaListIdForUser(String opusUuid) {
        def wr = WebUtils.retrieveGrailsWebRequest()
        def request = wr.getRequest()
        getFlorulaListIdForUser(request, opusUuid)
    }

    String getFlorulaListIdForUser(HttpServletRequest request, String opusUuid) {
        String florulaId
        def userid = userService.currentUserDetails?.userId
        if (!userid) {
            try {
                florulaId = request.parameterMap[MASTER_LIST_OVERRIDE_PARAM + '-' + opusUuid]?.first()
            } catch (e) {
                log.error("Not in a request context", e)
                florulaId = null
            }
        } else {
            florulaId = userSettingsService.getUserSettings(userid)?.allFlorulaSettings?.get(opusUuid)?.drUid
        }
        return florulaId
    }

    private void logUri() {
        if (log.isTraceEnabled()) {
            try {
                def request = WebUtils.retrieveGrailsWebRequest().request
                def uri = request.forwardURI
                def method = request.method
                def protocol = request.protocol
                log.trace("!!! GET MASTER LIST FOR USER !!! ($method $uri $protocol)")
            } catch (e) {
                log.trace("!!! GET MASTER LIST FOR USER !!!", new RuntimeException())
            }
        }
    }

    @Timed(value = 'Timed: get combined list for user', useClassPrefix = true)
    @Metered(value = 'Metered: get combined list for user', useClassPrefix = true)
    List<Map<String,String>> getCombinedListForUser(Opus opus, String florulaId) throws ProfileListUnavailableException {
        def florulaListItems = florulaId ? getProfileList(florulaId) : null
        def masterListItems = opus?.masterListUid ?  getProfileList(opus?.masterListUid) : null
        def result
        if (florulaListItems != null && masterListItems != null) {
            def fm = florulaListItems.collectEntries { [(it.name.toLowerCase()): it] }
            def mlm = masterListItems.collectEntries { [(it.name.toLowerCase()): it] }
            def i = Sets.intersection(mlm.keySet(), fm.keySet())
            result = i.collect { fm[it] + mlm[it] } // master list entries win
        } else if (masterListItems != null) {
            result = masterListItems
        } else if (florulaListItems) {
            result = florulaListItems
        } else {
            result = null
        }
        return result
    }

    class ProfileListUnavailableException extends IOException {
        @Override synchronized Throwable fillInStackTrace() {
            return this
        }
    }
}
