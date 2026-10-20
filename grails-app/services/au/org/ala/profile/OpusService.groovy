package au.org.ala.profile

import au.org.ala.profile.security.Role
import au.org.ala.profile.util.*
import com.mongodb.DBObject
import grails.gorm.transactions.Transactional
import grails.plugin.dropwizard.metrics.meters.Metered
import grails.plugin.dropwizard.metrics.timers.Timed
import org.grails.datastore.mapping.mongo.query.MongoQuery
import org.springframework.web.multipart.MultipartFile

import java.text.SimpleDateFormat

import static au.org.ala.profile.util.Utils.toBooleanWithDefault

@Transactional
class OpusService extends BaseDataAccessService {

    EmailService emailService
    UserService userService
    AttachmentService attachmentService
    MasterListService masterListService
    def grailsApplication
    def groovyPageRenderer

    ImportService getImportService() {
        grailsApplication.mainContext.importService
    }

    Opus createOpus(json) {
        log.debug("Creating new opus record")
        Opus opus = new Opus(json)

        Vocab attributeVocab = new Vocab(name: "${opus.title} Attribute Vocabulary", strict: false)
        save attributeVocab
        Vocab authorshipVocab = new Vocab(name: "${opus.title} Authorship Vocabulary", strict: false)
        Term authorTerm = new Term(name: "Author", vocab: authorshipVocab, uuid: UUID.randomUUID(), required: true)
        authorshipVocab.addToTerms(authorTerm)
        save authorshipVocab
        Vocab groupVocab = new Vocab(name: "${opus.title} Group Vocabulary", strict: true)
        save groupVocab

        opus.attributeVocabUuid = attributeVocab.uuid
        opus.authorshipVocabUuid = authorshipVocab.uuid
        opus.groupVocabUuid = groupVocab.uuid

        Glossary glossary = new Glossary()
        save glossary

        opus.glossary = glossary

        boolean success = save opus
        if (success) {
            opus
        } else {
            null
        }
    }

    Opus updateOpus(opusId, json) {
        Opus opus = Opus.findByUuid(opusId)

        opus.title = json.title ? json.title : null
        opus.shortName = json.shortName ? json.shortName.toLowerCase() : null
        opus.description = json.description ? json.description : null

        if (json.approvedLists) {
            if (opus.approvedLists) {
                opus.approvedLists.clear()
            } else {
                opus.approvedLists = []
                opus.markDirty('approvedLists')
            }
            opus.approvedLists.addAll(json.approvedLists)
        } else {
            opus.approvedLists.removeAll(opus.approvedLists)
        }

        if (json.featureLists) {
            if (opus.featureLists) {
                opus.featureLists.clear()
            } else {
                opus.featureLists = []
                opus.markDirty('featureLists')
            }
            opus.featureLists.addAll(json.featureLists)
        } else {
            opus.featureLists.removeAll(opus.featureLists)
        }
        opus.featureListSectionName = json.featureListSectionName ? json.featureListSectionName : null;

        if (json.dataResourceConfig) {
            if (!opus.dataResourceConfig) {
                opus.dataResourceConfig = new DataResourceConfig()
            }

            opus.dataResourceConfig.recordResourceOption = json.dataResourceConfig.recordResourceOption ?
                    json.dataResourceConfig.recordResourceOption as DataResourceOption : DataResourceOption.NONE

            if (json.dataResourceConfig.recordSources) {
                if (opus.dataResourceConfig.recordSources) {
                    opus.dataResourceConfig.recordSources.clear()
                } else {
                    opus.dataResourceConfig.recordSources = []
                    opus.dataResourceConfig.markDirty('recordSources')
                }
                opus.dataResourceConfig.recordSources.addAll(json.dataResourceConfig.recordSources)
            }

            if (json.dataResourceConfig.privateRecordSources) {
                if (opus.dataResourceConfig.privateRecordSources) {
                    opus.dataResourceConfig.privateRecordSources.clear()
                } else {
                    opus.dataResourceConfig.privateRecordSources = []
                    opus.dataResourceConfig.markDirty('privateRecordSources')
                }
                opus.dataResourceConfig.privateRecordSources.addAll(json.dataResourceConfig.privateRecordSources)
            }

            opus.dataResourceConfig.imageResourceOption = json.dataResourceConfig.imageResourceOption ?
                    json.dataResourceConfig.imageResourceOption as DataResourceOption : DataResourceOption.NONE

            if (json.dataResourceConfig.imageSources) {
                if (opus.dataResourceConfig.imageSources) {
                    opus.dataResourceConfig.imageSources.clear()
                } else {
                    opus.dataResourceConfig.imageSources = []
                    opus.dataResourceConfig.markDirty('imageSources')
                }
                opus.dataResourceConfig.imageSources.addAll(json.dataResourceConfig.imageSources)
            }
        }

        opus.copyrightText = json.copyrightText ? json.copyrightText : null
        opus.footerText = json.footerText ? json.footerText : null
        if (json.contact) {
            opus.email = json.contact.email ? json.contact.email : null
            opus.facebook = json.contact.facebook ? json.contact.facebook : null
            opus.twitter = json.contact.twitter ? json.contact.twitter : null
        }

        if (json.brandingConfig) {
            if (!opus.brandingConfig) {
                opus.brandingConfig = new BrandingConfig()
            }

            if (opus.brandingConfig.logos) {
                opus.brandingConfig.logos.clear()
            } else {
                opus.brandingConfig.logos = []
                opus.brandingConfig.markDirty('logos')
            }

            opus.brandingConfig.logos.addAll(json.brandingConfig.logos.collect { logo ->
                new Logo(hyperlink: logo.hyperlink, logoUrl: logo.logoUrl)
            })

            opus.brandingConfig.thumbnailUrl = json.brandingConfig.thumbnailUrl ?
                    json.brandingConfig.thumbnailUrl : null
            opus.brandingConfig.opusBannerUrl = json.brandingConfig.opusBannerUrl ?
                    json.brandingConfig.opusBannerUrl : null
            opus.brandingConfig.profileBannerUrl = json.brandingConfig.profileBannerUrl ?
                    json.brandingConfig.profileBannerUrl : null
            opus.brandingConfig.pdfBannerUrl = json.brandingConfig.pdfBannerUrl ?
                    json.brandingConfig.pdfBannerUrl : null
            opus.brandingConfig.pdfBackBannerUrl = json.brandingConfig.pdfBackBannerUrl ?
                    json.brandingConfig.pdfBackBannerUrl : null
            opus.brandingConfig.colourTheme = json.brandingConfig.colourTheme ? json.brandingConfig.colourTheme : null
            opus.brandingConfig.issn = json.brandingConfig.issn ?: null
            opus.brandingConfig.pdfLicense = json.brandingConfig.pdfLicense ?: null
            opus.brandingConfig.shortLicense = json.brandingConfig.shortLicense ?: null
        }

        if (json.opusLayoutConfig) {
            if (!opus.opusLayoutConfig) {
                opus.opusLayoutConfig = new OpusLayoutConfig()
            }

            if (opus.opusLayoutConfig.images) {
                opus.opusLayoutConfig.images.clear()
            } else {
                opus.opusLayoutConfig.images = []
                opus.opusLayoutConfig.markDirty('images')
            }

            opus.opusLayoutConfig.images.addAll(json.opusLayoutConfig.images.collect { image ->
                new Image(imageUrl: image.imageUrl, credit: image.credit)
            })

            opus.opusLayoutConfig.updatesSection = json.opusLayoutConfig.updatesSection as String
            opus.opusLayoutConfig.explanatoryText = json.opusLayoutConfig.explanatoryText as String
            opus.opusLayoutConfig.duration = json.opusLayoutConfig.duration ?: opus.opusLayoutConfig.duration
            opus.opusLayoutConfig.helpTextSearch = json.opusLayoutConfig.helpTextSearch ?: null
            opus.opusLayoutConfig.helpTextBrowse = json.opusLayoutConfig.helpTextBrowse ?: null
            opus.opusLayoutConfig.helpTextIdentify = json.opusLayoutConfig.helpTextIdentify ?: null
            opus.opusLayoutConfig.helpTextFilter = json.opusLayoutConfig.helpTextFilter ?: null
            opus.opusLayoutConfig.helpTextDocuments = json.opusLayoutConfig.helpTextDocuments ?: null
            opus.opusLayoutConfig.bannerOverlayText = json.opusLayoutConfig.bannerOverlayText ?: null
        }

        if (json.theme) {
            opus.theme = new Theme(json.theme)
        }

        if (json.help) {
            opus.help= new HelpLink(json.help)
        }

        if (json.profileLayoutConfig) {
            if (!opus.profileLayoutConfig) {
                opus.profileLayoutConfig = new ProfileLayoutConfig()
            }
            opus.profileLayoutConfig.layout = json.profileLayoutConfig.layout ? json.profileLayoutConfig.layout : null
        }

        if (json.mapConfig) {
            if (!opus.mapConfig) {
                opus.mapConfig = new MapConfig()
            }
            opus.mapConfig.mapAttribution = json.mapConfig.mapAttribution ? json.mapConfig.mapAttribution : null
            opus.mapConfig.mapPointColour = json.mapConfig.mapPointColour ?
                    json.mapConfig.mapPointColour : Utils.DEFAULT_MAP_POINT_COLOUR;
            opus.mapConfig.mapDefaultLatitude = json.mapConfig.mapDefaultLatitude ?
                    json.mapConfig.mapDefaultLatitude as Float : Utils.DEFAULT_MAP_LATITUDE
            opus.mapConfig.mapDefaultLongitude = json.mapConfig.mapDefaultLongitude ?
                    json.mapConfig.mapDefaultLongitude as Float : Utils.DEFAULT_MAP_LONGITUDE
            opus.mapConfig.mapZoom = json.mapConfig.mapZoom ? json.mapConfig.mapZoom as int : Utils.DEFAULT_MAP_ZOOM
            opus.mapConfig.maxZoom = json.mapConfig.maxZoom ? json.mapConfig.maxZoom as int : Utils.DEFAULT_MAP_MAX_ZOOM
            opus.mapConfig.maxAutoZoom = json.mapConfig.maxAutoZoom ?
                    json.mapConfig.maxAutoZoom as int : Utils.DEFAULT_MAP_MAX_AUTO_ZOOM
            opus.mapConfig.autoZoom = json.mapConfig.autoZoom?.toBoolean() ?: false
            opus.mapConfig.allowSnapshots = json.mapConfig.allowSnapshots?.toBoolean() ?: false
            opus.mapConfig.mapBaseLayer = json.mapConfig.mapBaseLayer ?
                    json.mapConfig.mapBaseLayer : Utils.DEFAULT_MAP_BASE_LAYER
            opus.mapConfig.biocacheUrl = json.mapConfig.biocacheUrl ? json.mapConfig.biocacheUrl : null
            opus.mapConfig.biocacheName = json.mapConfig.biocacheName ? json.mapConfig.biocacheName : null
            opus.markDirty('mapConfig')
        }

        opus.approvedImageOption = json.approvedImageOption ?
                json.approvedImageOption as ImageOption : ImageOption.INCLUDE

        opus.keybaseProjectId = json.keybaseProjectId ? json.keybaseProjectId : null
        opus.keybaseKeyId = json.keybaseKeyId ? json.keybaseKeyId : null
        opus.enablePhyloUpload = json.enablePhyloUpload?.toBoolean() ?: false
        opus.enableOccurrenceUpload = json.enableOccurrenceUpload?.toBoolean() ?: false
        opus.enableTaxaUpload = json.enableTaxaUpload?.toBoolean() ?: false
        opus.enableKeyUpload = json.enableKeyUpload?.toBoolean() ?: false
        opus.showLinkedOpusAttributes = json.showLinkedOpusAttributes?.toBoolean() ?: false
        opus.keepImagesPrivate = json.keepImagesPrivate?.toBoolean() ?: false
        opus.usePrivateRecordData = json.usePrivateRecordData?.toBoolean() ?: false
        opus.citationProfile = json.citationProfile ?: null

        opus.privateCollection = json.privateCollection?.toBoolean() ?: false
        // if we are changing from public to private, then all other collections that have been granted access to
        // use this collection as a Supporting Collection need to have their access revoked.
        if (opus.privateCollection) {
            revokeAllSupportingCollectionAccess(opus)
        }

        opus.allowCopyFromLinkedOpus = json.allowCopyFromLinkedOpus?.toBoolean() ?: false
        opus.allowFineGrainedAttribution = toBooleanWithDefault(json.allowFineGrainedAttribution, true)

        opus.autoApproveShareRequests = toBooleanWithDefault(json.autoApproveShareRequests, true)

        opus.autoDraftProfiles = json.autoDraftProfiles?.toBoolean() ?: false
        if (json.tags) {
            opus.tags = []
            json.tags.each {
                opus.tags << Tag.findByUuid(it.uuid)
            }
        }

        opus.markDirty()
        boolean success = save opus

        if (success) {
            opus
        } else {
            null
        }
    }

    Opus updateAbout(String opusId, def json) {
        checkArgument opusId

        Opus opus = Opus.findByUuid(opusId)
        checkState opus

        opus.aboutHtml = json.aboutHtml as String
        opus.citationHtml = json.citationHtml as String

        save opus

        opus
    }

    boolean updateUserAccess(String opusId, Map json) {
        checkArgument opusId
        checkArgument json

        Opus opus = Opus.findByUuid(opusId)
        checkState opus

        if (json.containsKey("authorities")) {
            List<String> incomingIds = json.authorities?.findResults { it.uuid }

            List<Authority> authoritiesToRemove = []
            Map<String, Authority> existingAuthorities = [:]

            opus.authorities?.each {
                if (!incomingIds.contains(it.uuid)) {
                    authoritiesToRemove << it
                } else {
                    existingAuthorities << [(it.uuid): it]
                }
            }

            authoritiesToRemove?.each { authority ->
                opus.removeFromAuthorities(authority)
            }

            json.authorities?.each {
                if (it.uuid) {
                    Authority auth = existingAuthorities[it.uuid]
                    auth.notes = it.notes
                    auth.role = Role.valueOf(it.role.toUpperCase())
                    auth.user = getOrCreateContributor(it.name, it.userId)
                } else {
                    Contributor user = getOrCreateContributor(it.name, it.userId)
                    Role role = Role.valueOf(it.role.toUpperCase())
                    if (opus.authorities == null) {
                        opus.authorities = []
                    }
                    opus.addToAuthorities(new Authority(uuid: UUID.randomUUID().toString(),user: user, role: role, notes: it.notes))
                }
            }

            if (opus.validate()) {
                Authority.deleteAll(authoritiesToRemove)
            }
        }

        if (json.privateCollection != null) {
            opus.privateCollection = json.privateCollection?.toBoolean() ?: false
            if (opus.privateCollection) {
                revokeAllSupportingCollectionAccess(opus)
            }
        }

        save opus
    }

    String generateAccessToken(String opusId) {
        checkArgument opusId

        Opus opus = Opus.findByUuid(opusId)
        checkState opus

        String token = UUID.randomUUID().toString()

        opus.accessToken = token
        save opus

        token
    }

    void revokeAccessToken(String opusId) {
        checkArgument opusId

        Opus opus = Opus.findByUuid(opusId)
        checkState opus

        opus.accessToken = null
        save opus
    }

    boolean deleteOpus(String opusId, boolean profilesOnly = false) {
        Opus opus = Opus.findByUuid(opusId);

        List profiles = Profile.findAllByOpus(opus)
        Profile.deleteAll(profiles)

        if (!profilesOnly) {
            if (opus.glossary) {
                GlossaryItem glossaryItems = GlossaryItem.findByGlossary(opus.glossary)
                if (glossaryItems) {
                    GlossaryItem.deleteAll(glossaryItems)
                }
                delete opus.glossary
            }

            if (opus.attributeVocabUuid) {
                Vocab vocab = Vocab.findByUuid(opus.attributeVocabUuid)
                Term.deleteAll(vocab.terms)
                delete vocab
            }
            if (opus.authorshipVocabUuid) {
                Vocab vocab = Vocab.findByUuid(opus.authorshipVocabUuid)
                Term.deleteAll(vocab.terms)
                delete vocab
            }
            if (opus.groupVocabUuid) {
                Vocab vocab = Vocab.findByUuid(opus.groupVocabUuid)
                Term.deleteAll(vocab.terms)
                delete vocab
            }

            Opus.withCriteria {
                eq "supportingOpuses.uuid", opus.uuid
            }?.each {
                SupportingOpus supporting = it.supportingOpuses.find { it.uuid == opus.uuid }
                it.supportingOpuses.remove(supporting)
                save it
            }

            delete opus
        }
    }

    def updateSupportingOpuses(String opusId, Map json) {
        checkArgument opusId
        checkArgument json

        Opus opus = Opus.findByUuid(opusId)
        checkState opus

        // for each supporting opus:
        // if the entry is new, send an email requesting approval to the administrator(s) of the supporting opus
        // if the entry is old, do nothing
        // if the entry has been removed, delete the supporting opus
        if (json.supportingOpuses != null) {
            if (!opus.supportingOpuses) {
                opus.supportingOpuses = []
            }

            json.supportingOpuses.each {
                String supportingOpusId = it.uuid
                SupportingOpus existing = opus.supportingOpuses.find { it.uuid == supportingOpusId }

                if (!existing) {
                    Opus supportingOpus = Opus.findByUuid(it.uuid)
                    ShareRequestStatus status = supportingOpus.autoApproveShareRequests ?
                            ShareRequestStatus.ACCEPTED : ShareRequestStatus.REQUESTED
                    opus.supportingOpuses << new SupportingOpus(uuid: supportingOpus.uuid,
                            title: supportingOpus.title,
                            requestStatus: status,
                            dateRequested: new Date())

                    if (!supportingOpus.autoApproveShareRequests) {
                        List administrators = supportingOpus.authorities.findAll {
                            it.role == Role.ROLE_PROFILE_ADMIN
                        }.collect { userService.getUserForUserId(it.user.userId).userName }

                        String user = userService.getUserForUserId(userService.getUserId()).displayName

                        String url = "${grailsApplication.config.getProperty('profile.hub.base.url')}/opus/${supportingOpus.uuid}/shareRequest/${opus.uuid}"

                        String body = groovyPageRenderer.render(template: "/email/shareRequest", model: [user: user, supportingOpus: supportingOpus, opus: opus, url: url])

                        emailService.sendEmail(administrators, "${opus.title}<no-reply@ala.org.au>",
                                "Request to share collection information", body)
                    } else {
                        SupportingOpus existingShare = supportingOpus.sharingDataWith.find { it.uuid == opus.uuid }
                        if (!existingShare) {
                            supportingOpus.addToSharingDataWith( new SupportingOpus(uuid: opus.uuid, title: opus.title))
                            save supportingOpus
                        }
                    }
                }
            }

            List toBeRemoved = opus.supportingOpuses.findAll {
                !json.supportingOpuses.find { incoming -> it.uuid == incoming.uuid }
            }

            toBeRemoved.each {
                opus.supportingOpuses.remove(it)
            }

        }

        opus.showLinkedOpusAttributes = json.showLinkedOpusAttributes?.toBoolean() ?: false
        opus.autoApproveShareRequests = toBooleanWithDefault(json.autoApproveShareRequests, true)
        opus.allowCopyFromLinkedOpus = json.allowCopyFromLinkedOpus?.toBoolean() ?: false

        save opus
    }

    def respondToSupportingOpusRequest(String opusId, String requestingOpusId, ShareRequestAction action) {
        checkArgument requestingOpusId
        checkArgument opusId

        Opus requestingOpus = Opus.findByUuid(requestingOpusId)
        checkState requestingOpus

        Opus opus = Opus.findByUuid(opusId)
        checkState opus

        def status = action.resultingStatus
        requestingOpus.supportingOpuses.find { it.uuid == opusId }.requestStatus = status

        SupportingOpus existing = opus.sharingDataWith.find { it.uuid == requestingOpus.uuid }
        if (action == ShareRequestAction.ACCEPT && !existing) {
            opus.sharingDataWith << new SupportingOpus(uuid: requestingOpus.uuid, title: requestingOpus.title, requestStatus: ShareRequestStatus.ACCEPTED, dateApproved: new Date())
            save opus
        } else if (action != ShareRequestAction.ACCEPT && existing) {
            opus.sharingDataWith.remove(existing)
            save opus
        }

        List administrators = requestingOpus.authorities.findAll {
            it.role == Role.ROLE_PROFILE_ADMIN
        }.collect { userService.getUserForUserId(it.user.userId)?.userName }

        String user = userService.getUserForUserId(userService.getUserId()).displayName

        String body = groovyPageRenderer.render(template: "/email/shareResponse", model: [user: user, action: action, opus: opus])

        emailService.sendEmail(administrators,
                "${opus.title}<no-reply@ala.org.au>",
                "Request to share collection information - ${action.resultingStatus.toString().toLowerCase()}",
                body)

        save requestingOpus
    }

    private revokeAllSupportingCollectionAccess(Opus opus) {
        // use a shallow clone of the list to avoid possible concurrent modification errors
        List<SupportingOpus> sharedWith = opus.sharingDataWith?.collect()

        sharedWith.each {
            respondToSupportingOpusRequest(opus.uuid, it.uuid, ShareRequestAction.REVOKE)
        }
    }

    boolean deleteGlossaryItem(String glossaryItemId) {
        GlossaryItem item = GlossaryItem.findByUuid(glossaryItemId);

        boolean success = false

        if (item) {
            item.glossary.items.remove(item);

            save item.glossary

            success = delete item
        }

        return success
    }

    boolean updateGlossaryItem(String glossaryItemId, data) {
        GlossaryItem item = GlossaryItem.findByUuid(glossaryItemId);

        boolean success = false

        if (item) {
            updateItemDetails(item, data)

            success = save item
        }

        return success
    }

    GlossaryItem createGlossaryItem(String opusId, data) {
        Opus opus = Opus.findByUuid(opusId)

        GlossaryItem item = new GlossaryItem();
        item.uuid = UUID.randomUUID().toString()
        updateItemDetails(item, data)

        item.glossary = opus.glossary

        opus.glossary.items << item

        boolean success = save opus.glossary

        if (!success) {
            item = null
        }

        item
    }

    private updateItemDetails(GlossaryItem item, data) {
        if (data.description && data.description != item.description) {
            item.description = data.description
        }
        if (data.term && data.term != item.term) {
            item.term = data.term
        }
        if (data.cf != null) {
            item.cf.clear()

            data.cf.each {
                GlossaryItem cfItem = GlossaryItem.findByTerm(it)
                if (cfItem) {
                    item.cf << cfItem
                } else {
                    log.warn("Could find matching cf item ${it}. Ignoring.")
                }
            }
        }
    }

    boolean saveGlossaryItems(String opusId, Map json) {
        Glossary glossary

        if (json.glossaryId) {
            glossary = Glossary.findByUuid(json.glossaryId)
        } else if (json.opusId) {
            Opus opus = Opus.findByUuid(opusId)

            glossary = opus.glossary

            if (!glossary) {
                glossary = new Glossary()
                save glossary

                opus.glossary = glossary
                save opus
            }
        } else {
            throw new IllegalArgumentException("Could not find glossary or opus")
        }


        if (glossary.items) {
            GlossaryItem.deleteAll(glossary.items)
            glossary.items?.clear()
        } else if (glossary.items == null) {
            glossary.items = []
            glossary.markDirty('items')
        }

        json.items.each {
            GlossaryItem item
            if (it["glossaryItemId"]) {
                item = GlossaryItem.findByUuid(it.glossaryItemId)
            } else {
                item = new GlossaryItem()
                item.uuid = UUID.randomUUID().toString()
                item.glossary = glossary
            }

            if (item) {
                updateItemDetails(item, it)

                glossary.items << item
            }
        }

        save glossary
    }

    Collection<Authority> getAuthoritiesForUser(String userId, String opusId) {
        Collection<Authority> authorities = []

        Contributor user = Contributor.findByUserId(userId)

        if (opusId) {
            Opus opus = Opus.findByUuid(opusId)
            authorities = opus.authorities?.findAll { it.user == user }
        }

        authorities
    }

    List<Attachment> saveAttachment(String opusId, Map metadata, MultipartFile file) {
        Opus opus = Opus.findByUuid(opusId)
        checkState opus

        Date createdDate = metadata.createdDate ? new SimpleDateFormat("yyyy-MM-dd").parse(metadata.createdDate) : null

        if (metadata.uuid) {
            Attachment existing = opus.attachments.find { it.uuid == metadata.uuid }
            if (existing) {
                existing.url = metadata.url
                existing.title = metadata.title
                existing.description = metadata.description
                existing.rights = metadata.rights
                existing.rightsHolder = metadata.rightsHolder
                existing.licence = metadata.licence
                existing.creator = metadata.creator
                existing.category = metadata.category
                existing.createdDate = createdDate
            }
        } else {
            Attachment newAttachment = new Attachment(uuid: UUID.randomUUID().toString(), url: metadata.url,
                    title: metadata.title, description: metadata.description, filename: metadata.filename,
                    contentType: file?.contentType, rights: metadata.rights, createdDate: createdDate,
                    rightsHolder: metadata.rightsHolder, licence: metadata.licence, creator: metadata.creator, category: metadata.category)
            if (file) {
                String extension = Utils.getFileExtension(file.originalFilename)
                attachmentService.saveAttachment(opus.uuid, null, newAttachment.uuid, file, extension)
            }
            opus.attachments << newAttachment
        }

        save opus

        opus.attachments
    }

    List<Attachment> deleteAttachment(String opusId, String attachmentId) {
        Opus opus = Opus.findByUuid(opusId)
        checkState opus

        Attachment attachment = opus.attachments?.find { it.uuid = attachmentId }
        if (attachment) {
            opus.attachments.remove(attachment)
            if (attachment.filename) {
                attachmentService.deleteAttachment(opus.uuid, null, attachment.uuid, Utils.getFileExtension(attachment.filename))
            }
        }

        save opus

        opus.attachments
    }

    void updateAdditionalStatuses(Opus opus, List<String> additionalStatuses) {
        opus.additionalStatuses = additionalStatuses
        save opus
    }

    def updateMasterList(Opus opus, String masterListUid) {
        opus.masterListUid = masterListUid
        save opus
        log.info("Queueing sync of opus master list")
        importService.asyncSyncroniseMasterList(opus.uuid, true)
    }

    @Timed(value = 'Timed: is profile on master list - timed', useClassPrefix = true)
    @Metered(value = 'Metered: is profile on master list - metered', useClassPrefix = true)
    boolean isProfileOnMasterList(Opus opus, profile) {
        if (!opus.masterListUid || !profile) return true

        def masterList = masterListService.getCombinedListForUser(opus)
        def exists = masterList.find { it.name.toLowerCase() == (profile.scientificNameLower ?: profile.scientificName?.toLowerCase()) }
        return exists != null
    }

    def getMasterListKeybaseItems(Opus opus) {
        def filterList = masterListService.getCombinedLowerCaseNamesListForUser(opus)
        if (filterList == null) {
            return null
        }
        MongoQuery.AggregatedResultList ps = Profile.withCriteria {
            eq('opus', opus)
            'in'('scientificNameLower', filterList)
            projections {
                property('matchedName') // TODO use matchedName.scientificName after GORM 6.1+ upgrade
                property('guid')
            }
        }

        // If you see this and think the following sucks and the mongo plugin is > 3.0.3 then please feel free to try
        // replacing it.

        // XXX Trying to run a .collect or .each on ps results in a NPE from running ps.size()
        // so white box the $#!? out of this to find a way to get results without running ps.initializeFully
        // TODO replace with
        //   def names = (filterList + ps.collect { it[0]?.scientificName }).findAll { it }.unique()
        //   def guids = ps.collect { it[1] }.findAll { it }.unique()
        // after upgrading GORM?
        // NOTE placing a debug point around here will attempt to load all the ps properties which will trigger
        // the consumption of the cursor and then this method doesn't work
        def pnames = []
        def pguids = []
        ps.withCloseable {
            def cursor = ps.cursor // this is a private property, whoops
            while (cursor.hasNext()) {
                DBObject obj = cursor.next()
                pnames << obj[0]?.scientificName
                pguids << obj[1]
            }

        }

        def names = (filterList + pnames).findAll { it }.unique()
        def guids = pguids.findAll { it }.unique()
        return [ names: names, guids: guids ]
    }
}
