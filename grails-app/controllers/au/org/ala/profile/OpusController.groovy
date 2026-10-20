package au.org.ala.profile

import au.ala.org.ws.security.RequireApiKey
import au.org.ala.profile.security.Role
import au.org.ala.profile.util.ShareRequestAction
import au.org.ala.profile.util.Utils
import au.org.ala.web.UserDetails
import grails.converters.JSON
import grails.gorm.transactions.Transactional
import groovy.json.JsonSlurper
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.multipart.MultipartHttpServletRequest

import jakarta.servlet.http.HttpServletResponse

import static au.org.ala.profile.ImportService.SyncResponse.*

@RequireApiKey
class OpusController extends BaseController {

    OpusService opusService
    UserService userService
    ImportService importService
    AttachmentService attachmentService
    SearchService searchService
    MasterListService masterListService
    UserSettingsService userSettingsService


    def index() {
        def opuses = Opus.findAll()
        respond opuses
    }

    /**
     * List Opuses by a tag abbreviation
     * @return all matching opuses
     */
    def listOpusesByTagAbbrev() {
        if (!params.tagAbbrev) {
            badRequest "You must provide a tag abbreviation"
        } else {
            Tag tag = Tag.findByAbbrev(params.tagAbbrev)
            if (tag) {
                def opuses = Opus.findAllByTags(tag)
                render opuses as JSON
//                respond opuses
            }
            else {
                notFound "No matching tag was found for abbreviation ${params.tagAbbrev}"
            }
        }
    }

    def show() {
        def result = getOpus()
        if (result) {
            int profiles = Profile.countByOpus(result)
            result.profileCount = profiles
            def florulaListId = masterListService.getFlorulaListIdForUser(request, result.uuid)
            result.florulaListId = florulaListId
            render result as JSON
        } else {
            notFound()
        }
    }

    def create() {
        Opus opus = opusService.createOpus(request.getJSON())
        render opus as JSON
    }

    def deleteOpus() {
        if (!params.opusId) {
            badRequest "You must provide an opusId"
        } else {
            Opus opus = getOpus()

            if (!opus) {
                notFound()
            } else {
                boolean success = opusService.deleteOpus(opus.uuid)

                render([success: success] as JSON)
            }
        }
    }

    def updateOpus() {
        if (!params.opusId) {
            badRequest "You must provide an opusId"
        } else {
            Opus opus = getOpus()

            if (!opus) {
                notFound()
            } else {
                def json = request.getJSON()

                boolean updated = opusService.updateOpus(opus.uuid, json)

                if (!updated) {
                    saveFailed()
                } else {
                    success([updated: true])
                }
            }
        }
    }

    def updateSupportingOpuses() {
        def json = request.getJSON()
        if (!params.opusId || !json) {
            badRequest "opusId and a json body are required"
        } else {
            Opus opus = getOpus()

            if (!opus) {
                notFound "No opus found for id ${params.opusId}"
            } else {
                boolean updated = opusService.updateSupportingOpuses(opus.uuid, json)

                if (!updated) {
                    saveFailed()
                } else {
                    success([updated: true])
                }
            }
        }
    }

    def respondToSupportingOpusRequest() {
        if (!params.requestingOpusId || !params.opusId || !params.requestAction || !ShareRequestAction.valueOf(params.requestAction.toUpperCase())) {
            badRequest "requestingOpusId, supportingOpusId and accept are required parameters"
        } else {
            Opus opus = getOpus()

            if (!opus) {
                notFound "No opus found for id ${params.opusId}"
            } else {
                boolean updated = opusService.respondToSupportingOpusRequest(opus.uuid, params.requestingOpusId, ShareRequestAction.valueOf(params.requestAction.toUpperCase()))

                if (!updated) {
                    saveFailed()
                } else {
                    success([updated: true])
                }
            }
        }
    }

    def updateUserAccess() {
        if (!params.opusId) {
            badRequest "You must provide an opusId"
        } else {
            Opus opus = getOpus()

            if (!opus) {
                notFound()
            } else {
                def json = request.getJSON()

                boolean updated = opusService.updateUserAccess(opus.uuid, json)

                if (!updated) {
                    saveFailed()
                } else {
                    success([updated: true])
                }
            }
        }
    }

    def generateAccessToken() {
        if (!params.opusId) {
            badRequest "You must provide an opusId"
        } else {
            Opus opus = getOpus()

            if (!opus) {
                notFound()
            } else {
                String token = opusService.generateAccessToken(opus.uuid)

                render ([token: token] as JSON)
            }
        }
    }

    def revokeAccessToken() {
        if (!params.opusId) {
            badRequest "You must provide an opusId"
        } else {
            Opus opus = getOpus()

            if (!opus) {
                notFound()
            } else {
                opusService.revokeAccessToken(opus.uuid)

                render ([revoked: true] as JSON)
            }
        }
    }

    def getGlossary() {
        Glossary glossary = null

        if (!params.opusId && !params.glossaryId) {
            badRequest "You must provide either an opusId or a glossaryId"
        } else if (params.opusId) {
            Opus opus = getOpus()

            if (!opus || !opus.glossary) {
                notFound()
            } else {
                glossary = opus.glossary
            }
        } else {
            glossary = Glossary.findByUuid(params.glossaryId)
        }

        if (!glossary) {
            notFound()
        } else {
            List<GlossaryItem> items = GlossaryItem.findAllByGlossaryAndTermIlike(glossary, "${params.prefix ?: ''}%")

            glossary.items = items
        }

        render glossary as JSON
    }

    def saveGlossaryItems() {
        def json = request.getJSON();
        if (!json || (!json.opusId && !json.glossaryId) || !params.opusId) {
            badRequest()
        } else {
            Opus opus = getOpus()
            boolean updated = opusService.saveGlossaryItems(opus.uuid, json)

            if (!updated) {
                saveFailed()
            } else {
                success([updated: updated])
            }
        }
    }

    def deleteGlossaryItem() {
        if (!params.glossaryItemId) {
            badRequest()
        } else {
            boolean deleted = opusService.deleteGlossaryItem(params.glossaryItemId)

            if (!deleted) {
                saveFailed()
            } else {
                success([deleted: deleted])
            }
        }
    }

    def updateGlossaryItem() {
        def json = request.getJSON();

        if (!params.glossaryItemId || !json) {
            badRequest()
        } else {
            boolean updated = opusService.updateGlossaryItem(params.glossaryItemId, json)

            if (!updated) {
                saveFailed()
            } else {
                success([updated: updated])
            }
        }
    }

    def createGlossaryItem() {
        def json = request.getJSON();

        if (!params.opusId || !json) {
            badRequest()
        } else {
            Opus opus = getOpus()
            if (!opus) {
                notFound()
            } else {
                GlossaryItem item = opusService.createGlossaryItem(opus.uuid, json)

                if (!item) {
                    saveFailed()
                } else {
                    render item as JSON
                }
            }
        }
    }

    def about() {
        if (!params.opusId) {
            badRequest()
        } else {
            Opus opus = getOpus()

            if (!opus) {
                notFound()
            } else {
                render ([
                        opus: opus,
                        opusId: opus.uuid,
                        administrators: opus.authorities.collect {
                            if (it.role == Role.ROLE_PROFILE_ADMIN) {
                                [email: userService.getUserForUserId(it.user.userId, false)?.userName,
                                 name: it.user.name]
                            }
                        }] as JSON)
            }
        }
    }

    def updateAbout() {
        def json = request.getJSON()
        if (!params.opusId || !json || !json.containsKey("aboutHtml") || !json.containsKey("citationHtml")) {
            badRequest()
        } else {
            Opus opus = getOpus()

            if (!opus) {
                notFound()
            } else {
                opus = opusService.updateAbout(opus.uuid, json)

                render ([opus: [title: opus.title, opusId: opus.uuid, shortName: opus.shortName, aboutHtml: opus.aboutHtml]] as JSON)
            }
        }
    }

    def getAttachmentMetadata() {
       if (!params.opusId) {
           badRequest "opusId is a required parameter"
       } else {
           Opus opus = getOpus()

           if (!opus) {
               notFound "No opus exists for id ${params.opusId}"
           } else {
               if (params.attachmentId) {
                   Attachment attachment = opus.attachments?.find { it.uuid == params.attachmentId }
                   if (!attachment) {
                       notFound "No attachment exists in opus ${params.opusId} with id ${params.attachmentId}"
                   } else {
                       attachment.downloadUrl = "${grailsApplication.config.getProperty('profile.hub.base.url')}/opus/${opus.uuid}/attachment/${attachment.uuid}/download"

                       render ([attachment] as JSON)
                   }
               } else {
                   List<Attachment> attachments = opus.attachments
                   attachments?.each {
                       // attachments may have a local file associated with them (e.g. pdf), or they may be a link to an
                       // external resource. If they have a local file, then the filename property will be set. If they
                       // are an external link, then the url property will be set.
                       if (it.filename) {
                           it.downloadUrl = "${grailsApplication.config.getProperty('profile.hub.base.url')}/opus/${opus.uuid}/attachment/${it.uuid}/download"
                       }
                   }

                   render attachments as JSON
               }
           }
       }
    }

    def downloadAttachment() {
        if (!params.opusId || !params.attachmentId) {
            badRequest "opusId and attachmentId are required parameters"
        } else {
            Opus opus = getOpus()

            if (!opus) {
                notFound "No opus was found for id ${params.opusId}"
            } else {
                Attachment attachment = opus.attachments.find { it.uuid == params.attachmentId }
                File file = null
                if (attachment) {
                    file = attachmentService.getAttachment(opus.uuid, null, params.attachmentId, Utils.getFileExtension(attachment.filename))
                }

                if (!file) {
                    notFound "No attachment was found with id ${params.attachmentId}"
                } else {
                    response.setContentType(attachment.contentType ?: "application/pdf")
                    response.setHeader("Content-disposition", "attachment;filename=${attachment.filename ?: 'attachment.pdf'}")
                    response.outputStream << file.newInputStream()
                    response.outputStream.flush()
                }
            }
        }
    }

    def saveAttachment() {
        if (!params.opusId || !(request instanceof MultipartHttpServletRequest) || !request.getParameter("data")) {
            badRequest "opusId is a required parameter and a JSON post body must be provided"
        } else {
            Opus opus = getOpus()

            if (!opus) {
                notFound()
            } else {
                Map metadata = new JsonSlurper().parseText(request.getParameter("data"))

                MultipartFile file = null
                if (request instanceof MultipartHttpServletRequest) {
                    file = request.getFile(request.fileNames[0])
                }

                List<Attachment> attachments = opusService.saveAttachment(opus.uuid, metadata, file)

                render attachments as JSON
            }
        }
    }

    def deleteAttachment() {
        if (!params.opusId || !params.attachmentId) {
            badRequest "opusId and attachmentId are required parameters"
        } else {
            Opus opus = getOpus()

            if (!opus) {
                notFound()
            } else {
                opusService.deleteAttachment(opus.uuid, params.attachmentId)

                render ([success: true] as JSON)
            }
        }
    }

    def getCategories() {
        def categories = grailsApplication.config.getProperty('attachement.categories', List)
        render(categories as JSON)
    }

    def getTags() {
        render([tags: Tag.list()] as JSON)
    }

    def updateAdditionalStatuses() {
        if (!params.opusId) {
            badRequest "opusId is required"
        } else {
            Opus opus = getOpus()

            if (!opus) {
                notFound()
            } else {
                opusService.updateAdditionalStatuses(opus, request.JSON.addtionalStatuses)
                render([success: true] as JSON)
            }
        }
    }

    def updateMasterList() {
        if (!params.opusId) {
            badRequest "opusId is required"
        } else {
            Opus opus = getOpus()
            opusService.updateMasterList(opus, request.JSON.masterListUid)
            render([success: true] as JSON)
        }
    }

    def syncMasterList() {
        if (!params.opusId) {
            badRequest "opusId is required"
        } else {
            Opus opus = getOpus()
            if (opus) {
                def regen = params.boolean('regenerateStubs', false)
                def result = importService.syncroniseMasterList(opus.uuid, regen)
                def statusCode
                switch (result) {
                    case OpusNotFound:
                        statusCode = 404
                        break
                    case SyncComplete:
                        statusCode = 200
                        break
                    case SyncFailed:
                    default:
                        statusCode = 500
                }
                response.status = statusCode
                render(result as JSON)
            } else {
                notFound 'opus not found'
            }
        }
    }

    def isMasterListSyncing() {
        if (!params.opusId) {
            badRequest "opusId is required"
        } else {
            Opus opus = getOpus()
            if (opus) {
                def result = importService.isMasterListSyncing(opus.uuid)
                response.status = 200
                render([response:result] as JSON)
            } else {
                notFound 'opus not found'
            }
        }
    }


    def getMasterListKeybaseItems() {
        if (!params.opusId) {
            badRequest "opusId is required"
        } else {
            Opus opus = getOpus()
            if (!opus) {
                notFound "Ain't no opus"
            } else {
                def result = opusService.getMasterListKeybaseItems(opus)
                if (result != null) {
                    render(result as JSON)
                } else {
                    response.sendError(HttpServletResponse.SC_NO_CONTENT)
                }
            }
        }
    }
    @Transactional
    def updateFlorulaListForUser() {
        if (!params.opusId) {
            badRequest "opusId is required"
        } else {
            Opus opus = getOpus()
            if (!opus) {
                notFound "Ain't no opus"
            } else {
                def json = request.getJSON()
                def listId = json.florulaListId
                UserDetails userDetails = (UserDetails) request.getAttribute(AuditInterceptor.REQUEST_USER_DETAILS_KEY)
                if (!userDetails.userId) {
                    badRequest "Ain't no user"
                }
                userSettingsService.setFlorulaList(userSettingsService.getUserSettings(userDetails?.userId), opus.uuid, listId)
                response.sendError(HttpServletResponse.SC_NO_CONTENT)
            }
        }
    }

    def reindex() {
        if (!params.opusId) {
            badRequest "opusId is required"
        } else {
            if (Status.count() == 0) {
                Status status = new Status()
                status.searchReindex = true
                status.save()
            }

            searchService.reindex(opus)

            render (Status.first() as JSON)
        }
    }
}
