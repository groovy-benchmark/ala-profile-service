package au.org.ala.profile

import org.springframework.scheduling.annotation.Async

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

import static au.org.ala.profile.util.Utils.*
import static groovyx.gpars.GParsPool.withPool

class AdminService extends BaseDataAccessService {
    static final int THREAD_POOL_SIZE = 10

    NameService nameService
    ProfileService profileService

    @Async
    NameRematch rematchAllNames(List<String> opusIds) {
        Opus.withSession {
            long start = System.currentTimeMillis()

            List<Opus> collections
            if (opusIds) {
                collections = opusIds.collect { Opus.findByUuid(it) }
            } else {
                collections = Opus.list()
            }

            Map<String, Map> results = [:] as ConcurrentHashMap

            NameRematch rematch = new NameRematch(uuid: UUID.randomUUID().toString(), startDate: new Date(), opusIds: opusIds)
            save rematch
            AtomicInteger changed = new AtomicInteger(0)
            AtomicInteger total = new AtomicInteger(0)

            collections.each { Opus opus ->
                List<Profile> profiles = Profile.findAllByOpus(opus)

                log.info("Processing Opus ${opus.title} (${opus.uuid}) with ${profiles.size()} profiles...")
                results[opus.uuid] = [:] as ConcurrentHashMap
                results[opus.uuid].totalProfiles = profiles.size()
                results[opus.uuid].opusTitle = opus.title
                results[opus.uuid].profilesUpdated = [:] as ConcurrentHashMap

                withPool(THREAD_POOL_SIZE) {
                    profiles.eachParallel { Profile profile ->
                        try {
                            Boolean isDirty = false
                            isDirty = updateProfileWithRematchedName(profile, isDirty, results, opus)

                            if(profile.draft){
                                isDirty = updateProfileWithRematchedName(profile.draft, isDirty, results, opus)
                            }

                            if(isDirty){
                                changed.incrementAndGet()
                            }

                            // Doing this check independent of name change since this field was not kept updated with previous
                            // name changes. So it is possible that profile.guid and lsid in occurrenceQuery are out of sync.
                            if(profile.occurrenceQuery?.contains("lsid")){
                                isDirty = updateOccurrenceQuery(profile, isDirty)
                            }

                            // make sure occurrence query field in profile's draft version is also updated
                            if(profile.draft?.occurrenceQuery?.contains("lsid")){
                                // make sure draft's guid is used in occurrenceQuery. It is possible profile and draft
                                // have different guid.
                                isDirty = updateOccurrenceQuery(profile.draft, isDirty)
                            }

                            if(isDirty){
                                Profile.withSession { session ->
                                    session.attach(profile)
                                    save profile
                                }
                            }
                        } catch (Exception e) {
                            log.error("Failed to match ${profile.scientificName}", e)
                            results[opus.uuid] << [(profile.uuid): [error: "Failed to match ${profile.scientificName}: ${e.message}. See log for stacktrace."]]
                        }
                        total.incrementAndGet()
                    }
                }
            }

            log.info("Name rematched finished - updated ${changed.get()} profiles in ${System.currentTimeMillis() - start}ms")
            rematch.numberOfProfilesChanged = changed.get()
            rematch.numberOfProfilesChecked = total.get()
            rematch.results = results
            rematch.endDate = new Date()
            save rematch
            rematch
        }
    }

    boolean updateOccurrenceQuery(Object profile, boolean isDirty) {
        String name = profileService.getProfileIdentifierForMapQuery(profile)
        String query = profile.occurrenceQuery
        if (!query?.contains(name)) {
            profile.occurrenceQuery = updateLSIDInQueryString(name, query);
            isDirty = true
        }
        isDirty
    }

    boolean updateProfileWithRematchedName(Object profile, boolean isDirty, Map results, Opus opus) {
        Map<String, String> classification = profile.classification?.collectEntries {
            [(it.rank): it.name]
        } ?: [:]

        Map newMatchedName
        if (profile.manuallyMatchedName) {
            if (profile.matchedName) {
                newMatchedName = nameService.matchName(profile.matchedName.scientificName, classification, profile.matchedName.guid)
            } else {
                log.info("Skip rematch, profile does not have a matched name: ${profile}")
            }
        } else {
            newMatchedName = nameService.matchName(profile.scientificName, classification)
        }

        if (profile.matchedName?.guid != newMatchedName?.guid) {
            results[opus.uuid].profilesUpdated << [
                    (profile.uuid): [
                            'profileName': profile.scientificName,
                            'old'        : [guid: profile.matchedName?.guid, name: profile.matchedName?.fullName],
                            'new'        : [guid: newMatchedName?.guid, name: newMatchedName?.fullName]
                    ]
            ]
            profile.matchedName = new Name(newMatchedName)
            profile.guid = newMatchedName?.guid

            isDirty = true
        }

        isDirty
    }

    private String updateLSIDInQueryString(String lsid, String query){
        if(lsid && query){
            Map parsedQuery = parseQueryString(query)
            if(parsedQuery.q){
                parsedQuery.q[0] = parsedQuery.q[0].replaceFirst("lsid:[^ \$]+", lsid)
            }

            createQueryString(parsedQuery);
        }
    }

    Tag createTag(Map properties) {
        properties.abbrev = properties.abbrev.toUpperCase()
        Tag tag = new Tag(properties)
        tag.uuid = UUID.randomUUID().toString()

        boolean success = save tag

        if (!success) {
            tag = null
        }

        tag
    }

    Tag updateTag(String tagId, Map properties) {
        Tag tag = Tag.findByUuid(tagId)

        if (tag) {
            tag.colour = properties.colour
            tag.name = properties.name
            tag.abbrev = properties.abbrev.toUpperCase()

            save tag
        }

        tag
    }

    void deleteTag(String tagId) {
        Tag tag = Tag.findByUuid(tagId)

        if (tag) {
            tag.delete()

            Opus.list().each { opus ->
                Tag t = opus.tags?.find {
                    if (it) {
                        it.uuid == tagId
                    }
                }
                if (t) {
                    opus.tags.remove(t)
                    save opus
                }
                else if (opus.tags?.contains(null)) {
                    opus.tags.remove(null)
                    save opus
                }
            }
        }
    }
}
