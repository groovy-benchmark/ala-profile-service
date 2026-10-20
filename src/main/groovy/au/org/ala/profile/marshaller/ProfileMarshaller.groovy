package au.org.ala.profile.marshaller

import au.org.ala.profile.Profile
import au.org.ala.profile.util.Utils
import com.google.common.base.Stopwatch
import grails.converters.JSON
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ProfileMarshaller {
    Logger log = LoggerFactory.getLogger(ProfileMarshaller.class)
    void register() {
        JSON.registerObjectMarshaller(Profile) { Profile profile ->
            Stopwatch sw = new Stopwatch().start()
            def value = [
                    uuid                     : profile.uuid,
                    guid                     : profile.guid && profile.guid != "null" ? "${profile.guid}" : "",
                    nslNameIdentifier        : profile.nslNameIdentifier,
                    nslNomenclatureIdentifier: profile.nslNomenclatureIdentifier,
                    nslProtologue            : profile.nslProtologue,
                    dataResourceUid          : profile.opus?.dataResourceUid,
                    opusId                   : profile.opus?.uuid,
                    opusName                 : profile.opus?.title,
                    opusShortName            : profile.opus?.shortName,
                    privateMode              : profile.privateMode,
                    rank                     : profile.rank,
                    scientificName           : profile.scientificName,
                    nameAuthor               : profile.nameAuthor,
                    fullName                 : profile.fullName,
                    matchedName              : profile.matchedName ? [scientificName: profile.matchedName.scientificName,
                                                                      nameAuthor    : profile.matchedName.nameAuthor,
                                                                      fullName      : profile.matchedName.fullName,
                                                                      guid          : profile.matchedName.guid] : [:],
                    manuallyMatchedName      : profile.manuallyMatchedName,
                    classification           : profile.classification,
                    manualClassification     : profile.manualClassification,
                    taxonomyTree             : profile.taxonomyTree,
                    attributes               : profile.attributes?.sort() ?: [],
                    links                    : profile.links ?: [],
                    bhl                      : profile.bhlLinks ?: [],
                    primaryImage             : profile.primaryImage,
                    primaryAudio             : profile.primaryAudio,
                    primaryVideo             : profile.primaryVideo,
                    imageSettings            : profile.imageSettings?.collect { k, v -> [imageId: k, caption: v?.caption, displayOption: v?.imageDisplayOption?.toString()] } ?: [],
                    stagedImages             : profile.stagedImages ?: [],
                    privateImages            : profile.privateImages ?: [],
                    attachments              : profile.attachments ?: [],
                    specimenIds              : profile.specimenIds ?: [],
                    authorship               : profile.authorship?.collect {
                        [category: it.category.name, text: it.text]
                    } ?: [],
                    bibliography             : profile.bibliography?.collect {
                        [uuid: it.uuid, text: it.text, plainText: Utils.cleanupText(it.text), order: it.order]
                    }?.sort { it.order } ?: [],
                    documents             : profile.documents?.collect {
                        [documentId: it.documentId, name: it.name, attribution: it.attribution, licence: it.licence, url: it.url, type: it.type ]
                    } ?: [],
                    publications             : profile.publications?.sort { left, right -> right.publicationDate <=> left.publicationDate } ?: [],
                    lastAttributeChange      : profile.lastAttributeChange,
                    createdDate              : profile.dateCreated,
                    createdBy                : profile.createdBy,
                    lastUpdated              : profile.lastUpdated,
                    lastUpdatedBy            : profile.lastUpdatedBy,
                    archiveComment           : profile.archiveComment,
                    archivedDate             : profile.archivedDate,
                    archivedBy               : profile.archivedBy,
                    archivedWithName         : profile.archivedWithName,
                    showLinkedOpusAttributes : profile.showLinkedOpusAttributes,
                    lastPublished            : profile.lastPublished,
                    occurrenceQuery          : profile.occurrenceQuery,
                    isCustomMapConfig        : profile.isCustomMapConfig,
                    profileStatus            : profile.profileStatus,
                    profileSettings          : profile.profileSettings? [ autoFormatProfileName: profile.profileSettings.autoFormatProfileName, formattedNameText: profile.profileSettings.formattedNameText ]: null
            ]

            log.trace("profileMarshaller() - Get profile by UUID ${profile.uuid}: $sw")
            return value
        }
    }
}
