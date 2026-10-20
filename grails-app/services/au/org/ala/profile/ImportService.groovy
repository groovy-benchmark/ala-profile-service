package au.org.ala.profile


import au.org.ala.profile.util.NSLNomenclatureMatchStrategy
import au.org.ala.profile.util.Utils
import au.org.ala.ws.service.WebService
import com.google.common.collect.Sets
import grails.converters.JSON
import grails.plugin.dropwizard.metrics.meters.Metered
import grails.plugin.dropwizard.metrics.timers.Timed
import grails.plugins.elasticsearch.ElasticSearchService
import groovy.transform.ToString
import groovyx.gpars.actor.Actors
import groovyx.gpars.actor.DefaultActor
import groovyx.gpars.dataflow.Promise
import org.apache.http.HttpStatus
import org.apache.http.entity.ContentType

//import grails.plugins.elasticsearch.ElasticSearchService

import org.springframework.scheduling.annotation.Async

import jakarta.annotation.PreDestroy
import java.text.SimpleDateFormat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Matcher
import java.util.regex.Pattern

import static groovyx.gpars.GParsPool.withPool

class ImportService extends BaseDataAccessService {

    static final int IMPORT_THREAD_POOL_SIZE = 10

    ProfileService profileService
    NameService nameService
    VocabService vocabService

    ElasticSearchService elasticSearchService
    WebService webService
    def grailsApplication
    def masterListService

    Term getOrCreateTerm(String vocabId, String name) {
        Vocab vocab = Vocab.findByUuid(vocabId)
        Term term = Term.findByNameAndVocab(name, vocab)
        if (!term) {
            term = new Term(name: name, vocab: vocab)
            term.save(flush: true)
        }
        term
    }

    /**
     * Profile import is a two-pass operation:
     * 1.) Use multiple threads to loop through the provided data set to find all values that should be unique (e.g. attribute terms, contributors, etc)
     * 1.1) Using a single thread, store them in the database and return a map of references.
     * 2.) Use multiple threads to loop through the provided data set again to process each individual record, retrieving references to unique values from the maps from pass 1.
     *
     * Querying data from the database, and writing records if they don't exist, is the slowest part of the import process.
     * Executing these 'get or create' actions concurrently results in race conditions and duplicated data.
     * Synchronising the methods resolves this issue, but dramatically increases the execution time of the import.
     *
     * The two-pass approach ensures no duplicate records are created, and does not significantly impact performance.
     *
     * @param importId a unique identifier assigned to this import process, to be returned the service consumer so they can request the import report when the import completes
     * @param opusId
     * @param profilesJson
     */
    @Async
    void importProfiles(String importId, String opusId, profilesJson) {
        Opus.withSession {
            File importReportFile = new File("${grailsApplication.config.getProperty('temp.file.directory')}/${importId}.json.inprogress")
            importReportFile.createNewFile()

            Opus opus = Opus.findByUuid(opusId);

            Map<String, Map> profileResults = new ConcurrentHashMap<>()
            Map<String, String> termConcurrentListVocabMapping = new ConcurrentHashMap<>()
            Map<String, String> vocabIdTermNameTermIdMapping = new ConcurrentHashMap<>()
            Date startTime = new Date()

            AtomicInteger success = new AtomicInteger(0)
            AtomicInteger index = new AtomicInteger(0)
            int reportInterval = Math.max(0.05 * profilesJson.size(), 5.0) // log at 5% intervals

            Map uniqueValues = findAndStoreUniqueValues(opus, profilesJson)
            Map<String, Term> vocab = uniqueValues.vocab
            Map<String, Contributor> contributors = uniqueValues.contributors

            boolean enableNSLMatching = true

            Map nslNamesCached = enableNSLMatching ? nameService.loadNSLSimpleNameDump() : [:]

            if (!nslNamesCached) {
                log.warn("NSL Simple Name cache failed - using live service lookup instead")
            }

            log.info "Importing profiles ..."
            withPool(IMPORT_THREAD_POOL_SIZE) {
                profilesJson.eachParallel {
                    Opus.withSession { session ->
                        def profileJSON = it
                        Map results = [errors: [], warnings: []]
                        try {
                            def currentIndex = index.incrementAndGet()

                            if (!profileJSON.scientificName) {
                                results.errors << "Failed to import row ${currentIndex}, does not have a scientific name"
                            } else {
                                Map matchedName = nameService.matchName(profileJSON.scientificName?.trim(), profileJSON.classification ?: [:])

                                String scientificName = matchedName?.scientificName?.trim() ?: profileJSON.scientificName.trim()
                                String fullName = matchedName?.fullName?.trim() ?: scientificName.trim()
                                String nameAuthor = matchedName?.nameAuthor?.trim() ?: null
                                String guid = matchedName?.guid ?: null

                                if (!matchedName) {
                                    results.warnings << "ALA - No matching name for ${profileJSON.scientificName} in the ALA"
                                } else if (!profileJSON.scientificName.equalsIgnoreCase(matchedName.scientificName) && !profileJSON.scientificName.equalsIgnoreCase(fullName)) {
                                    results.warnings << "ALA - Provided with name ${profileJSON.scientificName}, but was matched with name ${fullName} in the ALA. Using provided name."
                                    scientificName = profileJSON.scientificName
                                    fullName = profileJSON.fullName
                                    nameAuthor = profileJSON.nameAuthor
                                }

                                Profile profile = Profile.findByScientificNameAndOpus(scientificName, opus)
                                if (profile && profile.profileStatus != Profile.STATUS_EMPTY) {
                                    log.info("Profile already exists in this opus for scientific name ${scientificName}")
                                    results.errors << "'${profileJSON.scientificName}' already exists (provided as ${profileJSON.scientificName}, matched as ${fullName})"
                                } else {
                                    if (!profile) {
                                        profile = new Profile(scientificName: scientificName, nameAuthor: nameAuthor, opus: opus, guid: guid, attributes: [], links: [], bhlLinks: [], bibliography: [], profileStatus: Profile.STATUS_LEGACY);
                                    } else {
                                        profile.scientificName = scientificName
                                        profile.nameAuthor = nameAuthor
                                        profile.opus = opus
                                        profile.guid = guid
                                        profile.profileStatus = Profile.STATUS_LEGACY
                                    }
                                    profile.fullName = fullName

                                    if (matchedName) {
                                        profile.matchedName = new Name(matchedName)
                                    }

                                    if (profile.guid) {
                                        profileService.populateTaxonHierarchy(profile)
                                    }

                                    if (profileJSON.nslNameIdentifier) {
                                        profile.nslNameIdentifier = profileJSON.nslNameIdentifier
                                    } else if (enableNSLMatching) {
                                        Map nslMatch
                                        boolean matchedByName
                                        if (profileJSON.nslNomenclatureIdentifier) {
                                            nslMatch = nameService.findNslNameFromNomenclature(profileJSON.nslNomenclatureIdentifier)
                                            matchedByName = false
                                        } else if (nslNamesCached) {
                                            nslMatch = nameService.matchCachedNSLName(nslNamesCached, profileJSON.scientificName, profileJSON.nameAuthor, profileJSON.fullName)
                                            matchedByName = true
                                        } else {
                                            nslMatch = nameService.matchNSLName(profileJSON.scientificName, profile.rank)
                                            matchedByName = true
                                        }

                                        if (nslMatch) {
                                            profile.nslNameIdentifier = nslMatch.nslIdentifier
                                            profile.nslProtologue = nslMatch.nslProtologue
                                            if (!profile.nameAuthor) {
                                                profile.nameAuthor = nslMatch.nameAuthor
                                            }

                                            if (matchedByName && !profileJSON.scientificName.equalsIgnoreCase(nslMatch.scientificName) && !profileJSON.scientificName.equalsIgnoreCase(nslMatch.fullName)) {
                                                results.warnings << "NSL - Provided with name ${profileJSON.scientificName}, but was matched with name ${nslMatch.fullName} in the NSL. Using provided name."
                                            }
                                        } else {
                                            results.warnings << "NSL - No matching name for ${profileJSON.scientificName} in the NSL."
                                        }
                                    }

                                    if (profileJSON.nslNomenclatureIdentifier) {
                                        profile.nslNomenclatureIdentifier = profileJSON.nslNomenclatureIdentifier
                                    } else if (profile.nslNameIdentifier && enableNSLMatching && profileJSON.nslNomenclatureMatchStrategy) {
                                        NSLNomenclatureMatchStrategy matchStrategy = NSLNomenclatureMatchStrategy.valueOf(profileJSON.nslNomenclatureMatchStrategy) ?: NSLNomenclatureMatchStrategy.DEFAULT
                                        if (matchStrategy != NSLNomenclatureMatchStrategy.NONE) {
                                            Map nomenclature = nameService.findNomenclature(profile.nslNameIdentifier, matchStrategy, profileJSON.nslNomenclatureMatchData)

                                            if (!nomenclature) {
                                                results.warnings << "No matching nomenclature was found for '${profileJSON.nslNomenclatureMatchData}'"
                                            }

                                            profile.nslNomenclatureIdentifier = nomenclature?.id
                                        }
                                    }

                                    profileJSON.links.each {
                                        if (it) {
                                            profile.links << createLink(it, contributors)
                                        }
                                    }

                                    profileJSON.bhl.each {
                                        if (it) {
                                            profile.bhlLinks << createLink(it, contributors)
                                        }
                                    }

                                    profileJSON.bibliography.each {
                                        if (it) {
                                            profile.bibliography << new Bibliography(uuid: UUID.randomUUID().toString(), text: it, order: profile.bibliography.size())
                                        }
                                    }

                                    Set<String> contributorNames = []
                                    profileJSON.attributes.each {
                                        if (it.title && it.text) {
                                            Term term = vocab.get(it.title.trim())

                                            if (it.text?.trim()) {
                                                Attribute attribute = new Attribute(title: term)
                                                attribute.uuid = UUID.randomUUID().toString()
                                                getContent(it, attribute, term, termConcurrentListVocabMapping, vocabIdTermNameTermIdMapping)

                                                if (it.creators) {
                                                    attribute.creators = []
                                                    it.creators.each {
                                                        String name = cleanName(it)
                                                        if (name) {
                                                            Contributor contrib = contributors[name]
                                                            if (contrib) {
                                                                attribute.creators << contrib
                                                                contributorNames << contrib.name
                                                            } else {
                                                                log.warn("Missing contributor for name '${name}'")
                                                            }
                                                        }
                                                    }
                                                }

                                                if (it.editors) {
                                                    attribute.editors = []
                                                    it.editors.each {
                                                        String name = cleanName(it)
                                                        if (name) {
                                                            attribute.editors << contributors[name]
                                                        }
                                                    }
                                                }

                                                attribute.profile = profile
                                                profile.attributes << attribute
                                            }
                                        }
                                    }

                                    if (profileJSON.images) {
                                        profileJSON.images.each {
                                            uploadImage(scientificName, opus.dataResourceUid, it)
                                        }
                                    }

                                    if (profileJSON.authorship) {
                                        profile.authorship = profileJSON.authorship.collect {
                                            Term term = getOrCreateTerm(opus.authorshipVocabUuid, it.category)
                                            new Authorship(category: term, text: it.text)
                                        }
                                    } else {
                                        Term term = getOrCreateTerm(opus.authorshipVocabUuid, "Author")
                                        profile.authorship = [new Authorship(category: term, text: contributorNames.join(", "))]
                                    }

                                    profile.save(flush: true)

                                    if (profile.errors.allErrors.size() > 0) {
                                        log.error("Failed to save ${profile}")
                                        profile.errors.each { log.error(profileJSON?.toString()) }
                                        results.errors << "Failed to save profile ${profile.errors.allErrors.get(0)}"
                                    } else {
                                        def currentSuccess = success.incrementAndGet()
                                        if (currentIndex % reportInterval == 0) {
                                            log.debug("Saved ${currentSuccess} of ${profilesJson.size()}")
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            log.error "An exception occurred while importing the record ${profileJSON}", e
                            results.errors << "Failed to create profile ${profileJSON.scientificName}: ${e.getMessage()}"
                        }

                        results.status = results.errors ? "error" : results.warnings ? "warning" : "success"
                        profileResults << [(profileJSON.scientificName): results]
                    }
                }
            }
            log.debug "${success} of ${profilesJson.size()} records imported"

            updateTermWithConstraintListVocab(termConcurrentListVocabMapping)
            Date finishTime = new Date()

            importReportFile << ([started: new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(startTime),
                                  finished: new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(finishTime),
                                  profiles: profileResults] as JSON).toString()

            importReportFile.renameTo("${grailsApplication.config.getProperty('temp.file.directory')}/${importId}.json")
        }
    }

    def updateTermWithConstraintListVocab(Map termConcurrentListVocabMapping){
        termConcurrentListVocabMapping.each { termId, vocabId ->
            Term term = Term.findByUuid(termId)
            term?.constraintListVocab = vocabId
            term.save(flush: true)
        }
    }

    def getContent(Map body, Attribute attribute, Term title, Map termConcurrentListVocabMapping, Map vocabIdTermNameTermIdMapping) {
        switch (title.dataType) {
            case 'number':
                getNumbers(body.text, attribute)
                break;
            case 'range':
                getRange(body.text, attribute)
                break;
            case 'list':
            case 'singleselect':
                synchronized(this){
                    getTerms(body.text, attribute, title, termConcurrentListVocabMapping, vocabIdTermNameTermIdMapping)
                }
                break;
            case 'text':
            default:
                attribute.text = body.stripHtml?.booleanValue() ? Utils.cleanupText(body.text) : body.text
                break
        }

        attribute
    }

    /**
     * Parses the given text and sets the numbers for the given attribute.
     * The method splits the text by comma, and then converts each element
     * to a double, and save it in the attribute.numbers field.
     *
     * @param text the text to be parsed
     * @param attribute the attribute whose numbers will be set
     */

    def getNumbers (String text, Attribute attribute) {
        try {
            if (text) {
                attribute.numbers = text.split(',').collect {
                    Double.parseDouble(it)
                }
            }
        } catch (NumberFormatException nfe) {
            log.error("Cannot convert text (${text}) to double", nfe)
        }

        attribute
    }

    /**
     * Parses the given text and sets the number range for the given attribute.
     * The method looks for patterns of the form "x", "x - y" or "[x - y)" in the text,
     * where x and y are numbers, uses the first group of digits as the start of the range
     * and the second group as the end of the range, and '[' or ']' is used to signify inclusive range.
     * It also takes into account whether the range is inclusive or exclusive based on the
     * enclosing brackets. If enclosing bracket is missing, range is inclusive by default.
     *
     * @param text the text to be parsed
     * @param attribute the attribute whose number range will be set
     */
    def getRange(String text, Attribute attribute) {
        Pattern pattern = Pattern.compile("\\s*(\\d+)\\s*")
        Matcher matcher = pattern.matcher(text)
        if(matcher.find()) {
            attribute.numberRange = new NumberRange(from: Double.parseDouble(matcher.group(1)), to: Double.parseDouble(matcher.group(1)))
        }

        pattern = Pattern.compile("(\\d+)\\s*-\\s*(\\d+)")
        matcher = pattern.matcher(text)
        if(matcher.find()) {
            attribute.numberRange = new NumberRange(from: Double.parseDouble(matcher.group(1)), to: Double.parseDouble(matcher.group(2)))
        }

        pattern = Pattern.compile("([\\[\\(]*)(\\d+)\\s*-\\s*(\\d+)([\\]\\)]*)")
        matcher = pattern.matcher(text)
        if(matcher.find()) {
            Map range = [from: Double.parseDouble(matcher.group(2)), to: Double.parseDouble(matcher.group(3))]
            range.fromInclusive = matcher.group(1) == '[' ? true : matcher.group(1) == '(' ? false : true
            range.toInclusive = matcher.group(4) == ']' ? true : matcher.group(4) == ')' ? false : true
            attribute.numberRange = new NumberRange(range)
        }
    }

    def getTerms(String text, Attribute attribute, Term title, Map termConcurrentListVocabMapping, Map vocabIdTermNameTermIdMapping) {
        if(text) {
            List parts = text.split("\\s*,\\s*")
            attribute.constraintList = parts?.collect { String part ->
                if(!termConcurrentListVocabMapping[title.uuid] && !title.constraintListVocab) {
                    Map result = vocabService.updateVocab(null, [name: "Contraint list for ${title.name}"])
                    termConcurrentListVocabMapping[title.uuid] = result.vocab.uuid
                }

                String vocabId = termConcurrentListVocabMapping[title.uuid] ?: title.constraintListVocab
                part = part.trim()
                if (!vocabIdTermNameTermIdMapping[vocabId + part]) {
                    Term term = getOrCreateTerm(vocabId, part)
                    vocabIdTermNameTermIdMapping[vocabId + part] = term.uuid
                }

                vocabIdTermNameTermIdMapping[vocabId+part]
            }
        }
    }

    def uploadImage(String scientificName, String dataResourceId, Map metadata) {
        Map payload = [scientificName: scientificName, multimedia: [metadata]]

        try {
            Map resp = webService.post("${grailsApplication.config.getProperty('image.upload.url')}dr3", payload,
                    [apiKey: grailsApplication.config.getProperty('image.upload.apiKey')],
                    ContentType.APPLICATION_JSON, false, false, ["User-Agent": "groovy"])

            if (resp.statusCode != HttpStatus.SC_OK && resp.statusCode != HttpStatus.SC_CREATED) {
                log.warn("Failed to upload image: ${resp.error ?: resp.resp}")
            }
        }
        catch (Exception e) {
            log.warn("Failed to upload image: ", e)
        }
    }

    Map findAndStoreUniqueValues(Opus opus, profilesJson) {
        Set<String> uniqueTerms = [] as ConcurrentSkipListSet
        Set<String> uniqueContributors = [] as ConcurrentSkipListSet

        log.info "Retrieving unique data..."
        withPool(IMPORT_THREAD_POOL_SIZE) {
            profilesJson.eachParallel {
                it.attributes.each { attr ->
                    if (attr.title) {
                        uniqueTerms << attr.title.trim()
                    }

                    attr.editors?.each { name ->
                        uniqueContributors << cleanName(name)
                    }
                    attr.creators?.each { name ->
                        uniqueContributors << cleanName(name)
                    }
                }
            }
        }

        log.info "Storing unique vocabulary (${uniqueTerms.size()} terms) ..."
        Map<String, Term> vocab = [:]
        uniqueTerms.each {
            vocab[it] = getOrCreateTerm(opus.attributeVocabUuid, it)
        }

        log.info "Storing unique contributors (${uniqueContributors.size()} names) ..."
        Map<String, Contributor> contributors = [:]
        uniqueContributors.each {
            contributors[it] = getOrCreateContributor(it, opus.dataResourceUid)
        }

        [vocab: vocab, contributors: contributors]
    }

    private static cleanName(String name) {
        name.replaceAll("\\(.*\\)", "").replaceAll(" +", " ").trim()
    }

    Contributor getOrCreateContributor(String name, String opusDataResourceId) {
        Contributor contributor = Contributor.findByName(name)
        if (!contributor) {
            contributor = new Contributor(name: name, uuid: UUID.randomUUID().toString(), dataResourceUid: opusDataResourceId)
        }
        contributor
    }

    Link createLink(data, contributors) {
        Link link = new Link(data)
        link.uuid = UUID.randomUUID().toString()

        if (data.creators) {
            link.creators = []
            data.creators.each {
                String name = cleanName(it)
                if (name) {
                    link.creators << contributors[name]
                }
            }
        }

        link
    }

    final static int EMPTY_PROFILE_VERSION = 3

    //Adds a basic level of monitoring to the master list synchronisation so the UI can be more responsive
    // without interfering with the current execution model implemented already in this class
    /**
     * Return true if there is currently a synchronisation underway for the current opus
     * @param uuid The opus UUID
     * @return true if the current Opus masterlist is being synchronised
     */
    boolean isMasterListSyncing(String uuid) {
        runningActors.contains(uuid)
    }

    /**
     * Syncronise the master list for the given Opus UUID and wait for the result before returning.
     *
     * @param uuid The opus UUID
     * @param forceStubRegeneration true to delete all empty profiles instead of only missing / old version ones
     * @return A SyncActorResponse
     */
    SyncResponse syncroniseMasterList(String uuid, boolean forceStubRegeneration = false) {
        def result = syncActor.sendAndWait(new SyncActorMessage.SyncMessage(uuid: uuid, forceRegenStubs: forceStubRegeneration))
        switch (result) {
            case SyncResponse.SyncFailed:
                log.error("syncroniseMasterList for $uuid failed", result.exception)
                break
        }
        return result
    }

    /**
     * Syncronise the master list for the given Opus UUID and return a GPars Actor MessageStream.
     *
     * @param uuid The opus UUID
     * @param forceStubRegeneration true to delete all empty profiles instead of only missing / old version ones
     * @return The GPars Promise that the result will be delivered to.
     */
    Promise<SyncResponse> asyncSyncroniseMasterList(String uuid, boolean forceStubRegeneration = false) {
        def promise = syncActor.sendAndPromise(new SyncActorMessage.SyncMessage(uuid: uuid, forceRegenStubs: forceStubRegeneration))
        return promise.then { result ->
            switch (result) {
                case SyncResponse.SyncFailed:
                    log.error("asyncSyncroniseMasterList for $uuid failed", result.exception)
                    break
            }
            result
        }
    }

    @Timed(value = 'Timed: sync master list', useClassPrefix = true)
    @Metered(value = 'Metered: sync master list', useClassPrefix = true)
    private def syncMasterList(Opus collection, boolean forceStubRegeneration = false) {

        def colId = collection.shortName ?: collection.uuid

        def masterList = collection.masterListUid ? masterListService.getMasterList(collection) : Collections.<Map<String, String>>emptyList()

        log.info("Syncing Master List for ${colId} with ${masterList.size()} entries")
        boolean enableNSLMatching = true

        Map nslNamesCached = enableNSLMatching && masterList ? nameService.loadNSLSimpleNameDump() : [:]

        if (!nslNamesCached && masterList) {
            log.warn("NSL Simple Name cache failed - using live service lookup instead")
        }

        def allResults = [:].withDefault { [:] }
        log.info "Syncing Master List, starting pooled operations ..."
        withPool(IMPORT_THREAD_POOL_SIZE, logException("syncMasterList(${colId})")) { pool ->
            def matches = masterList.collectParallel {
                [match: nameService.matchName(it.name), listItem: it]
            }
            log.info("Sync Master List for ${colId} matched ${matches.inject(0) { s, m -> s + (m.match ? 1 : 0) } } records")

            def matchesMap = matches.collectEntries { [(it.listItem.name?.toLowerCase()): it] }

            def namesSet = masterList.collect { it.name?.toLowerCase() }.toSet()
            def names = namesSet.toList()

            // Load all profiles from mongo so that we can run a GORM delete on them, this should trigger
            // an equivalent delete in elasticsearch as well.  TODO find a more efficient way of doing this.

            Profile.withStatelessSession { session ->
                // Use a stateless session to reduce memory pressure since we're just going to delete these
                // As a side benefit, this means that integration tests won't return the deleted objects afterwards
                // TODO does the elastic search entry get deleted as well?

                // ids = delete from profile where opus_id = ? and profile_status = ? and scientificName not in ? returning id
                // elasticsearchService.delete(ids.collect { new Profile(id: id) }) !

                def toDelete = Profile.withCriteria {
                    eq('opus', collection.id)
                    eq('profileStatus', Profile.STATUS_EMPTY)
                    not { 'in'('scientificNameLower', names) } // need to use a withCriteria because the GORM dynamic finder ScientificNameNotInList doesn't apply the not part
                }

                Profile.deleteAll(toDelete)
                elasticSearchService.unindex(toDelete)
                def deleteCount = toDelete.size()

                log.info("Sync Master List for ${colId} deleted ${deleteCount} existing empty records which do not exist on master list")
                toDelete.clear()

                if (forceStubRegeneration) {
                    toDelete = Profile.findAllByOpusAndProfileStatus(collection, Profile.STATUS_EMPTY)
                } else {
                    toDelete = Profile.findAllByOpusAndProfileStatusAndEmptyProfileVersionNotEqual(collection, Profile.STATUS_EMPTY, EMPTY_PROFILE_VERSION)
                }

                Profile.deleteAll(toDelete)
                elasticSearchService.unindex(toDelete)
                deleteCount = toDelete.size()
                log.info("Sync Master List for ${colId} deleted ${deleteCount} ${forceStubRegeneration ? '' : 'outdated '}empty records")
                toDelete.clear()
                session.flush()
                session.clear()
            }


            def existingProfileNames = Profile.withCriteria {
                eq('opus', collection.id)
                'in'('scientificNameLower', names)

                projections {
                    property('scientificNameLower')
                }
            }
            log.info("Sync Master List for ${colId} found ${existingProfileNames?.size() ?: 0} existing non-empty records")

            def newNames = Sets.difference(namesSet, existingProfileNames.toSet())

            def inserts = newNames.collectParallel {
                def match = matchesMap[it]
                Map results = [errors: [], warnings: []]
                def emptyProfile = generateEmptyProfile(collection, match.listItem, match.match, nslNamesCached, results)
                if (results.errors || results.warnings) {
                    log.info("Sync Master List for ${colId} Name: ${match.listItem.name} Results: $results")
                }
                allResults[match.listItem.name] = results

                emptyProfile
            }

            inserts*.save(flush: true, validate: true)


            def ids = inserts*.id.findAll { it }
            def errors = inserts.findAll { it.hasErrors() }
            if (errors) {
                log.warn("Some validation errors while creating empty profiles")
                errors.each { profile ->
                    log.warn("${profile.scientificName} has errors:")
                    profile.errors.allErrors.each { error ->
                        log.warn(error?.toString())
                    }
                    allResults[profile.scientificName].validationErrors = profile.errors.allErrors
                }
            }
            log.info("Sync Master List for ${colId} inserted ${ids.size()} empty records")
        }

        log.info("Sync Master List for $colId completed.")
        return allResults
    }

    def generateEmptyProfile(opus, listItem, matchedName, nslNamesCached, results) {
//        String scientificName = listItem.name.trim()
        String scientificName = matchedName?.scientificName?.trim() ?: listItem.name.trim()
        String fullName = matchedName?.fullName?.trim() ?: matchedName?.scientificName?.trim() ?: listItem.name.trim()
        String nameAuthor = matchedName?.nameAuthor?.trim() ?: null
        String guid = matchedName?.guid ?: null

        if (!matchedName) {
            results.warnings << "ALA - No matching name for ${listItem.name} in the ALA"
        } else if (!listItem.name.equalsIgnoreCase(matchedName.scientificName) && !listItem.name.equalsIgnoreCase(fullName)) {
            results.warnings << "ALA - Provided with name ${listItem.name}, but was matched with name ${fullName} in the ALA."
        }

        // pregenerate uuids so that we can create drafts before saving.

        // Always assign the name as the list name, because the list is the source of truth
        Profile profile = new Profile(uuid: UUID.randomUUID().toString(), scientificName: listItem.name, nameAuthor: nameAuthor, fullName: fullName, opus: opus, guid: guid, attributes: [], links: [], bhlLinks: [], bibliography: [], profileStatus: Profile.STATUS_EMPTY, emptyProfileVersion: EMPTY_PROFILE_VERSION)

        if (matchedName) {
            profile.matchedName = new Name(matchedName)
        }

        if (profile.guid) {
            profileService.populateTaxonHierarchy(profile)
        }

        if (listItem.nslNameIdentifier) {
            profile.nslNameIdentifier = listItem.nslNameIdentifier
        } else {
            Map nslMatch
            boolean matchedByName
            if (listItem.nslNomenclatureIdentifier) {
                nslMatch = nameService.findNslNameFromNomenclature(listItem.nslNomenclatureIdentifier)
                matchedByName = false
            } else if (nslNamesCached) {
                nslMatch = nameService.matchCachedNSLName(nslNamesCached, scientificName, nameAuthor, fullName)
                matchedByName = true
            } else {
                nslMatch = nameService.matchNSLName(scientificName, profile.rank)
                matchedByName = true
            }

            if (nslMatch) {
                profile.nslNameIdentifier = nslMatch.nslIdentifier
                profile.nslProtologue = nslMatch.nslProtologue
                if (!profile.nameAuthor) {
                    profile.nameAuthor = nslMatch.nameAuthor
                }

                if (matchedByName && !listItem.name.equalsIgnoreCase(nslMatch.scientificName) && !listItem.name.equalsIgnoreCase(nslMatch.fullName)) {
                    results.warnings << "NSL - Provided with name ${listItem.name}, but was matched with name ${nslMatch.fullName} in the NSL. Using provided name."
                }
            } else {
                results.warnings << "NSL - No matching name for ${listItem.name} in the NSL using scientific name: $scientificName, author: $nameAuthor, fullname: $fullName"
            }
        }

        if (profile.nslNameIdentifier) {
            NSLNomenclatureMatchStrategy matchStrategy = NSLNomenclatureMatchStrategy.DEFAULT
            Map nomenclature = nameService.findNomenclature(profile.nslNameIdentifier, matchStrategy)

            if (!nomenclature) {
                results.warnings << "No matching nomenclature was found with NSL Name ID '${profile.nslNameIdentifier}' using $matchStrategy match strategy"
            }

            profile.nslNomenclatureIdentifier = nomenclature?.id
        }

        return profile
    }

    def createUuidSyncActor = { uuid ->
        Actors.actor {
            loop {
                react { message ->
                    def result = syncMasterListForActor(uuid, message.forceRegenStubs)
                    replyIfExists(result)
                }
            }
        }
    }

    Map<String, DefaultActor> actors = [:].withDefault(createUuidSyncActor)

    // Actor for an opus is either running or not
    Set<String> runningActors = [] as Set

    // This actor responds to SyncActorMessages and creates a new actor per Opus UUID on
    // demand when a SyncMessage arrives, the response of the child actor will then be
    // routed to the sender of the original message
    def syncActor = Actors.actor {

        loop {
            react { message ->
                switch (message) {
                    case SyncActorMessage.SyncMessage:
                        if (message.uuid) {
                            DefaultActor actor = actors[message.uuid]
                            actor.send(message, sender) // send the result to the sender
                        }
                        break
                    case SyncActorMessage.ShutdownMessage:
                        actors.values().each { it?.stop() }*.join()
                        stop()
                        replyIfExists('shutdown')
                        break
                    default:
                        log.warn("Unexpected message: $message")
                }
            }
        }
    }



    /* sealed */ static class SyncActorMessage {
        @ToString final static class ShutdownMessage extends SyncActorMessage { final static INSTANCE = new ShutdownMessage() }
        @ToString final static class SyncMessage extends SyncActorMessage {
            String uuid
            boolean forceRegenStubs = false
        }
    }

    /* sealed */ static class SyncResponse {
        @ToString final static class OpusNotFound extends SyncResponse { String uuid }
        @ToString final static class SyncFailed extends SyncResponse { Exception exception }
        @ToString final static class SyncComplete extends SyncResponse { Map results }
    }

    private SyncResponse syncMasterListForActor(String opusUuid, boolean forceStubRegeneration = false) {
        try {
            runningActors.add(opusUuid)
            return Opus.withNewSession { session ->
                def opus = Opus.findByUuid(opusUuid)
                if (opus) {
                    def results = syncMasterList(opus, forceStubRegeneration)
                    return new SyncResponse.SyncComplete(results: results)
                } else {
                    return new SyncResponse.OpusNotFound(uuid: opusUuid)
                }
            }
        } catch (Exception e) {
            return new SyncResponse.SyncFailed(exception: e)
        } finally {
            runningActors.remove(opusUuid)
        }
    }

    @PreDestroy
    void shutdownActors() {
        log.info("Sending shutdown message to Import Service actors")
        syncActor.send(SyncActorMessage.ShutdownMessage.INSTANCE)
        syncActor.join()
        log.info("Import Service actors shutdown")
    }

    private Thread.UncaughtExceptionHandler logException(String prefix) {
//        return UncaughtEx
        new Thread.UncaughtExceptionHandler() {
            @Override
            void uncaughtException(Thread t, Throwable e) {
                log.error("Uncaught exception for $prefix", e)
            }
        }
    }
}
