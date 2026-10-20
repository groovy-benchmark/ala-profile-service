package au.org.ala.profile

import au.org.ala.profile.util.Utils
import org.springframework.web.multipart.MultipartFile

class AttachmentService {

    def grailsApplication

    boolean deleteAttachment(String opusId, String profileId, String attachmentId, String extension) {
        File file = new File(getPath(opusId, profileId, attachmentId, extension))

        file.delete()
    }

    void saveAttachment(String opusId, String profileId, String attachmentId, MultipartFile incomingFile, String extension) {
        File file = new File(getPath(opusId, profileId, attachmentId, extension))
        file.mkdirs()
        incomingFile.transferTo(file)
    }

    File getAttachment(String opusId, String profileId, String attachmentId, String extension) {
        File file = new File(getPath(opusId, profileId, attachmentId, extension))

        file.exists() ? file : null
    }

    String getPath(String opusId, String profileId, String attachmentId, String extension) {
        "${grailsApplication.config.getProperty('attachments.directory')}/${opusId}/${profileId ? profileId + '/' : ''}${attachmentId}.${extension}"
    }

    Map<String,File> collectAllAttachmentsIncludingOriginalNames(Profile profile) {
        Map<String,File> fileMap = [:]
        if (profile.attachments) {
            List<Attachment> attachments = profile.getAttachments()
            attachments.each { attachment ->
                File file = getAttachment(profile.opus.uuid, profile.uuid, attachment.uuid, Utils.getFileExtension(attachment.filename))
                if (file) {
                    fileMap.put(attachment.getFilename(),file)
                }
            }
        }
        return fileMap
    }


}
