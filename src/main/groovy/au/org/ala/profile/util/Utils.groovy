package au.org.ala.profile.util

import com.google.common.base.Charsets
import com.google.common.base.Supplier
import com.google.common.cache.CacheLoader
import com.google.common.escape.Escapers
import com.google.common.net.UrlEscapers
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FirstParam
import org.apache.http.client.utils.URLEncodedUtils

import static com.google.common.net.UrlEscapers.urlPathSegmentEscaper
import static org.apache.commons.lang3.StringEscapeUtils.unescapeHtml4
import static org.apache.http.HttpStatus.SC_OK

class Utils {

    static final double DEFAULT_MAP_LATITUDE = -27
    static final double DEFAULT_MAP_LONGITUDE = 133.6
    static final double DEFAULT_MAP_ZOOM = 3
    static final double DEFAULT_MAP_MAX_ZOOM = 20
    static final double DEFAULT_MAP_MAX_AUTO_ZOOM = 15
    static final String DEFAULT_MAP_POINT_COLOUR = "FF9900"
    static final String DEFAULT_MAP_BASE_LAYER = "https://{s}.tiles.mapbox.com/v4/{id}/{z}/{x}/{y}.png?access_token={token}"
    static final String UUID_REGEX = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;

    static final String CHAR_ENCODING = "utf-8"

    /**
     * Whitelists safe characters to prevent possible regex injection. Escapes regex characters (like .) that are whitelisted
     *
     * @param regex The regex string to make safe
     * @return A safe version of the string or an empty string
     */
    static sanitizeRegex(String regex) {
        String clean = ""

        if (regex) {
            clean = regex?.replaceAll(/[^0-9a-zA-Z'\.",\-\(\)& ]/, "")?.replaceAll(/\.+/, ".")?.replaceAll(/([\.\-\(\)])/, "\\\\\$1")
        }

        clean
    }

    static cleanupText(str) {
        if (str) {
            str = unescapeHtml4(str)
            // preserve line breaks as new lines, remove all other html
            str = str.replaceAll(/<p\/?>|<br\/?>/, "\n").replaceAll(/<.+?>/, "").replaceAll(" +", " ").trim()
        }
        return str
    }

    static String encPath(String str) {
        urlPathSegmentEscaper().escape(str)
    }

    static String enc(String str) {
        URLEncoder.encode(str, CHAR_ENCODING)
    }

    static String decode(String str) {
        URLDecoder.decode(str, CHAR_ENCODING)
    }

    static boolean isUuid(String str) {
        str =~ UUID_REGEX
    }

    static String getFileExtension(String filename) {
        filename ? filename.substring(filename.lastIndexOf(".") + 1) : null
    }

    static String getCCLicenceIcon(String ccLicence) {
        String icon

        switch (ccLicence) {
            case "Creative Commons Attribution":
                icon = "https://licensebuttons.net/l/by/4.0/80x15.png"
                break
            case "Creative Commons Attribution-Noncommercial":
                icon = "https://licensebuttons.net/l/by-nc/3.0/80x15.png"
                break
            case "Creative Commons Attribution-Share Alike":
                icon = "https://licensebuttons.net/l/by-sa/3.0/80x15.png"
                break
            case "Creative Commons Attribution-Noncommercial-Share Alike":
                icon = "https://licensebuttons.net/l/by-nc-sa/3.0/80x15.png"
                break
            default:
                icon = "https://licensebuttons.net/l/by/4.0/80x15.png"
        }

        icon
    }

    /** Returns true for HTTP status codes from 200 to 299 */
    static  boolean isSuccessful(int statusCode) {
        return statusCode >= SC_OK && statusCode <= 299
    }

    static String createQueryString(Map parsedQuery) {
        parsedQuery.collectMany { k, vs -> vs.collect { v -> "${enc(k)}=${enc(v)}" } }.join('&')
    }

    static Map<String, List<String>> parseQueryString(String query) {
        def result = URLEncodedUtils.parse(query, Charsets.UTF_8)
        return result.groupBy { nvp -> nvp.name }.collectEntries { [(it.key): it.value*.value ] }
    }

    static boolean toBooleanWithDefault(value, boolean defaultValue) {
        Boolean b = value?.toBoolean()
        return b != null ? b : defaultValue
    }

    static <T> Supplier<T> closureSupplier(Closure<T> closure) {
        return closure as Supplier<T>
    }

    static <K, T> CacheLoader<K, T> closureCacheLoader(Class<K> argType, @ClosureParams(FirstParam.FirstGenericType) Closure<T> listClosure) {
        // groovy compiler doesn't like adding type parameters to the CacheLoader :(
        return new CacheLoader() {
            @Override
            Object load(Object key) throws Exception {
                listClosure(key)
            }
        }
    }
}
