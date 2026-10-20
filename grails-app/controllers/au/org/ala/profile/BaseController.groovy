package au.org.ala.profile

import au.org.ala.profile.BasicWSController
import au.org.ala.profile.util.DataResourceOption
import au.org.ala.web.UserDetails
import com.google.common.base.Stopwatch

import static AuditInterceptor.REQUEST_USER_DETAILS_KEY

class BaseController extends BasicWSController {

    ProfileService profileService
    OpusService opusService

    Profile getProfile() {
        Stopwatch sw = new Stopwatch().start()

        Profile profile
        Opus opus

        if (au.org.ala.profile.util.Utils.isUuid(params.profileId)) {
            profile = Profile.findByUuid(params.profileId)
            opus = profile?.opus
            log.trace("getProfile() - Get profile by UUID ${params.profileId}: $sw")
            sw.reset().start()
        } else {
            opus = getOpus()
            profile = Profile.findByOpusAndScientificNameIlike(opus.id, params.profileId)
            log.trace("getProfile() - Get profile by opus ${opus.uuid} and sci name ${params.profileId}: $sw")
            sw.reset().start()

            // names can be changed, so if there is no profile with the name, check for a draft with that name,
            // but only if the 'latest' flag is true
            if (!profile && params.latest?.toBoolean()) {
                List matches = Profile.withCriteria {
                    eq "opus", opus.id
                    ilike "draft.scientificName", params.profileId
                }
                profile = matches.isEmpty() ? null : matches.first()

                log.trace("getProfile() - Get profile by with changed name: $sw")
                sw.reset().start()
            }
        }

        if (!profile) {
            return null
        } else if (!opusService.isProfileOnMasterList(opus, profile)) {
            log.debug("${opus.shortName ?: opus.uuid}: ${profile.scientificName} was found but is filtered out")
            return null
        }

        Closure expandConstraintList = { attr ->
            if (attr.constraintList) {
                attr.constraintListExpanded = Term.findAllByUuidInList(attr.constraintList)
            }
        }

        // expand constraint list
        profile.attributes?.each expandConstraintList
        profile.draft?.attributes?.each expandConstraintList

        // if occurrenceQuery is not custom configured by user, then use default occurrenceQuery for opus/profile combo.
        if(!profile.isCustomMapConfig){
            profile.occurrenceQuery = createOccurrenceQuery(profile)
        }

        if(profile.draft?.isCustomMapConfig == false){
            profile.draft?.occurrenceQuery = createOccurrenceQuery(profile, true)
        }

        profile
    }

    private String createOccurrenceQuery(profile, boolean draft = false) {
        Opus opus = profile.opus
        profile = draft ? profile.draft : profile

        String result = ""

        if (profile && opus) {
            String query = ""

            if (profile.guid && profile.guid != "null") {
                query += "${"lsid:${profile.guid}"}"
            } else {
                query += profile.scientificName
            }

            String occurrenceQuery = query

            if (opus.usePrivateRecordData) {
                DataResourceConfig config = opus.dataResourceConfig
                if (config?.privateRecordSources) {
                    occurrenceQuery = "${query} AND (data_resource_uid:${config.privateRecordSources?.join(" OR data_resource_uid:")})"
                }
            } else if (opus.dataResourceConfig) {
                DataResourceConfig config = opus.dataResourceConfig
                switch (config.recordResourceOption) {
                    case DataResourceOption.ALL:
                        occurrenceQuery = query
                        break
                    case DataResourceOption.NONE:
                        occurrenceQuery = "${query} AND data_resource_uid:${opus.dataResourceUid}"
                        break
                    case DataResourceOption.HUBS:
                        occurrenceQuery = "${query} AND (data_resource_uid:${opus.dataResourceUid} OR data_hub_uid:${config.recordSources?.join(" OR data_hub_uid:")})"
                        break
                    case DataResourceOption.RESOURCES:
                        occurrenceQuery = "${query} AND (data_resource_uid:${opus.dataResourceUid} OR data_resource_uid:${config.recordSources?.join(" OR data_resource_uid:")})"
                        break
                }
            }

            result = "q=${au.org.ala.profile.util.Utils.enc(occurrenceQuery)}"
        }

        result
    }

    Opus getOpus() {
        Stopwatch sw = new Stopwatch().start()
        Opus opus
        if (au.org.ala.profile.util.Utils.isUuid(params.opusId)) {
            opus = Opus.findByUuid(params.opusId)
            log.trace("getOpus() - Get opus by UUID ${params.opusId}: $sw")
        } else {
            opus = Opus.findByShortName(params.opusId.toLowerCase())
            log.trace("getOpus() - Get opus by short name ${params.opusId}: $sw")
        }
        opus
    }

    UserDetails currentUser() {
        return (UserDetails) request.getAttribute(REQUEST_USER_DETAILS_KEY)
    }
}
