package au.org.ala.profile.util

import au.org.ala.profile.*

class CloneAndDraftUtil {

    static void updateProfileFromDraft(Profile profile) {
        if (!profile.draft) {
            return
        }

        profile.uuid = profile.draft.uuid
        profile.scientificName = profile.draft.scientificName
        profile.scientificNameLower = profile.draft.scientificNameLower
        profile.nameAuthor = profile.draft.nameAuthor
        profile.fullName = profile.draft.fullName
        profile.taxonomyTree = profile.draft.taxonomyTree
        profile.matchedName = cloneName(profile.draft.matchedName)
        profile.manuallyMatchedName = profile.draft.manuallyMatchedName
        profile.rank = profile.draft.rank
        profile.nslNameIdentifier = profile.draft.nslNameIdentifier
        profile.nslNomenclatureIdentifier = profile.draft.nslNomenclatureIdentifier
        profile.primaryImage = profile.draft.primaryImage
        profile.primaryVideo = profile.draft.primaryVideo
        profile.primaryAudio = profile.draft.primaryAudio
        profile.showLinkedOpusAttributes = profile.draft.showLinkedOpusAttributes
        profile.occurrenceQuery = profile.draft.occurrenceQuery
        profile.isCustomMapConfig = profile.draft.isCustomMapConfig
        profile.imageSettings = cloneImageSettings(profile.draft.imageSettings)
        profile.specimenIds = profile.draft.specimenIds?.collect()
        profile.authorship = profile.draft.authorship?.collect { cloneAuthorship(it) }
        profile.classification = profile.draft.classification?.collect { cloneClassification(it) }
        profile.links = profile.draft.links?.collect { cloneLink(it) }
        profile.bhlLinks = profile.draft.bhlLinks?.collect { cloneLink(it) }
        profile.bibliography = profile.draft.bibliography?.collect { cloneBibliography(it) }
        profile.documents = profile.draft.documents?.collect { cloneDocuments(it) }
        profile.privateImages = profile.draft.privateImages?.collect { cloneImage(it) }
        profile.attachments = profile.draft.attachments?.collect { cloneAttachment(it) }
        profile.manualClassification = profile.draft.manualClassification
        profile.profileStatus = profile.draft.profileStatus

        // Update the existing record rather than replacing it with the draft object,
        // otherwise, the hibernate dirty check will fail (dirty check looks for changes since the entity
        // was read from the DB, but the draft object is not treated as an entity (because it is an
        // embedded Mongo subdocument))
//        def attributes = profile.draft.attributes.collect()
//        attributes?.each {
//            String uuid = it.uuid
//            Attribute existing = profile.attributes.find { it.uuid == uuid }
//            if (existing) {
////                existing.title = it.title
////                existing.text = it.text
//            } else {
//                Attribute at = cloneAttribute(it)
//                profile.addToAttributes(at)
//            }
//        }

        profile.draft.attributes.each {
            String uuid = it.uuid
            Attribute existing = profile.attributes.find { it.uuid == uuid }
            if (existing) {
                existing.title = it.title
                existing.text = it.text
                existing.numbers = it.numbers
                existing.numberRange = it.numberRange
                existing.constraintList = it.constraintList
            } else {
                it.profile = profile
                profile.addToAttributes(it)
            }
        }

//        profile.markDirty('attributes')
    }

    static DraftProfile createDraft(Profile profile) {
        DraftProfile clone = new DraftProfile()
        clone.draftDate = new Date()
        clone.uuid = profile.uuid
        clone.scientificName = profile.scientificName
        clone.scientificNameLower = profile.scientificNameLower
        clone.nameAuthor = profile.nameAuthor
        clone.fullName = profile.fullName
        clone.matchedName = cloneName(profile.matchedName)
        clone.manuallyMatchedName = profile.manuallyMatchedName
        clone.rank = profile.rank
        clone.guid = profile.guid
        clone.taxonomyTree = profile.taxonomyTree
        clone.nslNameIdentifier = profile.nslNameIdentifier
        clone.nslNomenclatureIdentifier = profile.nslNomenclatureIdentifier
        clone.primaryImage = profile.primaryImage
        clone.primaryVideo = profile.primaryVideo
        clone.primaryAudio = profile.primaryAudio
        clone.showLinkedOpusAttributes = profile.showLinkedOpusAttributes
        clone.occurrenceQuery = profile.occurrenceQuery
        clone.isCustomMapConfig = profile.isCustomMapConfig
        clone.imageSettings = cloneImageSettings(profile.imageSettings)
        clone.specimenIds = profile.specimenIds?.collect()
        clone.authorship = profile.authorship?.collect { cloneAuthorship(it) }
        clone.classification = profile.classification?.collect { cloneClassification(it) }
        clone.manualClassification = profile.manualClassification
        clone.profileStatus = profile.profileStatus
        clone.links = profile.links?.collect { cloneLink(it) }
        clone.bhlLinks = profile.bhlLinks?.collect { cloneLink(it) }
        clone.bibliography = profile.bibliography?.collect { cloneBibliography(it) }
        clone.documents = profile.documents?.collect { cloneDocuments(it) }
        clone.attributes = profile.attributes?.collect { cloneAttribute(it) }
        clone.privateImages = profile.privateImages?.collect { cloneImage(it) }
        clone.attachments = profile.attachments?.collect { cloneAttachment(it) }

        clone.dateCreated = profile.dateCreated

        clone
    }

    static Name cloneName(Name source) {
        Name clone = null
        if (source != null) {
            clone = new Name()
            clone.scientificName = source.scientificName
            clone.nameAuthor = source.nameAuthor
            clone.fullName = source.fullName
            clone.guid = source.guid
        }
        clone
    }

    static Authorship cloneAuthorship(Authorship source) {
        Authorship clone = new Authorship()
        clone.category = source.category
        clone.text = source.text

        clone
    }

    static Classification cloneClassification(Classification source) {
        Classification clone = new Classification()
        clone.name = source.name
        clone.guid = source.guid
        clone.rank = source.rank

        clone
    }

    static Bibliography cloneBibliography(Bibliography source, boolean includeIds = true) {
        Bibliography clone = new Bibliography()
        if (includeIds) {
            clone.uuid = source.uuid
        }
        clone.text = source.text
        clone.order = source.order

        clone
    }

    static Document cloneDocuments(Document source, boolean includeIds = true) {
        Document clone = new Document()
        if (includeIds) {
            clone.documentId = source.documentId
        }
        clone.attribution = source.attribution
        clone.licence = source.licence
        clone.name = source.name
        clone.url = source.url
        clone.type = source.type

        clone
    }

    static Link cloneLink(Link source, boolean includeIds = true) {
        Link clone = new Link()
        if (includeIds) {
            clone.uuid = source.uuid
        }
        clone.url = source.url
        clone.title = source.title
        clone.description = source.description
        clone.doi = source.doi
        clone.edition = source.edition
        clone.publisherName = source.publisherName
        clone.fullTitle = source.fullTitle

        clone
    }

    static Attribute cloneAttribute(Attribute source, boolean includeIds = true) {
        Attribute clone = new Attribute()

        if (includeIds) {
            clone.uuid = source.uuid
            clone.id = source.id
        }
        clone.text = source.text
        clone.numbers = source.numbers
        clone.numberRange = source.numberRange
        clone.constraintList = source.constraintList
        clone.source = source.source

        // title, original, creators & editors are not cloned - copy by reference, not value
        clone.title = source.title
        clone.original = source.original
        clone.creators = source.creators?.collect()
        clone.editors = source.editors?.collect()

        clone
    }

    static LocalImage cloneImage(LocalImage source) {
        LocalImage clone = new LocalImage()

        clone.creator = source.creator
        clone.created = source.created
        clone.description = source.description
        clone.imageId = source.imageId
        clone.licence = source.licence
        clone.originalFileName = source.originalFileName
        clone.rights = source.rights
        clone.rightsHolder = source.rightsHolder
        clone.title = source.title
        clone.contentType = source.contentType

        clone
    }

    static Attachment cloneAttachment(Attachment source) {
        Attachment clone = new Attachment()

        clone.creator = source.creator
        clone.createdDate = source.createdDate
        clone.description = source.description
        clone.contentType = source.contentType
        clone.licence = source.licence
        clone.filename = source.filename
        clone.rights = source.rights
        clone.rightsHolder = source.rightsHolder
        clone.title = source.title
        clone.uuid = source.uuid
        clone.url = source.url
        clone.category = source.category

        clone
    }

    static Map cloneImageSettings (Map imageSettings) {
        Map clone = new HashMap<String, ImageSettings>()
//        imageSettings?.each { String key, ImageSettings imageSetting ->
        imageSettings?.each { String key, imageSetting ->
            ImageSettings cloneImageSetting = new ImageSettings()
            cloneImageSetting.caption = imageSetting.caption
            cloneImageSetting.imageDisplayOption = imageSetting.imageDisplayOption
            clone[key] = cloneImageSetting
        }

        clone
    }
}
