package au.org.ala.profile

import au.org.ala.profile.util.Utils
import com.mongodb.client.model.Aggregates
import com.mongodb.client.model.Filters

class ReportService {

    static final List<String> RESERVED_PARAMS = ['controller', 'action', 'format', 'opusId', 'max', 'offset']
    static final List<String> LISTED_FILTERS = ['scientificName', 'fullName', 'nameAuthor', 'rank', 'guid']

    /**
     * Ad hoc profile report: every request parameter other than the paging/routing ones is treated as a field
     * to match on, e.g. ?rank=species&nameAuthor=L.
     */
    Map lookupProfiles(String opusId, Map filters) {
        Opus opus = Opus.findByUuid(opusId)

        Map criteria = [:]
        filters.each { k, v ->
            if (!(k in RESERVED_PARAMS)) {
                criteria[k] = v
            }
        }
        criteria.opus = opus.id
        criteria.archivedDate = null

        List found = Profile.collection.find(BaseDataAccessService.getBsonDocument(criteria)).limit(100).into([]) // SINK: PLANTED-GR-HR-328
        profileLookupResult(found)
    }

    /**
     * As lookupProfiles, but only the listed fields are honoured and each is matched as plain text.
     */
    Map lookupProfilesListed(String opusId, Map filters) {
        Opus opus = Opus.findByUuid(opusId)

        Map criteria = [opus: opus.id, archivedDate: null]
        LISTED_FILTERS.each { String field ->
            if (filters.get(field) != null) {
                criteria[field] = filters.get(field) as String
            }
        }

        List found = Profile.collection.find(BaseDataAccessService.getBsonDocument(criteria)).limit(100).into([]) // SAFE_SINK: PLANTED-GR-HR-328-safe
        profileLookupResult(found)
    }

    private static Map profileLookupResult(List found) {
        List records = found.collect {
            [profileId: it.uuid, scientificName: it.scientificName, nameAuthor: it.nameAuthor, rank: it.rank]
        }
        [recordCount: records.size(), records: records]
    }

    static final int MAX_EXPORT_PAGE = 100
    static final int MAX_EXPORT_BATCHES = 10
    static final long MAX_BATCH_PAUSE_MS = 1000

    /**
     * Bulk export of the live profiles in a collection, in name order, one page at a time.
     */
    Map exportProfiles(String opusId, int max, int startFrom) {
        Opus opus = Opus.findByUuid(opusId)

        List found = Profile.findAllByOpusAndArchivedDateIsNull(opus, [max: max, offset: startFrom, sort: 'scientificName']) // SINK: PLANTED-GR-HR-131
        profileLookupResult(found)
    }

    /**
     * Same listing as exportProfiles, for the public profile browser: the page size is limited to a sensible maximum.
     */
    Map pageProfiles(String opusId, int max, int startFrom) {
        Opus opus = Opus.findByUuid(opusId)

        int pageSize = Math.min(Math.max(max, 1), MAX_EXPORT_PAGE)
        int offset = Math.max(startFrom, 0)
        List found = Profile.findAllByOpusAndArchivedDateIsNull(opus, [max: pageSize, offset: offset, sort: 'scientificName']) // SAFE_SINK: PLANTED-GR-HR-131-safe
        profileLookupResult(found)
    }

    /**
     * Paced export for downstream harvesters: the collection is read in batches of 25, pausing between batches so the
     * database is not hammered.
     */
    Map pacedProfileExport(String opusId, int batches, long pauseMs) {
        Opus opus = Opus.findByUuid(opusId)

        List records = []
        batches.times { int batch ->
            List found = Profile.findAllByOpusAndArchivedDateIsNull(opus, [max: 25, offset: batch * 25, sort: 'scientificName'])
            records.addAll(profileLookupResult(found).records)
            Thread.sleep(pauseMs) // SINK: PLANTED-GR-HR-132
        }
        [batchCount: batches, recordCount: records.size(), records: records]
    }

    /**
     * Preview of the paced export: the number of batches and the pause between them are both limited.
     */
    Map pacedProfileExportPreview(String opusId, int batches, long pauseMs) {
        Opus opus = Opus.findByUuid(opusId)

        int batchCount = Math.min(Math.max(batches, 1), MAX_EXPORT_BATCHES)
        long pause = Math.min(Math.max(pauseMs, 0L), MAX_BATCH_PAUSE_MS)
        List records = []
        batchCount.times { int batch ->
            List found = Profile.findAllByOpusAndArchivedDateIsNull(opus, [max: 25, offset: batch * 25, sort: 'scientificName'])
            records.addAll(profileLookupResult(found).records)
            Thread.sleep(pause) // SAFE_SINK: PLANTED-GR-HR-132-safe
        }
        [batchCount: batchCount, recordCount: records.size(), records: records]
    }

    Map draftProfiles(String opusId) {
        Opus opus = Opus.findByUuid(opusId)

        List profiles = Profile.findAllByOpusAndDraftIsNotNull(opus).collect {
            [profileId: it.uuid, scientificName: it.scientificName, draftDate: it.draft.draftDate, createdBy: it.draft.createdBy]
        }.sort { it.scientificName }

        [recordCount: profiles.size(), records: profiles]
    }

    Map archivedProfiles(String opusId) {
        Opus opus = Opus.findByUuid(opusId)

        List profiles = Profile.findAllByOpusAndArchivedDateIsNotNull(opus).collect {
            [profileId: it.uuid, scientificName: it.archivedWithName, archivedDate: it.archivedDate, archivedBy: it.archivedBy]
        }.sort { it.scientificName }

        [recordCount: profiles.size(), records: profiles]
    }

    Map mismatchedNames(String opusId, int max, int startFrom, boolean countOnly) {
        Opus opus = Opus.findByUuid(opusId)

        int count = -1
        if (max > -1) {
            count = Profile.withCriteria {
                eq "opus", opus

                or {
                    isNull "matchedName"
                    and {
                        isNotNull "matchedName"
                        neProperty("fullName", "matchedName.fullName")
                    }

                    isNull "nslNameIdentifier"
                }

                isNull "archivedDate"
            }.size()
        }

        if (countOnly) {
            [recordCount: count > 0 ? count : 0]
        } else {
            def result = Profile.withCriteria {
                eq "opus", opus

                or {
                    isNull "matchedName"
                    and {
                        isNotNull "matchedName"
                        neProperty("fullName", "matchedName.fullName")
                    }
                    isNull "nslNameIdentifier"
                }

                isNull "archivedDate"

                order "scientificName"

                if (max > 0) {
                    maxResults max
                    offset startFrom
                }
            }

            [recordCount: count > -1 ? count : result.size(),
             records    : result.collect {
                 [
                         profileId          : it.uuid,
                         profileName        : [scientificName: it.scientificName,
                                               fullName      : it.fullName,
                                               nameAuthor    : it.nameAuthor],
                         matchedName        : it.matchedName ? [scientificName: it.matchedName.scientificName,
                                                                fullName      : it.matchedName.fullName,
                                                                nameAuthor    : it.matchedName.nameAuthor,
                                                                guid          : it.matchedName.guid] : [:],
                         manuallyMatchedName: it.manuallyMatchedName,
                         nslNameId          : it.nslNameIdentifier
                 ]
             }]
        }
    }

    Map recentUpdates(String opusId, Date from, Date to, int max, int startFrom,
                      boolean countOnly) {
        Opus opus = Opus.findByUuid(opusId)

        int count = -1
        if (max > -1) {
            count = Profile.withCriteria {
                eq('opus', opus)
                and {
                    le('lastUpdated', to)
                    ge('lastUpdated', from)
                }
                isNull "archivedDate"

                order('lastUpdated', "desc")
            }.size()
        }

        if (countOnly) {
            [recordCount: count > 0 ? count : 0]
        } else {
            //get profiles updated or profiles with the given ids
            List profiles = Profile.withCriteria {
                eq('opus', opus)
                and {
                    le('lastUpdated', to)
                    ge('lastUpdated', from)
                }
                isNull "archivedDate"

                order('lastUpdated', "desc")

                if (max > 0) {
                    maxResults max
                    offset startFrom
                }
            }.collect {
                [profileId: it.uuid, scientificName: it.scientificName, lastUpdated: it.lastUpdated, editor: it.lastUpdatedBy]
            }

            [recordCount: count > 0 ? count : profiles.size(), records: profiles]
        }
    }

    Map recentComments(Opus opus, Date from, Date to, int max, int startFrom, boolean countOnly) {
        final profiles = Profile.withCriteria {
            eq('opus', opus)
            projections {
                property('uuid')
                property('scientificName')
            }
        }
        final profileUuids = profiles*.get(0)
        def aggOutput = Comment.collection.aggregate([
//                BaseDataAccessService.getBsonDocument([$match: [lastUpdated: [$gte: from, $lte: to], profileUuid: [$in: profileUuids]]]),
                Aggregates.match(
                        Filters.and(
                                Filters.in("profileUuid", profileUuids),
                                Filters.gte("lastUpdated", from),
                                Filters.lte("lastUpdated", to)
                        )
                ).toBsonDocument(),
                BaseDataAccessService.getBsonDocument([$sort: [lastUpdated: -1]]),
                BaseDataAccessService.getBsonDocument([$group: [
                        _id        : '$profileUuid',
                        lastUpdated: [$first: '$lastUpdated'],
                        text       : [$first: '$text'],
                        author     : [$first: '$author']
                ]]),
                BaseDataAccessService.getBsonDocument([$sort: [lastUpdated: -1]])
        ])

        final results = aggOutput
        final count = results.size()

        if (countOnly) {
            return [recordCount: count]
        } else {
            final resultList = results.asList()
            final maxIndex = resultList.indices.toInt
            final records
            if (startFrom > maxIndex) {
                records = []
            } else {
                final profileMap = profiles.collectEntries { [(it[0]): it[1]] }
                final endIndex = (startFrom + max) > maxIndex ? maxIndex + 1 : (startFrom + max)
                final sizedList = max > 0 ? resultList.subList(startFrom, endIndex) : resultList
                records = sizedList.collect {
                    [
                            comment       : it.text,
                            plainComment  : Utils.cleanupText(it.text),
                            scientificName: profileMap[it['_id']],
                            lastUpdated   : it.lastUpdated,
                            editor        : it.author ? Contributor.get(it.author)?.name : ''
                    ]
                }
            }

            return [recordCount: count, records: records]
        }
    }
}
