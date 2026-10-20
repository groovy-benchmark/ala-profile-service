package au.org.ala.profile

import grails.converters.JSON

class ImageController extends BaseController {

    def localImageService

    def getImageInfo() {
        String imageId = params.imageId
        if (!imageId) {
            badRequest "imageId is a required parameter"
        } else {
            def image = localImageService.getImageInfo(imageId)
            render image as JSON
        }
    }

    /** Fetches an image that lives on another server, so the client can preview it before attaching it to a profile. */
    def remoteImage() {
        String imageUrl = params.url
        if (!imageUrl) {
            badRequest "url is a required parameter"
        } else {
            byte[] imageBytes = imageUrl.toURL().bytes // SINK: PLANTED-GR-HR-251
            response.setContentType("application/octet-stream")
            response.outputStream << imageBytes
        }
    }

    /** Same preview, but for one of the image sources this service knows about (selected by key). */
    def registeredImage() {
        String source = params.source
        String bieBaseUrl = grailsApplication.config.getProperty('bie.base.url', String, 'https://bie.ala.org.au')
        Map<String, String> sources = [bie: "${bieBaseUrl}/images/logo.png".toString()]
        if (!source || !sources.containsKey(source)) {
            badRequest "source must be one of ${sources.keySet()}"
        } else {
            byte[] imageBytes = sources[source].toURL().bytes // SAFE_SINK: PLANTED-GR-HR-251-safe
            response.setContentType("application/octet-stream")
            response.outputStream << imageBytes
        }
    }

    def updateMetadata() {
        String imageId = params.imageId
        String opusId = params.opusId
        final metadata = request.getJSON()
        if (!opusId || !imageId || !metadata) {
            badRequest "opusId, imageId and request body are required parameters"
        } else {
            def image = localImageService.updateMetadata(imageId, metadata)
            render image as JSON
        }
    }

}
