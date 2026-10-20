package au.org.ala.profile.marshaller

import au.org.ala.profile.DataResourceConfig
import au.org.ala.profile.HelpLink
import au.org.ala.profile.Opus
import au.org.ala.profile.OpusLayoutConfig
import au.org.ala.profile.Theme
import au.org.ala.profile.util.DataResourceOption
import au.org.ala.profile.util.ImageOption
import au.org.ala.profile.util.ShareRequestStatus
import com.google.common.base.Stopwatch
import grails.converters.JSON
import org.slf4j.Logger
import org.slf4j.LoggerFactory


class OpusMarshaller {
    Logger log = LoggerFactory.getLogger(OpusMarshaller.class)
    void register() {
        JSON.registerObjectMarshaller(Opus) { Opus opus ->
            Stopwatch sw = new Stopwatch().start()
            def value = [
                    uuid                       : opus.uuid,
                    dataResourceUid            : opus.dataResourceUid,
                    title                      : opus.title,
                    shortName                  : opus.shortName,
                    description                : opus.description,
                    masterListUid              : opus.masterListUid,
                    dataResourceConfig         : marshalDataResourceConfig(opus.dataResourceConfig),
                    approvedImageOption        : opus.approvedImageOption?.name() ?: ImageOption.INCLUDE.name(),
                    approvedLists              : opus.approvedLists ?: [],
                    featureLists               : opus.featureLists ?: [],
                    featureListSectionName     : opus.featureListSectionName,
                    brandingConfig             : opus.brandingConfig ?: [:],
                    profileLayoutConfig        : opus.profileLayoutConfig ?: [:],
                    opusLayoutConfig           : opus.opusLayoutConfig ?: new OpusLayoutConfig(),
                    theme                      : opus.theme ?: new Theme(),
                    help                      : opus.help ?: new HelpLink(),
                    keybaseProjectId           : opus.keybaseProjectId,
                    keybaseKeyId               : opus.keybaseKeyId,
                    attributeVocabUuid         : opus.attributeVocabUuid,
                    authorshipVocabUuid        : opus.authorshipVocabUuid,
                    groupVocabUuid             : opus.groupVocabUuid,
                    autoDraftProfiles          : opus.autoDraftProfiles,
                    glossaryUuid               : opus.glossary?.uuid,
                    attachments                : opus.attachments ?: [],
                    enablePhyloUpload          : opus.enablePhyloUpload != null ? opus.enableKeyUpload : true,
                    enableOccurrenceUpload     : opus.enableOccurrenceUpload != null ? opus.enableKeyUpload : true,
                    enableTaxaUpload           : opus.enableTaxaUpload != null ? opus.enableKeyUpload : true,
                    enableKeyUpload            : opus.enableKeyUpload != null ? opus.enableKeyUpload : true,
                    privateCollection          : opus.privateCollection != null ? opus.privateCollection : false,
                    keepImagesPrivate          : opus.keepImagesPrivate ?: false,
                    usePrivateRecordData       : opus.usePrivateRecordData ?: false,
                    mapConfig                  : opus.mapConfig ?: [:],
                    supportingOpuses           : marshalSupportingOpuses(opus.supportingOpuses?.findAll {
                        it.requestStatus == ShareRequestStatus.ACCEPTED
                    }),
                    requestedSupportingOpuses  : marshalSupportingOpuses(opus.supportingOpuses?.findAll {
                        it.requestStatus != ShareRequestStatus.ACCEPTED
                    }),
                    sharingDataWith            : marshalSupportingOpuses(opus.sharingDataWith),
                    autoApproveShareRequests   : opus.autoApproveShareRequests,
                    allowCopyFromLinkedOpus    : opus.allowCopyFromLinkedOpus != null ? opus.allowCopyFromLinkedOpus : false,
                    showLinkedOpusAttributes   : opus.showLinkedOpusAttributes != null ? opus.showLinkedOpusAttributes : false,
                    allowFineGrainedAttribution: opus.allowFineGrainedAttribution != null ? opus.allowFineGrainedAttribution : true,
                    authorities                : opus.authorities?.collect {
                        [uuid: it.uuid, userId: it.user.userId, name: it.user.name, role: it.role.toString(), notes: it.notes]
                    }?:[],
                    copyrightText              : opus.copyrightText,
                    footerText                 : opus.footerText,
                    contact                    : [twitter : opus.twitter,
                                                  email   : opus.email,
                                                  facebook: opus.facebook],
                    hasAboutPage               : opus.aboutHtml != null,
                    profileCount               : opus.profileCount,
                    florulaListId              : opus.florulaListId ?: '',
                    aboutHtml                  : opus.aboutHtml,
                    citationHtml               : opus.citationHtml,
                    citationProfile            : opus.citationProfile,
                    accessToken                : opus.accessToken,
                    tags                       : opus.tags?.collect {
                        [uuid: it.uuid, colour: it.colour, name: it.name, abbrev: it.abbrev]
                    } ?: [],
                    additionalStatuses         : opus.additionalStatuses ?: [],
                    dateCreated                : opus.dateCreated?.time,
                    lastUpdated                : opus.lastUpdated?.time
            ]
            log.trace("opusMarshaller() - Get opus by UUID ${opus.uuid}: $sw")
            return value
        }
    }

    def static marshalSupportingOpuses(List opuses) {
        opuses ?
                opuses.collect {
                    [uuid: it.uuid, title: it.title, requestStatus: it.requestStatus.toString()]
                } : []
    }


    private static Map marshalDataResourceConfig(DataResourceConfig config) {
        Map result = [:]
        if (config) {
            result.recordResourceOption = config.recordResourceOption?.name() ?: DataResourceOption.NONE.name()
            result.imageResourceOption = config.imageResourceOption?.name() ?: DataResourceOption.NONE.name()
            result.imageSources = config.imageSources
            result.recordSources = config.recordSources
            result.privateRecordSources = config.privateRecordSources
        } else {
            result.imageSources = []
            result.recordSources = []
            result.privateRecordSources = []
            result.imageResourceOption = DataResourceOption.NONE.name()
            result.recordResourceOption = DataResourceOption.NONE.name()
        }

        result
    }
}
