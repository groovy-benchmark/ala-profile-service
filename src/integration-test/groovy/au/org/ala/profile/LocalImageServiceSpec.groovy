package au.org.ala.profile

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import org.springframework.beans.factory.annotation.Autowired

@Integration
@Rollback
class LocalImageServiceSpec extends BaseIntegrationSpec {

    @Autowired
    LocalImageService service

    void "updateMetadata should update image metadata"() {
        given:
        Opus opus = new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")
        save opus
        Profile profile
        def originalImage1 = new LocalImage(imageId: UUID.randomUUID().toString(), title: 'title', licence: 'licence')
        def originalImage2 = new LocalImage(imageId: UUID.randomUUID().toString(), title: 'title', licence: 'licence')
        def privateImages = [originalImage1, originalImage2]
        profile = new Profile(opus: opus, scientificName: "sciName", privateImages: privateImages, lastPublished: new Date())
        save profile

        when:
        service.updateMetadata(originalImage1.imageId,
                [
                        'created'     : '2001-01-01',
                        'creator'     : 'smitty',
                        'description' : 'description',
                        'licence'     : 'licence 2 the relicencing',
                        'rights'      : 'righton',
                        'rightsHolder': 'righthold',
                        'title'       : 'new title'
                ])
        profile = Profile.findByUuid(profile.uuid)

        then:
        def image1 = profile.privateImages.find { it.imageId == originalImage1.imageId }
        def image2 = profile.privateImages.find { it.imageId == originalImage2.imageId }
                //.created == null
        image1.created == '2001-01-01'
        image2.created == null
        image1.creator == 'smitty'
        image2.creator == null
        image1.description == 'description'
        image2.description == null
        image1.licence == 'licence 2 the relicencing'
        image2.licence == 'licence'
        image1.rights == 'righton'
        image2.rights == null
        image1.rightsHolder == 'righthold'
        image2.rightsHolder == null
        image1.title == 'new title'
        image2.title == 'title'
    }

    void "test updating an an image when the profile is in draft mode updates the draft image"() {
        given:
        Opus opus = new Opus(glossary: new Glossary(), dataResourceUid: "dr1234", title: "title")
        save opus
        Profile profile
        def image1 = new LocalImage(imageId: UUID.randomUUID().toString(), title: 'title', licence: 'licence')
        def image2 = new LocalImage(imageId: UUID.randomUUID().toString(), title: 'title', licence: 'licence')
        def privateImages = [image1, image2]
        def draftImages = [new LocalImage(image1.properties), new LocalImage(image2.properties)]

        when:
        profile = new Profile(opus: opus, scientificName: "sciName", privateImages: privateImages, lastPublished: new Date(), draft: [uuid: "asd", scientificName: "sciName", privateImages: draftImages])
        save profile
        service.updateMetadata(image1.imageId, ['created': '2001-01-01'])
        profile = Profile.findByUuid(profile.uuid)

        then:
        profile.draft.privateImages.find { it.imageId == image1.imageId }.created == '2001-01-01'
        profile.privateImages.find { it.imageId == image1.imageId }.created == null
    }
}
