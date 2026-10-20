package au.org.ala.profile

import au.org.ala.profile.util.Utils
import grails.converters.JSON
import org.bson.types.ObjectId

class ExportService extends BaseDataAccessService {

    def grailsApplication

    int getProfileCount(Opus opus, boolean includeArchived) {
        includeArchived ? Profile.countByOpus(opus) : Profile.countByOpusAndArchivedDateIsNull(opus)
    }

    void exportProfiles(OutputStream outputStream, List<String> opusIds, List<String> collectionTags, List<String> profileNames, List<String> guids, boolean summary = false) {
        List<Opus> collections = []
        if (opusIds) {
            collections = Opus.findAllByUuidInListOrShortNameInListOrDataResourceUidInList(opusIds, opusIds, opusIds)
            // this is a publicly accessible function, so make sure we have no private collections
            collections = collections?.findAll { !it.privateCollection }
        }

        if (collectionTags) {
            collectionTags = collectionTags*.toUpperCase()
            collections = Opus.list().findAll { opus -> opus.tags?.find { tag -> collectionTags.contains(tag.abbrev) } }
        }

        if (!opusIds && !collectionTags) {
            collections = Opus.findAllByPrivateCollection(false)
        }

        if (collections) {
            Map query = [:]
            if (collections) {
                query << [opus: [$in: collections*.id]]
            }

            if (profileNames && guids) {
                query << [$or: [
                        [scientificName: [$in: profileNames]],
                        [guid: [$in: guids]]
                ]]
            } else if (profileNames) {
                query << [scientificName: [$in: profileNames]]
            } else if (guids) {
                query << [guid: [$in: guids]]
            }
            log.debug((query as JSON).toString(true))

            int count = Profile.collection.count(query)
            def profiles = Profile.collection.find(query).sort([scientificName: 1])

            export(outputStream, collections, count, profiles.iterator(), summary)
        }
    }

    void exportCollection(OutputStream outputStream, Opus opus, int max, int offset, boolean summary = false, boolean includeArchived = false) {
        Map query = [opus: opus.id]
        if (!includeArchived) {
            query << [archivedDate: null]
        }

        // result sets can be large, so we don't want to load them all into memory then transform them.
        // Therefore, write objects from the db cursor (stream) directly to the output stream, transforming as
        // we go.
        def profiles = Profile.collection.find(query).sort([scientificName: 1]).skip(offset).limit(max)

        int totalProfileCount = getProfileCount(opus, includeArchived)

        export(outputStream, [opus], totalProfileCount, profiles.iterator(), summary)
    }

    private void export(OutputStream outputStream, List<Opus> collections, int total, def profiles, boolean summary) {
        Map attributeTitles = [:]
        Map authorCategories = [:]
        collections?.each {
            Map attributeTerms = getVocabTerms(it.attributeVocabUuid)
            if (attributeTerms) {
                attributeTitles.putAll(attributeTerms)
            }
            Map authorshipTerms = getVocabTerms(it.authorshipVocabUuid)
            if (authorshipTerms) {
                authorCategories.putAll(authorshipTerms)
            }
        }

        Map<ObjectId, Opus> collectionMap = collections.collectEntries { [(it.id): it] }

        long start = System.currentTimeMillis()
        log.debug("Starting")
        int count = 0
        outputStream.with { writer ->
            writer << "{ \"total\": ${total}, "
            writer << ' "profiles": ['
            while (profiles.hasNext()) {
                Map data = profiles.next()
                Opus opus = collectionMap[data.opus]
                Map profile = [
                        id               : data.uuid,
                        name             : data.scientificName,
                        fullName         : data.fullName,
                        rank             : data.rank,
                        matchedName      : data.matchedName,
                        dateCreated      : data.dateCreated,
                        lastUpdate       : data.lastUpdated,
                        nameAuthor       : data.nameAuthor,
                        nslNameId        : data.nslNameIdentifier,
                        nslNomenclatureId: data.nslNomenclatureIdentifier,
                        attributes       : [],
                        thumbnailUrl     : constructThumbnailUrl(data, opus),
                        url              : "${grailsApplication.config.getProperty('profile.hub.base.url')}/opus/${opus.shortName ?: opus.uuid}/profile/${data.scientificName}".toString(),
                        mainVideo        : null,
                        mainAudio        : null
                ]

                profile.collection = [
                        id       : opus.uuid,
                        title    : opus.title,
                        shortName: opus.shortName,
                        url      : "${grailsApplication.config.getProperty('profile.hub.base.url')}/opus/${opus.shortName ?: opus.uuid}".toString()
                ]

                if (!summary) {
                    profile.taxonomy = [source: data.taxonomyTree, classification: data.classification]
                    profile.acknowledgements = data.authorship?.collectEntries {
                        [(authorCategories[it.category].name): it.text]
                    }
                    profile.links = data.links?.collectEntries {
                        [url: it.url, description: it.description, name: it.name]
                    }
                    profile.bhlLinks = data.links?.collectEntries {
                        [url: it.url, description: it.description, name: it.name]
                    }
                    profile.bibliography = data.bibliography?.collect { it.text }
                    profile.specimens = data.specimens?.collect { it }
                    profile.occurrenceQuery = data.occurrenceQuery
                }

                def attributeCursor = Attribute.collection.find([profile: data._id]).iterator()
                while (attributeCursor.hasNext()) {
                    Map attribute = attributeCursor.next()

                    Term title = attributeTitles[attribute.title]

                    if (!summary || title.containsName || title.summary) {
                        profile.attributes << [
                                id     : attribute.uuid,
                                title  : title.name,
                                text   : title.containsName ? Utils.cleanupText(attribute.text) : attribute.text,
                                name   : title.containsName,
                                summary: title.summary
                        ]
                    }
                }

                profile.documents = data.documents

                if (data.primaryVideo) {
                    def pv = data.primaryVideo
                    def video = data.documents?.find { it.documentId == pv }
                    if (video) {
                        profile.mainVideo = [
                                id: video.documentId,
                                name: video.name,
                                attribution: video.attribution,
                                license: video.license,
                                url: video.url
                        ]
                    }
                }

                if (data.primaryAudio) {
                    def pa = data.primaryAudio
                    def audio = data.documents?.find { it.documentId == pa }
                    if (audio) {
                        profile.mainAudio = [
                                id: audio.documentId,
                                name: audio.name,
                                attribution: audio.attribution,
                                license: audio.license,
                                url: audio.url
                        ]
                    }
                }

                writer << (profile as JSON).toString()
                if (profiles.hasNext()) {
                    writer << ","
                }
                count++
            }

            writer << "]}"
        }
        log.debug("Finished exporting ${count} profiles in ${System.currentTimeMillis() - start}ms")
    }

    private Map<String, Term> getVocabTerms(String vocabId) {
        Vocab.findByUuid(vocabId)?.terms?.collectEntries { [it.id, it] }
    }

    private String constructThumbnailUrl(profile, Opus opus) {
        String url

        if (profile.primaryImage) {
            def image = profile.privateImages.find { it.imageId == profile.primaryImage }
            if (image) {
                // the primary image is a local image
                url = "${grailsApplication.config.getProperty('profile.hub.base.url')}/opus/${opus.uuid}/profile/${profile.uuid}/image/thumbnail/${profile.primaryImage}.${Utils.getFileExtension(image.originalFileName)}?type=PRIVATE"
            } else {
                // the primary image is from the ALA Image Service
                url = "${grailsApplication.config.getProperty('images.base.url')}/image/proxyImageThumbnailLarge?imageId=${profile.primaryImage}"
            }
        } else if (profile.privateImages) {
            // if there is no primary image but there are local images (i.e. the editor has provided images that are not
            // in the ALA image service), then we use the first local image as the primary.
            def image = profile.privateImages[0]
            url = "${grailsApplication.config.getProperty('profile.hub.base.url')}/opus/${opus.uuid}/profile/${profile.uuid}/image/thumbnail/${profile.primaryImage}.${Utils.getFileExtension(image.originalFileName)}?type=PRIVATE"
        } else {
            // If there is no explicitly specified primary image and we have no local images, then leave the url blank:
            // the calling system can retrieve an image directly from the image service if one is required in this case.
            url = null
        }

        url
    }
}
