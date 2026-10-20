package au.org.ala.profile

import au.ala.org.ws.security.RequireApiKey
import au.org.ala.profile.util.ImageType
@RequireApiKey
class LocalImageService {

    Map getImageInfo(String imageId) {
        doWithProfileForImageId(imageId) { Profile profile ->
            Map image = [
                    profileId: profile.uuid,
                    opusId   : profile.opus.uuid
            ]

            LocalImage privateImage = profile.privateImages?.find { it.imageId == imageId } ?: profile.draft?.privateImages?.find { it.imageId == imageId }
            LocalImage stagedImage = profile.draft?.stagedImages?.find { it.imageId == imageId }
            if (privateImage) {
                image.putAll(privateImage.properties)
                image.type = ImageType.PRIVATE.name()
            } else if (stagedImage) {
                image.putAll(stagedImage.properties)
                image.type = ImageType.STAGED.name()
            }
            image?.remove("dbo")

            return image
        }
    }

    LocalImage updateMetadata(String imageId, metadata) {
        log.debug("updateMetadata(imageId: $imageId, metadata: $metadata)")
        doWithProfileForImageId(imageId) { Profile profile ->
            LocalImage image = profile.draft?.privateImages?.find { it.imageId == imageId } ?: profile.privateImages?.find { it.imageId == imageId }
            if (!image) {
                image = profile.draft?.stagedImages?.find { it.imageId == imageId }
            }
            if (image) {
                image.creator = metadata.creator
                image.description = metadata.description
                image.licence = metadata.licence
                image.rights = metadata.rights
                image.rightsHolder = metadata.rightsHolder
                image.title = metadata.title
                image.created = metadata.created

                profile.save(validate: true, failOnError: true)
                return image
            } else {
                throw new IllegalStateException("No image found with id $imageId")
            }
        }
    }

    private <T> T doWithProfileForImageId(String imageId, Closure<T> body) {
        // check for private images
        List<Profile> profiles = Profile.withCriteria {
            "privateImages" {
                eq "imageId", imageId
            }
        }

        if (!profiles) {
            // check for draft private images
            profiles = Profile.withCriteria {
                isNotNull "draft"
                "draft" {
                    eq "privateImages.imageId", imageId
                }
            }
        }

        if (!profiles) {
            // check for draft staged (i.e. public) images
            profiles = Profile.withCriteria {
                isNotNull "draft"
                "draft" {
                    eq "stagedImages.imageId", imageId
                }
            }
        }

        if (!profiles) {
            return null
        } else if (profiles?.size() == 1) {
            body(profiles[0])
        } else {
            throw new IllegalStateException("Non unique image id!") // should never happen
        }
    }
}
