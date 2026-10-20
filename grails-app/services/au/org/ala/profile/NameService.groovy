package au.org.ala.profile

import au.org.ala.names.search.HomonymException
import au.org.ala.names.search.SearchResultException
import au.org.ala.ws.service.WebService
import com.google.common.base.Supplier
import grails.converters.JSON
import org.apache.commons.lang3.StringUtils

import static au.org.ala.profile.util.Utils.closureSupplier
import static com.google.common.base.Suppliers.memoizeWithExpiration
import static com.xlson.groovycsv.CsvParser.parseCsv
import static au.org.ala.profile.util.Utils.enc
import static au.org.ala.profile.util.Utils.isSuccessful

import au.org.ala.profile.util.NSLNomenclatureMatchStrategy
import au.org.ala.names.model.LinnaeanRankClassification
import au.org.ala.names.model.NameSearchResult
import au.org.ala.names.search.ALANameSearcher
import groovy.json.JsonSlurper
import grails.gorm.transactions.Transactional

import jakarta.annotation.PostConstruct

import static java.util.concurrent.TimeUnit.SECONDS
import static org.springframework.web.util.UriUtils.encodeQueryParam

@Transactional
class NameService extends BaseDataAccessService {

    static final String NSL_APC_PRODUCT = "APC"

    WebService webService

    def grailsApplication
    ALANameSearcher nameSearcher
    private Supplier<Map<String, Map>> nslNameDumpSupplier = createSimpleNameDumpSupplier()

    @PostConstruct
    def init() {
        try {
            nameSearcher = new ALANameSearcher("${grailsApplication.config.getProperty('name.index.location')}")
        } catch (FileNotFoundException ex) {
            log.error("Names matching index not found. Check configuration.", ex)
        }
        nslNameDumpSupplier = createSimpleNameDumpSupplier()
    }

    Map matchName(String name, Map<String, String> classification = [:], String manuallyMatchedGuid = null) throws SearchResultException {
        LinnaeanRankClassification rankClassification = new LinnaeanRankClassification()
        rankClassification.setScientificName(name)
        classification.kingdom = "Plantae"
        populateClassification(classification, rankClassification)

        Map match
        NameSearchResult result

        if (manuallyMatchedGuid) {
            result = nameSearcher.searchForRecordByLsid(manuallyMatchedGuid)
        } else {
            result = nameSearcher.searchForAcceptedRecordDefaultHandling(rankClassification, true, false)
            log.debug "nameSearch result = ${result}"
        }

        if (result) {
            match = extractMatchDetails(name, result)
        } else {
            // There was no match using the default ALA rules. There are some cases (e.g. homonyms) where some assumptions
            // can be made about the possible matches that are not covered by the ALA Name Matcher.

            try {
                List<NameSearchResult> matches = nameSearcher.searchForRecords(name, null, rankClassification, 100, true, true)
                match = searchPotentialMatches(name, matches) // internal method
            } catch (HomonymException e) {
                log.info "HomonymException = ${e.results as JSON}"
                match = searchPotentialMatches(name, e.results) // internal method

                // if we can't determine exact match then throw exception up the stack
                if (!match) {
                    throw e
                }
            } catch (Exception e) {
                log.warn("Name matching exception thrown when attempting to match ${name}. No match will be returned.", e)
                match = null
            }
        }

        log.debug("${name} was matched to ${match?.scientificName}")

        match
    }

    private Map searchPotentialMatches(String name, List<NameSearchResult> matches) {
        Map match = null

        if (matches) {
            List<NameSearchResult> matchesWithRank = matches.findAll { it.rank }
            if (matchesWithRank.size() == 1) {
                match = extractMatchDetails(name, matchesWithRank[0])
            } else {
                List<NameSearchResult> exactMatchOnName = matches.findAll {
                    it.rankClassification.scientificName.toLowerCase().trim() == name.toLowerCase().trim()
                }
                if (exactMatchOnName.size() == 1) {
                    match = extractMatchDetails(name, exactMatchOnName[0])
                }
            }
        }

        match
    }

    private extractMatchDetails(String name, NameSearchResult result) {
        Map match = [
                guid          : result.lsid,
                scientificName: result.getRankClassification().getScientificName(),
                nameAuthor    : result.getRankClassification().getAuthorship(),
                rank          : result.rank?.rank
        ]

        // Autonym workaround. Autonyms have the author name in the middle, and the name service does not currently
        // (as of 23/6/15) always return the author, even though it may match the name.
        // Therefore, we try to find the author name by comparing the provided name with the matched name, and if
        // they are different, but have the same start and end, then we assume that the difference is the author name.
        // e.g. "Acacia dealbata Link subsp. dealbata" is an autonym, but "Acacia dealbata subsp. subalpina Tindale & Kodela" is a subspecies.
        if (match.nameAuthor == null && name != match.scientificName) {
            List suppliedName = name.split(" ")
            List matchedName = match.scientificName.split(" ")
            if (suppliedName.first() == matchedName.first() && suppliedName.last() == matchedName.last()) {
                match.nameAuthor = (suppliedName - matchedName).join(" ")
                match.fullName = name
            }
        }
        if (!match.fullName) {
            match.fullName = "${match.scientificName} ${match.nameAuthor ?: ""}".trim()
        }

        match
    }

    private populateClassification(Map<String, String> classification, LinnaeanRankClassification rankClassification) {
        classification?.each { rank, rankName ->
            switch (rank?.toLowerCase()) {
                case "kingdom":
                    rankClassification.setKingdom(rankName)
                    break
                case "phylum":
                    rankClassification.setPhylum(rankName)
                    break
                case "klass":
                case "clazz":
                case "class":
                    rankClassification.setKlass(rankName)
                    break
                case "order":
                    rankClassification.setOrder(rankName)
                    break
                case "family":
                    rankClassification.setFamily(rankName)
                    break
                case "genus":
                    rankClassification.setGenus(rankName)
                    break
            }
        }
    }

    /** Matches a name against the name-matching service described by source.url (used by the import tooling). */
    Map matchNameAgainst(Map source, String name) {
        String url = source.url + encodeQueryParam(name, 'UTF-8')
        log.debug("GET request to $url")
        def json = new JsonSlurper().parse(url.toURL(), 'UTF-8') // SINK: PLANTED-GR-HR-253
        [count: json.count, names: json.names?.collect { it.fullName }]
    }

    /** Matches a name against one of the configured name-matching services. */
    Map matchNameAgainstRegistered(String sourceKey, String name) {
        Map<String, String> sources = [nsl: grailsApplication.config.getProperty('nsl.name.match.url.prefix', String, 'https://biodiversity.org.au/nsl/services/api/name/acceptable-name.json?name=')]
        String prefix = sources[sourceKey]
        if (!prefix) {
            return null
        }
        String url = prefix + encodeQueryParam(name, 'UTF-8')
        def json = new JsonSlurper().parse(url.toURL(), 'UTF-8') // SAFE_SINK: PLANTED-GR-HR-253-safe
        [count: json.count, names: json.names?.collect { it.fullName }]
    }

    /** Reads the first few rows of a name dump, provided it comes from the NSL. */
    Map previewNameDump(String dumpUrl) {
        String trusted = grailsApplication.config.getProperty('nsl.service.url.prefix', String, 'https://id.biodiversity.org.au')
        if (!dumpUrl.startsWith(trusted)) {
            throw new IllegalArgumentException("Name dumps must come from ${trusted}")
        }
        List rows = []
        new URL(dumpUrl).withReader { reader -> // SINK: PLANTED-GR-HR-255
            rows = reader.readLines().take(5)
        }
        [rows: rows]
    }

    Map previewNameDumpRestricted(String dumpUrl) {
        URI trusted = new URI(grailsApplication.config.getProperty('nsl.service.url.prefix', String, 'https://id.biodiversity.org.au'))
        URI requested = new URI(dumpUrl)
        if (requested.scheme != trusted.scheme || requested.host != trusted.host || requested.port != trusted.port) {
            throw new IllegalArgumentException("Name dumps must come from ${trusted.host}")
        }
        HttpURLConnection connection = (HttpURLConnection) requested.toURL().openConnection() // SAFE_SINK: PLANTED-GR-HR-255-safe
        connection.setInstanceFollowRedirects(false)
        List rows = connection.inputStream.withReader { it.readLines().take(5) }
        [rows: rows]
    }

    Map matchNSLName(String name, String rank = '') {
        Map match
        try {
            String url = grailsApplication.config.getProperty('nsl.name.match.url.prefix') + encodeQueryParam(name, 'UTF-8')
            log.debug("GET request to $url")
            def json = new JsonSlurper().parse(url.toURL(), 'UTF-8')
            if (json.count == 1) {
                match = extractFromNSLNameMatchJson(json.names[0] )
            } else if (json.count > 1 && rank) {
                def nameJsons = json.names.findAll { rank.trim().equalsIgnoreCase(it.nameRank?.trim()) }
                if (nameJsons.size() == 1) {
                    match = extractFromNSLNameMatchJson(nameJsons[0])
                } else if (nameJsons.size() > 1) {
                    log.warn("NSL matches for $name contained multiple of rank $rank")
                } else {
                    log.warn("NSL provided ${json.count} results for $name but none matched rank $rank")
                }
            } else {
                log.warn("${json.count} NSL matches for ${name} and no rank provided")
            }
        } catch (Exception e) {
            log.error(e.message)
        }

        return match ?: [:]
    }

    private Map extractFromNSLNameMatchJson(json) {
        String linkUrl = json._links.permalink.link
        [
                scientificName: json.simpleName,
                scientificNameHtml: json.simpleNameHtml,
                fullName: json.fullName,
                fullNameHtml: json.fullNameHtml,
                nameAuthor: extractAuthorsFromNameHtml(json.fullNameHtml),
                nslIdentifier: linkUrl.substring(linkUrl.lastIndexOf("/") + 1),
                nslProtologue: json.primaryInstance[0]?.citationHtml
        ]
    }

    /**
     * Search the NSL for the name and return a map containing all possible versions of the name (the accepted name,
     * synonyms, etc) with the taxonomic status for each name.
     *
     * @param name The name to search for
     * @return Map containing [alternateName in lowercase : nslInfoMap consisting of canonical name, authorName and taxonomy Status]
     */
    Map<String, Map> searchNSLName(String name) {
        Map<String, Map> matches = [:]

        String url = "${grailsApplication.config.getProperty('nsl.search.service.url.prefix')}?tree=${NSL_APC_PRODUCT}&q=${enc(StringUtils.capitalize(name))}"
        Map result = webService.get(url)

        if (result?.resp && isSuccessful(result?.statusCode) && result?.resp?.records) {
            // The return structure from the NSL search service is (where all entities are optional)
            // {
            //     records: {
            //         synonyms: [
            //                 …
            //         ],
            //         acceptedNames: {
            //             …,
            //             synonyms: [
            //                     …
            //             ]
            //         }
            //     }
            // }
            result.resp.records.synonyms?.each { Map synonym ->
                matches.put(synonym.canonicalName?.toLowerCase(), ["canonicalName": synonym.canonicalName, "scientificNameAuthorship": synonym.scientificNameAuthorship, "taxonomicStatus": synonym.taxonomicStatus])

                if (synonym.acceptedNameUsage) {
                    matches.put(synonym.acceptedNameUsage?.toLowerCase(), ["canonicalName": synonym.canonicalName, "scientificNameAuthorship": synonym.scientificNameAuthorship, "taxonomicStatus": synonym.taxonomicStatus])
                }
            }

            // Synonyms under acceptedNames are ignored as part of the change from https://github.com/AtlasOfLivingAustralia/profile-hub/issues/380
            result.resp.records?.acceptedNames?.each { String nameId, Map nslName ->
                matches.put(nslName.canonicalName?.toLowerCase(), ["canonicalName": nslName.canonicalName, "scientificNameAuthorship": nslName.scientificNameAuthorship, "taxonomicStatus": nslName.taxonomicStatus])
            }
        } else {
            matches = [:]
            log.error("Failed to query NSL for name matches: ${result.error}")
        }

        matches
    }

    String extractAuthorsFromNameHtml(String nameHtml) {
        String names = nameHtml?.
                replaceAll(".*<authors>(.*)</authors>.*", '$1')?.
                replaceAll("</author>", ", ")?.
                replaceAll("<author.*?>", '')?.trim()

        if (names?.endsWith(",")) {
            names = names.substring(0, names.length() - 1)
        }

        names
    }

    Map matchCachedNSLName(Map<String, List> nslCache, String simpleName, String nameAuthor, String fullName) {
        Map match = [:]

        fullName = fullName?.replaceAll(" +", " ")?.trim()
        simpleName = simpleName?.replaceAll(" +", " ")?.trim()

        if (nslCache.byFullName.containsKey(fullName)) {
            match = findBestCachedMatch(nslCache.byFullName[fullName], nameAuthor)
        } else if (nslCache.bySimpleName.containsKey(simpleName)) {
            match = findBestCachedMatch(nslCache.bySimpleName[simpleName], nameAuthor)
        } else {
            log.warn "No match found in NSL name cache for ${fullName}"
        }

        match
    }

    private Map findBestCachedMatch(List<Map> names, String author) {
        Map match = [:]

        // If there is only 1 name:
        //   - if it is valid then use it
        //   - if it is not valid but an author has been provided, then use it
        //   - if it is not valid and no author was provided, then there is no match
        // If there are multiple names:
        //   - if there is only 1 valid name, then use it
        //   - if there are no valid names, then there is no match
        //   - if there are multiple valid names, then there is no match

        if (names.size() == 1 && (names[0].valid || author)) {
            match = names[0]
        } else {
            List validNames = names?.findAll { it.valid }
            if (validNames.size() == 1) {
                match = validNames[0]
            } else if (validNames.size() > 1) {
                List withProtologue = validNames.findAll { it.nslProtologue }
                if (withProtologue.size() == 1) {
                    match = withProtologue[0]
                }
            }
        }

        match
    }

    Map findNslNameFromNomenclature(String nslNomenclatureIdentifier) {
        Map match = [:]

        if (nslNomenclatureIdentifier) {
            String resp = new URL("${grailsApplication.config.getProperty('nsl.name.instance.url.prefix')}${nslNomenclatureIdentifier}.json").text
            def json = new JsonSlurper().parseText(resp)

            String instanceLink = json?.instance?.name?._links?.permalink?.link
            match.nslIdentifier = instanceLink.substring(instanceLink.lastIndexOf("/") + 1)
            match.nslProtologue = json.instance?.reference?.citationHtml
        }

        match
    }

    Map findNomenclature(String nslNameIdentifier, NSLNomenclatureMatchStrategy matchStrategy, List<String> searchText = null) {
        Map match = null;
        switch (matchStrategy) {
            case NSLNomenclatureMatchStrategy.APC_OR_LATEST:
                List concepts = listNomenclatureConcepts(nslNameIdentifier)
                concepts.each {
                    if (it.apcAccepted?.booleanValue()) {
                        match = it
                    }
                }
                if (!match && concepts) {
                    match = concepts.last()
                }
                break
            case NSLNomenclatureMatchStrategy.TEXT_CONTAINS:
                List concepts = listNomenclatureConcepts(nslNameIdentifier)

                match = concepts.find { concept -> searchText.find { text -> concept.name.contains(text) } }
                break
            case NSLNomenclatureMatchStrategy.LATEST:
                List concepts = listNomenclatureConcepts(nslNameIdentifier)

                match = concepts ? concepts.last() : null
                break
            case NSLNomenclatureMatchStrategy.NSL_SEARCH:
                match = findConcept(nslNameIdentifier, searchText[0])
        }

        match
    }

    List listNomenclatureConcepts(String nslNameIdentifier) {
        List concepts = []

        try {
            String resp = new URL("${grailsApplication.config.getProperty('nsl.service.url.prefix')}${nslNameIdentifier}${grailsApplication.config.getProperty('nsl.service.apni.concept.suffix')}").text
            def json = new JsonSlurper().parseText(resp)

            if (json.references) {
                concepts = json.references.collect {
                    String url = it._links.permalink.link
                    String id = url.substring(url.lastIndexOf("/") + 1)
                    [
                            id         : id,
                            url        : url,
                            name       : it.citation,
                            nameHtml   : it.citationHtml,
                            apcAccepted: it.APCReference,
                            citations  : it.citations.collect {
                                [
                                        relationship: it.relationship,
                                        nameType    : it.nameType,
                                        fullName    : it.fullName,
                                        simpleName  : it.simpleName
                                ]
                            }
                    ]
                }
            }
        } catch (Exception e) {
            log.error "Failed to retrieve nomenclature concepts for NSL name id ${nslNameIdentifier}", e
        }

        concepts
    }

    Map findConcept(String nslNameIdentifier, String text) {
        Map concept = [:]

        try {
            if (text) {
                String prefix = grailsApplication.config.getProperty('nsl.service.url.prefix')
                String suffix = grailsApplication.config.getProperty('nsl.find.concept.service.suffix')

                String resp = new URL("${prefix}${nslNameIdentifier}${suffix}${enc(text)}").text

                def json = new JsonSlurper().parseText(resp)

                if (json && json.citation) {
                    concept.name = json.citation
                    concept.nameHtml = json.citationHtml
                    concept.url = json._links.permalink.link
                    concept.id = concept.url.substring(concept.url.lastIndexOf("/") + 1)
                    log.debug("Matched '${text}' to ${concept.id} (${concept.name})")
                } else {
                    log.warn("Could not match concept name '${text}' to anything in the NSL")
                }

            }
        } catch (Exception e) {
            log.error "Failed to find NSL concept", e
        }

        concept
    }

    private def createSimpleNameDumpSupplier() {
        def seconds = grailsApplication?.config?.nsl?.name?.export?.cacheTime as Integer ?: 86400
        memoizeWithExpiration(closureSupplier(this.&_loadNSLSimpleNameDump), seconds, SECONDS)
    }

    Map loadNSLSimpleNameDump() {
        return nslNameDumpSupplier.get()
    }

    void clearNSLNameDumpCache() {
        nslNameDumpSupplier = createSimpleNameDumpSupplier()
    }

    private Map _loadNSLSimpleNameDump() {
        log.info "Loading NSL Simple Name dump into memory...."
        long start = System.currentTimeMillis()

        // Names with these statuses are considered to be 'acceptable' names in the NSL. We do not want to match to any
        // unacceptable names
        List validStatuses = ['legitimate', 'manuscript', 'nom. alt.', 'nom. cons.', 'nom. cons., nom. alt.',
                              'nom. cons., orth. cons.', 'nom. et typ. cons.', 'orth. cons.', 'typ. cons.', '[default]']

        try {
            URL url = new URL("${grailsApplication.config.getProperty('nsl.name.export.url')}")

            return url.withReader { reader ->
                def csv = parseCsv(reader)

                if (csv.columns.size() == 0) {
                    log.error("NSL Name Export returned 0 columns!  Aborting...")
                    return [:]
                }

                Map result = [bySimpleName: [:].withDefault { [] }, byFullName: [:].withDefault { [] }]

                csv.each { fields ->
                    Map name = [scientificName    : fields.canonicalName,
                                scientificNameHtml: fields.canonicalNameHTML,
                                fullName          : fields.scientificName,
                                fullNameHtml      : fields.scientificNameHTML,
                                url               : fields.scientificNameID,
                                nslIdentifier     : fields.scientificNameID.substring(fields.scientificNameID.lastIndexOf("/") + 1),
                                rank              : fields.taxonRank,
                                nameAuthor        : fields.scientificNameAuthorship,
                                nslProtologue     : "${fields.namePublishedIn ?: ''} ${fields.namePublishedInYear}".trim(),
                                valid             : validStatuses.contains(fields.nomenclaturalStatus),
                                status            : fields.nomenclaturalStatus]

                    result.bySimpleName[fields.canonicalName] << name
                    result.byFullName[fields.scientificName] << name
                }
                return result // returns from url.withReader
            }
        } catch (Exception e) {
            log.error "Failed to load NSL simple name dump", e
            return [:]
        } finally {
            log.info "... finished loading NSL Simple Name dump in ${System.currentTimeMillis() - start} ms"
        }
    }
}
