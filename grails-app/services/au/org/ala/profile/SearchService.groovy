package au.org.ala.profile

import au.org.ala.names.search.SearchResultException
import au.org.ala.profile.util.ProfileSortOption
import au.org.ala.profile.util.SearchOptions
import au.org.ala.profile.util.Utils
import au.org.ala.ws.service.WebService
import com.mongodb.BasicDBObject
import com.mongodb.client.MapReduceIterable
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoClient
import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Filters
import com.mongodb.client.model.MapReduceAction
import grails.plugins.elasticsearch.ElasticSearchService
import groovy.json.JsonSlurper
import org.apache.http.entity.ContentType
import org.apache.lucene.search.join.ScoreMode
import org.elasticsearch.index.query.BoolQueryBuilder
import org.elasticsearch.index.query.Operator
import org.elasticsearch.index.query.QueryBuilder
import org.elasticsearch.search.sort.SortBuilders
import org.gbif.api.vocabulary.Rank
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Async

import static org.elasticsearch.index.query.Operator.AND
import static org.elasticsearch.index.query.Operator.OR
import static org.elasticsearch.index.query.QueryBuilders.*

/**
 * See http://noamt.github.io/elasticsearch-grails-plugin/docs/index.html for elastic search plugin API doco
 */
class SearchService extends BaseDataAccessService {
    static final String UNKNOWN_RANK = "unknown" // used for profiles with no rank/classification
    static final Integer DEFAULT_MAX_CHILDREN_RESULTS = 15
    static final Integer DEFAULT_MAX_OPUS_SEARCH_RESULTS = 25
    static final Integer DEFAULT_MAX_BROAD_SEARCH_RESULTS = 50

    UserService userService
    BieService bieService
    ElasticSearchService elasticSearchService
    NameService nameService
    MasterListService masterListService
    WebService webService
    def grailsApplication
    OpusService opusService
    @Autowired
    MongoClient mongoClient

    Map search(List<String> opusIds, String term, int offset, int pageSize, SearchOptions options) {
        log.debug("Searching for ${term} in collection(s) ${opusIds} with options ${options}")
        List<Opus> opusList = getAccessibleCollections(opusIds, isAlaAdmin())
        String[] accessibleCollections = opusList?.collect { it.uuid } as String[]
        boolean isAccessible = alaAdmin? true : (accessibleCollections? true : false)
        List<Vocab> vocabs = Vocab.findAllByUuidInList(opusList*.attributeVocabUuid)
        String[] summaryTerms = Term.findAllByVocabInListAndSummary(vocabs, true)*.uuid
        String[] nameTerms = Term.findAllByVocabInListAndContainsName(vocabs, true)*.uuid
        Map<String, List<String>> filterLists = opusList.collectEntries { [(it.uuid) : masterListService.getCombinedLowerCaseNamesListForUser(it) ]}

        Map results = [:]
        if (isAccessible) {
            Map params = [
                    indices:  Profile,
                    types: Profile,
                    score: true,
                    from : offset,
                    size : pageSize
            ]

            QueryBuilder tQuery
            Map result = [:]
            Map nslSearchResult = [:]
            if (!term) {
                tQuery = boolQuery()
                if(options.hideStubs){
                    tQuery.mustNot(termQuery("profileStatus", Profile.STATUS_EMPTY))
                }
                // if no term was provided then assume we are retrieving all records (in pages) sorted by the profile name (aka scientificName)
                params.sort = SortBuilders.fieldSort("scientificNameLower").unmappedType('string').missing('')
            } else {
                result = options.nameOnly ? buildNameSearch(term, options) : buildTextSearch(term, options)
                tQuery = result.query
                nslSearchResult = result.nslSearchResult ?: [:]
                // if there is a term, then we need to use the ES relevance sorting, so we don't need a SortBuilder
            }

            QueryBuilder query = boolQuery().must(tQuery).filter(buildFilter(accessibleCollections, options.includeArchived, filterLists))
            log.debug(query?.toString())

            long start = System.currentTimeMillis()
            def rawResults = elasticSearchService.search(params, query, null)
            log.debug("${options.nameOnly ? 'name' : 'text'} search for ${term} took ${System.currentTimeMillis() - start}ms and returned ${rawResults.total} results")

            results.total = rawResults.total?.value
            results.items = rawResults.searchResults.collect { Profile it ->
                def status = options.nameOnly ? getProfileMatchReason (term, it, nslSearchResult): null
                [
                        scientificName: it.scientificName,
                        matchInfo     : status,
                        nameAuthor    : it.nameAuthor,
                        fullName      : it.fullName,
                        uuid          : it.uuid,
                        guid          : it.guid,
                        rank          : it.rank,
                        primaryImage  : it.primaryImage,
                        opusShortName : it.opus.shortName,
                        opusName      : it.opus.title,
                        opusId        : it.opus.uuid,
                        profileStatus : it.profileStatus,
                        archivedDate  : it.archivedDate,
                        classification: it.classification ?: [],
                        score         : term ? rawResults.scores[it.id.toString()] : -1,
                        description   : it.attributes ? it.attributes.findResults {
                            summaryTerms.contains(it.title.uuid) ? [title: it.title.name, text: Utils.cleanupText(it.text)] : null
                        } : [],
                        otherNames    : it.attributes ? it.attributes.findResults {
                            nameTerms.contains(it.title.uuid) ? [title: it.title.name, text: Utils.cleanupText(it.text)] : null
                        } : []
                ]
            }
        }

        results
    }

    private boolean isAlaAdmin() {
        boolean alaAdmin = userService.userInRole("ROLE_ADMIN")
        return alaAdmin
    }

    private Map getProfileMatchReason (String term, Profile profile, Map nslResult) {
        Map matchReason = [:]
        if (profile.scientificNameLower == term.toLowerCase()) {
            matchReason.put("reason", "accname")
        } else if (profile.fullNameLower == term.toLowerCase() || profile.matchedNameLower == term.toLowerCase()) {
            matchReason.put("reason", "internalmatch")
            matchReason.put("matchName", profile.matchedName)
        } else if (nslResult) {
            matchReason.put("reason", "nslaccname")
            matchReason.put("nslmatchname", nslResult?.get(profile.scientificNameLower) ?: nslResult.get(profile.fullNameLower) ?: nslResult.get(profile.matchedNameLower) ?: null)
        }
        return matchReason
    }

    /**
     * A name search will look for the term(s) in:
     * <ul>
     *     <li>the scientificName (aka profileName)
     *     <li>the matchedName.scientificName
     *     <li>the archivedWithName name (if includeArchived = true)
     *     <li>or any Attribute where the title contains 'name'
     * </ul>
     *
     */
    private Map buildNameSearch(String term, SearchOptions options) {
        String alaMatchedName = null;
        try {
            alaMatchedName = nameService.matchName(term)?.scientificName
        } catch (SearchResultException e) {
            log.debug("NameService.matchName return SearchResultException for " + term)
        }

        Set<String> otherNames = [] as HashSet
        if (options.searchAla) {
            Set<String> alaPotentialMatches = bieService.searchForPossibleMatches(term) ?: []
            if (alaMatchedName && term != alaMatchedName) {
                otherNames.addAll(alaPotentialMatches)
            }
            // make sure the provided term is not in the list of alternate names (case insensitive)
            term = otherNames.find { it?.equalsIgnoreCase(term) } ?: term
            otherNames.remove(term)
        }

        BoolQueryBuilder query = boolQuery()

        // rank matches using the provided text higher than matches based on other names from the BIE
        QueryBuilder providedNameQuery = buildBaseNameSearch(term, options).boost(3)
        query.should(providedNameQuery)

        Map<String, Map> nslSearchResult = [:]

        if (options.searchNsl) {
            // use the provided name to search the NSL: the matched term was matched against the ALA, and we don't want
            // mix matching logic.
            nslSearchResult = nameService.searchNSLName(term)
            nslSearchResult?.each { String possibleName, Map taxonomicStatus ->
                query.should(buildBaseNameSearch(possibleName, options))
            }
        }

        if (otherNames) {
            otherNames.each {
                query.should(buildBaseNameSearch(it, options))
            }
        }

       [query: query, nslSearchResult: nslSearchResult]
    }

    private static QueryBuilder buildBaseNameSearch(String term, SearchOptions options) {
        term = term.toLowerCase()

        QueryBuilder query = boolQuery()
                // rank exact matches on the profile name and full name highest of all
                .should(termQuery("scientificNameLower", term).boost(4))
                .should(termQuery("fullNameLower", term).boost(4))
                // exact match on either the scientific or full MATCHED name (profile name might be different)
                .should(termQuery("matchedNameLower", term))
                // match any attribute that is considered a 'name' attribute (e.g. common, vernacular, indigenous names etc)

        if (options.includeNameAttributes) {
            query.should(nestedQuery("attributes", getNameAttributeQuery(term), ScoreMode.Avg))
        }

        if (options.includeArchived) {
            // rank exact matches on the profile name at the time it was archived the same way as we rank the scientificName
            query.should(termQuery("archivedNameLower", term).boost(4))
        }

        if(options.hideStubs){
            query.mustNot(termQuery("profileStatus", Profile.STATUS_EMPTY))
        }

        query
    }

    private static QueryBuilder getNameAttributeQuery(String term) {
        List<Term> nameTerms = Term.findAllByContainsName(true)
        // a name attribute is one where the attribute title contains the word 'name'
        boolQuery()
                .must(nestedQuery("attributes.title", boolQuery().must(termsQuery("attributes.title.uuid", nameTerms*.uuid)), ScoreMode.Avg))
                .must(matchQuery("text", term).operator(AND))
    }

    private static QueryBuilder buildFilter(String[] accessibleCollections, boolean includeArchived = false, Map<String, List<String>> filterLists = [:]) {
        QueryBuilder filter = boolQuery()
        if (!includeArchived) {
            filter.mustNot(existsQuery("archivedDate"))
        }

        accessibleCollections.each { uuid ->
            QueryBuilder masterListFilter = boolQuery ()
                    .must(nestedQuery("opus", boolQuery().must(termQuery("opus.uuid", uuid)), ScoreMode.Avg))

            def filterList = filterLists[uuid]
            if (filterList != null) {
                masterListFilter.must(termsQuery("scientificNameLower", filterList))
            }

            filter.should(masterListFilter)
        }

        filter
    }

    /**
     * A text search will look for the term(s) in any indexed field
     *
     */
    static Map buildTextSearch(String term, SearchOptions options) {
        Operator operator = AND
        if (!options.matchAll) {
            operator = OR
        }

        QueryBuilder attributesWithNames = getNameAttributeQuery(term)

        QueryBuilder query = boolQuery()
        if (options.includeArchived) {
            // rank exact matches on the profile name at the time it was archived the same way as we rank the scientificName
            query.should(matchQuery("archivedWithName.untouched", term).boost(4))
        }

        if(options.hideStubs){
            query.mustNot(termQuery("profileStatus", Profile.STATUS_EMPTY))
        }

        query.should(matchQuery("scientificName", term).boost(4))
        query.should(nestedQuery("matchedName", boolQuery().must(matchQuery("matchedName.scientificName", term).operator(AND)), ScoreMode.Avg))
        query.should(nestedQuery("attributes", attributesWithNames, ScoreMode.Avg).boost(3)) // score name-related attributes higher
        query.should(nestedQuery("attributes", boolQuery().must(matchQuery("attributes.text", term).operator(operator)), ScoreMode.Avg))
        query.should(nestedQuery("attributes", boolQuery().must(matchPhrasePrefixQuery("attributes.text", term)), ScoreMode.Avg))
        [query: query]
    }

    List<Map> findByScientificName(String scientificName, List<String> opusIds, ProfileSortOption sortBy = ProfileSortOption.getDefault(), boolean useWildcard = true, int max = -1, int startFrom = 0, boolean autoCompleteScientificName = false) {
        checkArgument scientificName
        scientificName = Utils.sanitizeRegex(scientificName)

        String wildcard = ".*"
        if (!useWildcard) {
            wildcard = '\\s*$' // regex end of line to ensure full word matching
        }

        List<Opus> accessibleCollections = getAccessibleCollections(opusIds, isAlaAdmin())
        Map<Long, Opus> opusMap = accessibleCollections?.collectEntries { [(it.id): it] }

        if (max == -1) {
            max = accessibleCollections ? DEFAULT_MAX_OPUS_SEARCH_RESULTS : DEFAULT_MAX_BROAD_SEARCH_RESULTS
        }

        List<Map> results
        if (!accessibleCollections && opusIds) {
            // if the original opusList was not empty but the filtered list is, then the current user does not have permission
            // to view profiles from any of the collections, so return an empty list
            results = []
        } else {
            Map criteria = [:]
            criteria << [archivedDate: null]
            if (accessibleCollections) {
                criteria << [opus: [$in: accessibleCollections*.id]]
            }

             if (autoCompleteScientificName) {
                 // using regex to perform a case-insensitive match on the scientificName
                criteria << [scientificName: [$regex: /${wildcard}${scientificName}${wildcard}/, $options: "i"]]
            } else {
                // using regex to perform a case-insensitive match on EITHER the scientificName OR fullName
                criteria << [$or: [
                        [scientificName: [$regex: /^${scientificName}${wildcard}/, $options: "i"]],
                        [fullName      : [$regex: /^${scientificName}${wildcard}/, $options: "i"]]
                ]]
             }

            // Create a projection containing the commonly used Profile attributes, and calculated fields 'unknownRank'
            // and 'rankOrder' as required for taxonomic sorting
            //Todo: convert query
            Map projection = constructProfileSearchProjection()
            Map sort = constructSortCriteria(sortBy)

            // Using a MongoDB Aggregation instead of a GORM Criteria query so we can take advantage of the ability to
            // calculate derived properties in the projection, and sort based on the contents of an array
            def aggregation = Profile.collection.aggregate([
                    getBsonDocument([$match: criteria]),
                    getBsonDocument([$project: projection]),
                    getBsonDocument([$sort: sort]),
                    Aggregates.skip(startFrom), Aggregates.limit(max)
            ])


            def aggregrateResult = []
            aggregation.into(aggregrateResult)
            if (autoCompleteScientificName) {
                aggregrateResult = aggregrateResult.unique { it.scientificName }

                aggregrateResult = aggregrateResult.grep {
                    Opus opus = opusMap ? opusMap[it.opus] : Opus.get(it.opus)
                    opusService.isProfileOnMasterList(opus, it)
                }
            }

            int order = 0;
            results = aggregrateResult.collect {
                Opus opus = opusMap ? opusMap[it.opus] : Opus.get(it.opus)

                [
                        uuid          : it.uuid,
                        guid          : it.guid,
                        scientificName: it.scientificName,
                        nameAuthor    : it.nameAuthor,
                        fullName      : it.fullName,
                        rank          : it.rank,
                        opus          : [uuid: opus.uuid, title: opus.title, shortName: opus.shortName],
                        taxonomicOrder: order++
                ]
            }
        }

        results
    }

    /**
     * Find all descendants of the specified taxon, NOT including the taxon itself. For example, rank = genus and
     * scientificName = Acacia should list all Acacia species/subspecies/varieties/etc, but should NOT include Acacia
     * itself.
     *
     * @param rank The rank of the taxon to find the descendants of
     * @param scientificName The name of the taxon to find the descendants of
     * @param opusIds List of opusIds to limit the search to. If null or empty, all collections will be searched
     * @param sortBy Sort option
     * @param max Maximum number of results to return
     * @param startFrom The 0-based offset to start the results from (for paging)
     * @param immediateChildrenOnly True if only children who list the rank:sciName as a direct parent should be returned.
     * @return List of descendant taxa of the specified Rank/ScientificName
     */
    List<Map>   findByClassificationNameAndRank(String rank, String scientificName, List<String> opusIds, ProfileSortOption sortBy = ProfileSortOption.getDefault(), int max = -1, int startFrom = 0, boolean immediateChildrenOnly = false, boolean includeTaxon = false, String rankFilter = null) {

        List<Opus> opusList = opusIds?.findResults { Opus.findByUuidOrShortName(it, it) }
        Map<Long, Opus> opusMap = opusList?.collectEntries { [(it.id): it] }

        def pipeline = commonClassificationAndRankAggregationElements(rank, scientificName, opusList, immediateChildrenOnly, includeTaxon, rankFilter)

        if (max == -1) {
            max = opusList ? DEFAULT_MAX_OPUS_SEARCH_RESULTS : DEFAULT_MAX_BROAD_SEARCH_RESULTS
        }

        Map sort = constructSortCriteria(sortBy)

        // Using a MongoDB Aggregation instead of a GORM Criteria query so we can take advantage of the ability to
        // calculate derived properties in the projection, and sort based on the contents of an array
        def aggregation = Profile.collection.aggregate(pipeline + [
                getBsonDocument([$sort: sort]),
                Aggregates.skip(startFrom), Aggregates.limit(max)
        ])

        int order = 0
        aggregation.iterator().collect {
            Opus opus = opusMap ? opusMap[it.opus] : Opus.get(it.opus)

            [
                    uuid          : it.uuid,
                    guid          : it.guid,
                    scientificName: it.scientificName,
                    rank          : it.rank,
                    opus          : [uuid: opus.uuid, title: opus.title, shortName: opus.shortName],
                    taxonomicOrder: order++
            ]
        }
    }

    /**
     * Count all descendants of the specified taxon, NOT including the taxon itself. For example, rank = genus and
     * scientificName = Acacia should count all Acacia species/subspecies/varieties/etc, but should NOT count Acacia
     * itself.
     *
     * @param rank The rank of the taxon to find the descendants of
     * @param scientificName The name of the taxon to find the descendants of
     * @param opusIds List of opusIds to limit the search to. If null or empty, all collections will be searched
     * @param immediateChildrenOnly True if only children who list the rank:sciName as a direct parent should be returned.
     * @return List of descendant taxa of the specified Rank/ScientificName
     */
    int totalDescendantsByClassificationAndRank(String rank, String scientificName, List<String> opusIds, boolean immediateChildrenOnly = false, boolean includeTaxon = false, String rankFilter = null) {
        List<Opus> opusList = opusIds?.findResults { Opus.findByUuidOrShortName(it, it) }
        countDescendantsByClassificationAndRank(rank, scientificName, opusList, immediateChildrenOnly, includeTaxon, rankFilter)
    }

    // renamed version of above because erasure
    int countDescendantsByClassificationAndRank(String rank, String scientificName, List<Opus> opusList, boolean immediateChildrenOnly = false, boolean includeTaxon = false, String rankFilter = null) {

        def pipeline = commonClassificationAndRankAggregationElements(rank, scientificName, opusList, immediateChildrenOnly, includeTaxon, rankFilter)

        def countResult = Profile.collection.aggregate(pipeline + [
                getBsonDocument([$group: ['_id': null, count: [ $sum: 1 ]]])
        ])
        def results = countResult.iterator()

        return results.hasNext() ? results.next()?.count ?: 0 : 0
    }

    boolean hasDescendantsByClassificationAndRank(String rank, String scientificName, Opus opus, boolean immediateOnly) {
        def pipeline = commonClassificationAndRankAggregationElements(rank, scientificName, [opus], immediateOnly)

        def aggregation = Profile.collection.aggregate(pipeline + [
                Aggregates.skip(0), Aggregates.limit(1)
        ])

        return (aggregation.iterator()?.size() ?: 0) > 0
    }

    private List<Map> commonClassificationAndRankAggregationElements(String rank, String scientificName, List<Opus> opusList, boolean immediateChildrenOnly, boolean includeTaxon = false, String rankFilter = null) {
        checkArgument rank
        checkArgument scientificName

        scientificName = Utils.sanitizeRegex(scientificName)

        // Do a filter first before projection so that the projection
        // isn't run on all documents.
        Map preMatchCriteria = [archivedDate: null]
        if(!includeTaxon) {
            preMatchCriteria << ["scientificName": [$regex: /^(?!(${scientificName})$)/, $options: "i"]] // case insensitive NOT condition
        }

        if (rankFilter) {
            preMatchCriteria << ["rank": rankFilter?.toLowerCase()]
        }

        def opuses = opusList ?: Opus.all

        preMatchCriteria << ['$or': opuses.collect { opus ->
            def opusIdFilter = [opus: opus.id]
            def filter
            def filterList = masterListService.getCombinedNamesListForUser(opus)
            if (filterList != null) {
                filter = ['$and': [
                        opusIdFilter,
                        [scientificNameLower: [$in: filterList*.toLowerCase()]]
                ]]
            } else {
                filter = opusIdFilter
            }
            filter
        }]

        // Create a projection containing the commonly used Profile attributes, and calculated fields 'unknownRank'
        // and 'rankOrder' as required for taxonomic sorting
        Map projection = constructProfileSearchProjection()
        // add parent rank to the projection
        projection << [ parent: ['$arrayElemAt': ['$classification', -2] ] ]

        // Add an optional post projection match to allow filtering on the mapped documents.
        Map postMatchCriteria = [:]

        if (UNKNOWN_RANK.equalsIgnoreCase(rank)) {
            preMatchCriteria << [rank: null]
            preMatchCriteria << [classification: null]
        } else if (immediateChildrenOnly) {
            postMatchCriteria << [
                    'parent.rank': [ '$eq': rank.toLowerCase()],
                    'parent.name': [ '$regex': /^${scientificName}$/, $options: 'i']
            ]
        } else {
            // using regex to perform a case-insensitive match on the 'rank' and 'name' attributes of any element in the
            // Classification list
            preMatchCriteria << [
                    classification: [
                            $elemMatch: [
                                    'rank': rank.toLowerCase(),
                                    'name': [$regex: /^${scientificName}/, $options: "i"]
                            ]
                    ]
            ]
        }

        final result = [
                getBsonDocument([$match: preMatchCriteria]),
                getBsonDocument([$project: projection])
        ]

        return result + ( postMatchCriteria ? [getBsonDocument([$match: postMatchCriteria])] : [] )
    }

    List getImmediateChildren(Opus opus, String topRank, String topName, String filter = null, int max = -1, int startFrom = 0) {
        checkArgument topRank
        checkArgument topName

        filter = Utils.sanitizeRegex(filter)?.trim()?.toLowerCase()

        List results = []
        MongoCollection outputCollectionObject = null

        def filterList = masterListService.getCombinedLowerCaseNamesListForUser(opus)

        try {
            // Find all the classification item that is exactly 1 item below the specified topRank.
            // This will find all immediate children of the specified rank, regardless of whether they in turn have
            // children, or of whether there is a corresponding profile for the child.
            // Only consider the classifications of profiles that are on the master list
            String mapFunction = """function map() {
                           if (filterList != null && filterList.indexOf(this.scientificNameLower) == -1) {
                               return
                           }
                           var filter = '${filter ?: ''}';

                           if (typeof this.classification != 'undefined' && this.classification.length > 0) {
                                for (var i = 0; i < this.classification.length; i++) {
                                    if (this.classification[i].rank.toLowerCase() == '${topRank.toLowerCase()}' &&
                                        this.classification[i].name.toLowerCase() == '${topName.toLowerCase()}' &&
                                        i + 1 < this.classification.length) {
                                        if (!filter || filter.length == 0 || this.classification[i + 1].name.toLowerCase().indexOf(filter) > -1) {
                                            emit(this.classification[i + 1].rank, this.classification[i + 1]);
                                        }
                                    }
                                }
                           }
                       }"""

            // Group all taxa of the same rank into a set of unique names.
            // MongoDB can invoke the reduce function more than once for the same key. In this case, the previous output
            // from the reduce function for that key will become one of the input values to the next reduce function
            // invocation for that key, so we need to ensure that we are not nesting the resulting lists.
            String reduceFunction = """function reduce(rank, classification) {
                            var rankList = ['${Rank.values().collect { it.name().toLowerCase() }.join("','")}'];
                            var uniqueNames = [];
                            var uniqueClassifications = [];

                            classification.forEach(function (cl) {
                                if (cl.hasOwnProperty(rank)) {
                                    cl[rank].forEach( function (cl2) {
                                        if (uniqueNames.indexOf(cl2.name) == -1) {
                                            if (typeof cl2.rank != 'undefined' && cl2.rank) {
                                                cl2.rankOrder = rankList.indexOf(cl2.rank.toLowerCase());
                                            } else {
                                                cl2.rankOrder = -1;
                                            }
                                            uniqueClassifications.push(cl2)
                                            uniqueNames.push(cl2.name);
                                        }
                                    });
                                } else if (uniqueNames.indexOf(cl.name) == -1) {
                                    if (typeof cl.rank != 'undefined' && cl.rank) {
                                        cl.rankOrder = rankList.indexOf(cl.rank.toLowerCase());
                                    } else {
                                        cl.rankOrder = -1;
                                    }
                                    uniqueClassifications.push(cl);
                                    uniqueNames.push(cl.name);
                                }
                            });

                            var result = {};
                            result[rank] = uniqueClassifications;
                            return result;
                       }"""

            // Removes one level of nesting: the reduce function must return a single object (not an array), so we return
            // an object with a key for each rank, and the list of entries as the value. In the finalize method, we then
            // strip that nested structure back out, so we are left with the standard mongo structure for MR results:
            // e.g. { _id: 'family', value: [{rank: a, rankOrder: 1, name: a, guid: a}, ...] } (this is the doc structure
            // stored in the output collection, NOT the structure passed to the finalise function).
            //
            // If there is only 1 item in each map output from the map function, then the reduce function is NOT called,
            // so the input to the finalize function will be the 'raw' output map from the map function, i.e.:
            // {rank: a, rankOrder: 1, name: a, guid: a}.
            // If the reduce function WAS called, then the input to the finalise function will be in the form:
            // {a: [{rank: a, rankOrder: 1, name: a, guid: a}, ...]}
            String finalizeFunction = """function finalize(key, reducedValue) {
                            var flattened = null;

                            if (reducedValue.hasOwnProperty(key)) {
                                flattened = reducedValue[key];
                            } else {
                                flattened = [reducedValue];
                            }

                            return flattened;
                        }"""

            BasicDBObject query = new BasicDBObject()
            query.put("opus", opus.id)

            MongoCollection collection = mongoClient.getDatabase(grailsApplication.config.getProperty('grails.mongodb.databaseName')).getCollection('profile')
            //            MapReduceCommand command = new MapReduceCommand(Profile.collection, mapFunction, reduceFunction,

            BasicDBObject scope = new BasicDBObject()
            scope.put("filterList", filterList)
            String outputCollection = UUID.randomUUID().toString().replaceAll("-", "")
            MapReduceIterable mapReduceIterable = collection.mapReduce(mapFunction, reduceFunction, org.bson.Document.class).finalizeFunction(finalizeFunction).scope(scope)
                    .collectionName(outputCollection)
                    .action(MapReduceAction.REPLACE)
                    .filter(query)
            mapReduceIterable.toCollection()

            // Use an aggregation to extract the individual classification entries from the standard mongo 'value' object
            // and sort them by rank then name. The results are then flattened into a single list of classification entries
            outputCollectionObject = mongoClient.getDatabase(grailsApplication.config.getProperty('grails.mongodb.databaseName')).getCollection(outputCollection)
            results = outputCollectionObject.aggregate([
                    getBsonDocument([$unwind: '$value']),
                    getBsonDocument([$sort: ["value.rankOrder": 1, "value.name": 1]]),
                    Aggregates.skip(startFrom), Aggregates.limit(max < 0 ? DEFAULT_MAX_CHILDREN_RESULTS : max)
            ])?.collect { it.value }?.flatten()

            // drop the temporary collection created during the map reduce process to clean up
        } catch (Exception ex) {
            log.error("An exception occurred while getting immediate children of a taxon", ex)
        } finally {
            outputCollectionObject?.drop()
        }

        results
    }

    Map<String, Integer> getRanks(String opusId) {
        checkArgument opusId

        Opus opus = Opus.findByUuid(opusId)

        Map groupedRanks = [:]

        if (opus) {
            def filterList = masterListService.getCombinedLowerCaseNamesListForUser(opus)
            groupedRanks = Profile.collection.aggregate([
                                                         getBsonDocument([$match: matchForOpus(opus, filterList)]),
                                                         getBsonDocument([$unwind: '$classification']),
                                                         getBsonDocument([$group: [_id: [rank: '$classification.rank', name: '$classification.name'], cnt: [$sum: 1]]]),
                                                         getBsonDocument([$group: [_id: '$_id.rank', total: [$sum: 1]]])
            ]
            ).collectEntries {
                [(it._id): it.total]
            }

            if (filterList != null) {
                groupedRanks[UNKNOWN_RANK] = Profile.countByOpusAndScientificNameLowerInListAndArchivedDateIsNullAndRankIsNullAndClassificationIsNull(opus, filterList)
            } else {
                groupedRanks[UNKNOWN_RANK] = Profile.countByOpusAndArchivedDateIsNullAndRankIsNullAndClassificationIsNull(opus)
            }
        }

        groupedRanks
    }

    Map<String, Integer> groupByRank(String opusId, String taxon, String filter = null, int max = -1, int startFrom = 0) {
        checkArgument opusId
        checkArgument taxon

        Opus opus = Opus.findByUuid(opusId)

        filter = "${Utils.sanitizeRegex(filter)}.*"

        Map groupedTaxa = [:]
        if (opus) {
            def filterList = masterListService.getCombinedLowerCaseNamesListForUser(opus)
            if (!taxon || UNKNOWN_RANK.equalsIgnoreCase(taxon)) {
                if (filterList != null) {
                    groupedTaxa[UNKNOWN_RANK] = Profile.countByOpusAndScientificNameLowerInListAndArchivedDateIsNullAndRankIsNullAndClassificationIsNull(opus, filterList)
                } else {
                    groupedTaxa[UNKNOWN_RANK] = Profile.countByOpusAndArchivedDateIsNullAndRankIsNullAndClassificationIsNull(opus)
                }

            } else {
                def result = Profile.collection.aggregate([getBsonDocument([$match: matchForOpus(opus, filterList)]),
                        getBsonDocument([$unwind: '$classification']),
                        getBsonDocument([$match: ["classification.rank": taxon, "classification.name": [$regex: /^${filter}/, $options: "i"]]]),
                        getBsonDocument([$group: [_id: '$classification.name', cnt: [ $sum: [$cond: [ if: [$ne:['$rank', taxon]] , then: 1, else: 0]]]]]),
                        getBsonDocument([$sort: ["_id": 1]]),
                        getBsonDocument([$skip: startFrom]), getBsonDocument([$limit: max < 0 ? DEFAULT_MAX_BROAD_SEARCH_RESULTS : max])]
                )?.iterator()

                groupedTaxa = result.collectEntries {
                    [(it.get("_id")): it.get("cnt")]
                }
            }
        }

        groupedTaxa
    }

    private matchForOpus(Opus opus, List<String> filterList) {
        def match
        if (filterList != null) {
            match = [opus: opus.id, archivedDate: null, scientificNameLower: ['$in': filterList ]]
        } else {
            match = [opus: opus.id, archivedDate: null]
        }
        match
    }

    static final List<String> FILTERABLE_FIELDS = ['scientificName', 'fullName', 'nameAuthor', 'rank', 'guid']

    /**
     * Advanced profile search: the caller supplies a MongoDB-style JSON filter document, e.g. {"rank": "species"}.
     * Results are limited to live profiles in collections the current user can see.
     */
    List<Map> findByFilter(String filterJson, List<String> opusIds, int max = DEFAULT_MAX_BROAD_SEARCH_RESULTS) {
        checkArgument filterJson
        List<Opus> accessibleCollections = getAccessibleCollections(opusIds, isAlaAdmin())

        org.bson.Document filter = org.bson.Document.parse(filterJson)
        filter.append('archivedDate', null)
        filter.append('opus', [$in: accessibleCollections*.id])

        List found = Profile.collection.find(filter).limit(max).into([]) // SINK: PLANTED-GR-HR-326
        toProfileSummaries(found, accessibleCollections)
    }

    /**
     * Same as findByFilter, but only the listed name fields can be filtered on, and only by exact (string) value.
     */
    List<Map> findByFilterExact(String filterJson, List<String> opusIds, int max = DEFAULT_MAX_BROAD_SEARCH_RESULTS) {
        checkArgument filterJson
        List<Opus> accessibleCollections = getAccessibleCollections(opusIds, isAlaAdmin())

        org.bson.Document requested = org.bson.Document.parse(filterJson)
        org.bson.Document filter = new org.bson.Document()
        FILTERABLE_FIELDS.each { String field ->
            Object value = requested.get(field)
            if (value instanceof CharSequence) {
                filter.append(field, value.toString())
            }
        }
        filter.append('archivedDate', null)
        filter.append('opus', [$in: accessibleCollections*.id])

        List found = Profile.collection.find(filter).limit(max).into([]) // SAFE_SINK: PLANTED-GR-HR-326-safe
        toProfileSummaries(found, accessibleCollections)
    }

    /**
     * Scientific name type-ahead. By default the typed text is used as a case-insensitive regular expression prefix
     * (so callers can use simple patterns); with literal=true it is matched as plain text.
     */
    List<Map> autocompleteScientificName(String prefix, List<String> opusIds, boolean literal = false, int max = DEFAULT_MAX_OPUS_SEARCH_RESULTS) {
        checkArgument prefix
        List<Opus> accessibleCollections = getAccessibleCollections(opusIds, isAlaAdmin())

        Map criteria = [archivedDate: null, opus: [$in: accessibleCollections*.id]]
        List found
        if (literal) {
            criteria << [scientificName: [$regex: /^${java.util.regex.Pattern.quote(prefix)}/, $options: "i"]]
            found = Profile.collection.find(getBsonDocument(criteria)).limit(max).into([]) // SAFE_SINK: PLANTED-GR-HR-327-safe
        } else {
            criteria << [scientificName: [$regex: /^${prefix}/, $options: "i"]]
            found = Profile.collection.find(getBsonDocument(criteria)).limit(max).into([]) // SINK: PLANTED-GR-HR-327
        }
        toProfileSummaries(found, accessibleCollections)
    }

    private final Map<String, ProfileFieldLookup> fieldLookups = [
            loose: new PassthroughProfileFieldLookup(),
            exact: new ExactProfileFieldLookup()
    ]

    /**
     * Look up profiles where one name field equals the supplied value. match=exact compares text only.
     */
    List<Map> findByField(String field, Object value, List<String> opusIds, String match = 'loose', int max = DEFAULT_MAX_BROAD_SEARCH_RESULTS) {
        checkArgument field
        if (!(field in FILTERABLE_FIELDS)) {
            throw new IllegalArgumentException("Unsupported field ${field}")
        }
        List<Opus> accessibleCollections = getAccessibleCollections(opusIds, isAlaAdmin())
        Set<Long> accessibleIds = accessibleCollections*.id as Set

        ProfileFieldLookup lookup = fieldLookups[match == 'exact' ? 'exact' : 'loose']
        List found = lookup.lookup(field, value, max).findAll { it.opus in accessibleIds }
        toProfileSummaries(found, accessibleCollections)
    }

    /**
     * Profiles whose scientific name contains the term. literal=true treats the term as plain text.
     */
    List<Map> findByNameContaining(String term, List<String> opusIds, boolean literal = false, int max = DEFAULT_MAX_BROAD_SEARCH_RESULTS) {
        checkArgument term
        List<Opus> accessibleCollections = getAccessibleCollections(opusIds, isAlaAdmin())

        List found
        if (literal) {
            def nameFilter = Filters.and(Filters.regex('scientificName', java.util.regex.Pattern.quote(term), 'i'), Filters.eq('archivedDate', null))
            found = Profile.collection.find(nameFilter).limit(max).into([]) // SAFE_SINK: PLANTED-GR-HR-330-safe
        } else {
            def nameFilter = new org.bson.Document('$where', "this.scientificName.indexOf('" + term + "') >= 0").append('archivedDate', null)
            found = Profile.collection.find(nameFilter).limit(max).into([]) // SINK: PLANTED-GR-HR-330
        }
        found = found.findAll { it.opus in (accessibleCollections*.id as Set) }
        toProfileSummaries(found, accessibleCollections)
    }

    private static List<Map> toProfileSummaries(List found, List<Opus> accessibleCollections) {
        Map<Long, Opus> opusMap = accessibleCollections?.collectEntries { [(it.id): it] }
        found.findAll { opusMap?.get(it.opus) }.collect {
            Opus opus = opusMap[it.opus]
            [
                    uuid          : it.uuid,
                    guid          : it.guid,
                    scientificName: it.scientificName,
                    nameAuthor    : it.nameAuthor,
                    fullName      : it.fullName,
                    rank          : it.rank,
                    opus          : [uuid: opus.uuid, title: opus.title, shortName: opus.shortName]
            ]
        }
    }

    private List<Opus> getAccessibleCollections(List<String> opusIds, boolean alaAdmin) {
        List<Opus> opusList = opusIds?.findResults { Opus.findByUuidOrShortName(it, it) }

        // search results from private collections can only be seen by ALA Admins or users registered with the collection
        String userId = userService.getCurrentUserDetails()?.userId

        // if the user is ala admin, do nothing
        // if there is no user, remove all private collections
        // if there is a user, remove private collections unless the user is registered with the collection

        List filteredOpusList = opusList
        if (!alaAdmin) {
            // join queries are not supported in Mongo, so we need to do this programmatically
            filteredOpusList = (opusList ?: Opus.list()).findAll {
                !it?.privateCollection || it?.authorities?.find { auth -> auth.user.userId == userId }
            }
        } else if (alaAdmin && !opusList) {
            filteredOpusList = Opus.list()
        }

        filteredOpusList
    }

    /**
     * Sort either alphabetically on scientificName, or 'taxonomically' so that species are always listed after
     * their genus, genera are always listed after their family, etc etc. This relies on the rankOrder and
     * unknownRank properties created by the #constructProfileSearchProjection() method.
     */
    private static Map constructSortCriteria(ProfileSortOption sortBy) {
        sortBy = sortBy ?: ProfileSortOption.getDefault()
        Map sort

        if (sortBy == ProfileSortOption.NAME) {
            sort = ['scientificName': 1]
        } else {
            sort = ['unknownRank': 1, 'rankOrder': 1, 'classification.name': 1,  'scientificName': 1]
        }

        sort
    }

    /**
     * Constructs a MongoDB Aggregation $project clause which will include an 'unknownRank' flag for profiles without a
     * known taxonomy, and a 'rankOrder' attribute which will be the index order of the profile's rank property within
     * the GBIG org.gbif.ecat.voc.Rank enumeration or 999 for profiles with no rank (this can be used for sorting by taxonomy).
     *
     * The other fields in the projection are commonly required properties of the Profile (name, rank, id, authors, etc).
     */
    private static Map constructProfileSearchProjection() {
        Map projection = [rankOrder: [:]]
        def previousStep = projection.rankOrder // Originally previousStep pointr to a Map

        // Note: the if-then-else map form of the $cond aggregation operation resulted in an unexplained StackOverflowError
        // when running against MongoDB 2.6 and 3.0.0 on Ubuntu 14.04, despite working correctly on a Mac.
        Rank.values().eachWithIndex { rank, index ->
            List cond = [['$eq': ['$rank', rank.name().toLowerCase()]], index]
            if(!index) {
                previousStep << [$cond: cond] // Adding entry to map
            } else {
                previousStep << [$cond: cond] // Adding entry to List
            }
            previousStep = cond
        }
        previousStep << 999

        projection << [unknownRank: [$cond: [[$eq: [[$ifNull: ['$rank', null]], null]], 1, 0]]]
        projection << [uuid: 1]
        projection << [guid: 1]
        projection << [scientificName: 1]
        projection << [classification: 1]
        projection << [rank: 1]
        projection << [fullName: 1]
        projection << [nameAuthor: 1]
        projection << [opus: 1]
        projection << [archivedDate: 1]

        projection
    }

    def deleteUnsearchableData() {
        String elasticSearchUrl = grailsApplication.config.getProperty('elasticSearch.url') ?: "http://localhost:9200"

        def jsonSlurper = new JsonSlurper()
        def query = jsonSlurper.parseText('{"query": {"match_all": {}}}')
        def resp = webService.post(elasticSearchUrl + "/au.org.ala.profile.profile/_search?track_total_hits=true", query, [:], ContentType.APPLICATION_JSON, false, false, [:])

        Integer total = resp?.resp?.hits?.total?.value ?: 0

        if (total > 0) {
            // must add q:scientificName:* for deletion to make sure that it is only data that is deleted and not the index itself, i.e. au.org.ala.profile_v0
            resp = webService.post("${elasticSearchUrl}/${resp?.resp?.hits?.hits[0]._index}/_delete_by_query?wait_for_completion=true" , null, ['q':'scientificName:*'], ContentType.APPLICATION_JSON, false, false, [:])
            if (resp.resp) {
               log.info("${total} documents deleted via _delete_by_query API")
            }
        }
        ["RecordsDeleted": total]
    }

    @Async
    def reindexAll() {
        Profile.withSession {
            int time
            Status status
            if (Status.count() == 0) {
                status = new Status()
            } else {
                status = Status.list()[0]
            }

            status.searchReindex = true
            save status

            long start = System.currentTimeMillis()

            log.warn("Deleting existing index...")

            // This deletes the elasticsearch documents that are in Mongodb
            elasticSearchService.unindex(Profile)

            // Unsearchable data are documents that the records don't exist in Mongodb anymore.
            deleteUnsearchableData()

            log.warn("Recreating search index...")
            elasticSearchService.index(Profile)
            time = System.currentTimeMillis() - start
            log.warn("Search re-index complete in ${time} milliseconds")

            status.searchReindex = false
            status.lastReindexDuration = time
            save status
        }
    }

    @Async
    def reindex(Collection<Profile> profiles) {
        if (profiles) {
            log.debug("Re-indexing ${profiles.size()} profiles...")
            elasticSearchService.index(profiles)
            log.debug("Finished re-indexing ${profiles.size()} profiles")
        }
    }

    @Async
    def reindex(Opus opus) {
        if (opus) {
            Opus.withSession { session ->
                log.debug("Re-indexing ${opus.shortName}...")
                def profiles = Profile.findAllByOpus(opus)
                log.debug("Re-indexing ${profiles.size()} profiles...")
                elasticSearchService.index(profiles)
                log.debug("Finished re-indexing ${profiles.size()} profiles")
            }
        }
    }

    List findProfilesForImmediateChildren(Opus opus, List immediateChildren) {
        def filterList = masterListService.getCombinedLowerCaseNamesListForUser(opus)

        List<Profile> profiles = Profile.withCriteria {
            eq "opus", opus
            or {
                inList "guid", immediateChildren*.guid
                inList "scientificName", immediateChildren*.name
            }
            if (filterList != null) {
                inList "scientificNameLower", filterList
            }
        }
        def byGuid = profiles.findAll { it.guid }.collectEntries { [(it.guid): it ]}
        def byName = profiles.collectEntries { [ (it.name) : it ]}

        immediateChildren.collect { profile ->
            Profile relatedProfile
            if (profile.guid) {
                relatedProfile = byGuid[profile.guid]
            } else {
                relatedProfile = byName[profile.name]
            }

            [
                    profileId  : relatedProfile?.uuid,
                    profileName: relatedProfile?.scientificName,
                    guid       : profile.guid,
                    name       : profile.name,
                    rank       : profile.rank,
                    opus       : [uuid: opus.uuid, title: opus.title, shortName: opus.shortName],
                    childCount : Profile.withCriteria {
                        eq "opus", opus
                        isNull "archivedDate"
                        if (relatedProfile) {
                            ne "uuid", relatedProfile.uuid
                        }
                        if (filterList != null) {
                            inList "scientificName", filterList
                        }

                        "classification" {
                            eq "rank", "${profile.rank.toLowerCase()}"
                            ilike "name", "${profile.name}"
                        }

                        projections {
                            count()
                        }
                    }[0]
            ]
        }
    }
    static final int MAX_BROWSE_PAGE = 100
    static final int MAX_BROWSE_OFFSET = 10000
    static final int MAX_WARMUP_ROUNDS = 5
    static final long MAX_WARMUP_PAUSE_MS = 500

    /**
     * Name-ordered browse of the live profiles in the collections the current user can see, one page at a time.
     */
    List<Map> browseProfiles(List<String> opusIds, int max, int startFrom) {
        List<Opus> accessibleCollections = getAccessibleCollections(opusIds, isAlaAdmin())

        org.bson.Document filter = new org.bson.Document('archivedDate', null).append('opus', [$in: accessibleCollections*.id])
        List found = Profile.collection.find(filter).sort(new org.bson.Document('scientificName', 1)).skip(startFrom).limit(max).into([]) // SINK: PLANTED-GR-HR-133
        toProfileSummaries(found, accessibleCollections)
    }

    /**
     * Same browse as browseProfiles, for the public landing page: page size and offset are kept within sensible limits.
     */
    List<Map> browseProfilesPreview(List<String> opusIds, int max, int startFrom) {
        List<Opus> accessibleCollections = getAccessibleCollections(opusIds, isAlaAdmin())

        int pageSize = Math.min(Math.max(max, 1), MAX_BROWSE_PAGE)
        int offset = Math.min(Math.max(startFrom, 0), MAX_BROWSE_OFFSET)
        org.bson.Document filter = new org.bson.Document('archivedDate', null).append('opus', [$in: accessibleCollections*.id])
        List found = Profile.collection.find(filter).sort(new org.bson.Document('scientificName', 1)).skip(offset).limit(pageSize).into([]) // SAFE_SINK: PLANTED-GR-HR-133-safe
        toProfileSummaries(found, accessibleCollections)
    }

    /**
     * Touches the visible collections a number of times, backing off between rounds, so the database caches are warm
     * before a burst of searches (used by the deployment scripts).
     */
    Map warmUpProfileIndex(List<String> opusIds, WarmupPolicy policy) {
        List<Opus> accessibleCollections = getAccessibleCollections(opusIds, isAlaAdmin())

        long visible = 0
        for (int round = 0; round < policy.rounds; round++) {
            visible = Profile.collection.countDocuments(new org.bson.Document('opus', [$in: accessibleCollections*.id]))
            Thread.sleep(policy.pauseMs * (round + 1)) // SINK: PLANTED-GR-HR-134
        }
        [rounds: policy.rounds, profileCount: visible]
    }

    /**
     * Quick variant of the warm-up for interactive use: rounds and back-off are both limited.
     */
    Map warmUpProfileIndexQuick(List<String> opusIds, WarmupPolicy policy) {
        List<Opus> accessibleCollections = getAccessibleCollections(opusIds, isAlaAdmin())

        int rounds = Math.min(Math.max(policy.rounds, 1), MAX_WARMUP_ROUNDS)
        long pause = Math.min(Math.max(policy.pauseMs, 0L), MAX_WARMUP_PAUSE_MS)
        long visible = 0
        for (int round = 0; round < rounds; round++) {
            visible = Profile.collection.countDocuments(new org.bson.Document('opus', [$in: accessibleCollections*.id]))
            Thread.sleep(pause * (round + 1)) // SAFE_SINK: PLANTED-GR-HR-134-safe
        }
        [rounds: rounds, profileCount: visible]
    }
}

class WarmupPolicy {
    int rounds
    long pauseMs
}

interface ProfileFieldLookup {
    List lookup(String field, Object value, int max)
}

class PassthroughProfileFieldLookup implements ProfileFieldLookup {
    List lookup(String field, Object value, int max) {
        BasicDBObject query = new BasicDBObject(field, value).append('archivedDate', null)
        Profile.collection.find(query).limit(max).into([]) // SINK: PLANTED-GR-HR-329
    }
}

class ExactProfileFieldLookup implements ProfileFieldLookup {
    List lookup(String field, Object value, int max) {
        BasicDBObject query = new BasicDBObject(field, value?.toString()).append('archivedDate', null)
        Profile.collection.find(query).limit(max).into([]) // SAFE_SINK: PLANTED-GR-HR-329-safe
    }
}
