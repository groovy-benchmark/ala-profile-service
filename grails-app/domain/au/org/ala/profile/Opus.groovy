package au.org.ala.profile

import au.org.ala.profile.sanitizer.SanitizedHtml
import au.org.ala.profile.util.ImageOption
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

import jakarta.persistence.Transient

@EqualsAndHashCode(excludes = 'additionalOccurrenceResources,authorities,tags')
@ToString(allProperties=false, excludes = 'additionalOccurrenceResources,authorities,tags')
//@ToString(excludes = 'additionalOccurrenceResources,authorities,tags')
class Opus {

    static searchable = {
        root = false
        only = ["title", "uuid", "shortName", "dataResourceUid"]
        title index: "true"
        uuid index: "true"
        shortName index: "true"
        dataResourceUid index: "true"
    }

    String uuid
    String shortName
    String title
    String description
    String dataResourceUid

    String masterListUid
    List<String> approvedLists = []
    List<String> featureLists = []
    String featureListSectionName

    BrandingConfig brandingConfig
    ProfileLayoutConfig profileLayoutConfig
    MapConfig mapConfig
    DataResourceConfig dataResourceConfig
    OpusLayoutConfig opusLayoutConfig
    Theme theme
    HelpLink help

    String attributeVocabUuid
    String authorshipVocabUuid
    String groupVocabUuid
    Boolean enablePhyloUpload = false
    Boolean enableOccurrenceUpload = false
    Boolean enableTaxaUpload = false
    Boolean enableKeyUpload = false
    Boolean showLinkedOpusAttributes = false
    Boolean allowCopyFromLinkedOpus = false
    Boolean allowFineGrainedAttribution = true
    boolean privateCollection = false
    Glossary glossary
    String keybaseProjectId
    String keybaseKeyId
    @SanitizedHtml
    String aboutHtml
    @SanitizedHtml
    String citationHtml
    String citationProfile
    String copyrightText
    String footerText
    String email
    String facebook
    String twitter
    List<SupportingOpus> supportingOpuses = []
    List<SupportingOpus> sharingDataWith = []
    List<Attachment> attachments = []
    boolean autoApproveShareRequests = true
    boolean keepImagesPrivate = true
    boolean usePrivateRecordData = false
    ImageOption approvedImageOption = ImageOption.INCLUDE

    String accessToken

    boolean autoDraftProfiles = false // automatically lock profiles for draft when they are created

    Date dateCreated
    Date lastUpdated

    @Transient
    int profileCount
    @Transient
    String florulaListId

    List<String> additionalStatuses = ['In Review', 'Complete']
    List<Authority> authorities
    List<Tag> tags
    List <OccurrenceResource> additionalOccurrenceResources

    static transients = ['profileCount', 'florulaListId']
    static hasMany = [additionalOccurrenceResources: OccurrenceResource, authorities: Authority, tags: Tag]
    static embedded = ['supportingOpuses', 'sharingDataWith', 'attachments', 'brandingConfig', 'mapConfig', 'profileLayoutConfig', 'dataResourceConfig', 'opusLayoutConfig', 'theme', 'help']

    static constraints = {
        shortName nullable: true
        description nullable: true
        masterListUid nullable: true
        brandingConfig nullable: true
        profileLayoutConfig nullable: true
        mapConfig nullable: true
        dataResourceConfig nullable: true
        opusLayoutConfig nullable: true
        theme nullable: true
        help nullable: true
        attributeVocabUuid nullable: true
        authorshipVocabUuid nullable: true
        groupVocabUuid nullable: true
        enablePhyloUpload nullable: true
        enableOccurrenceUpload nullable: true
        enableTaxaUpload nullable: true
        enableKeyUpload nullable: true
        keybaseProjectId nullable: true
        keybaseKeyId nullable: true
        aboutHtml nullable: true
        citationHtml nullable: true
        citationProfile nullable: true, maxSize: 500
        copyrightText nullable: true
        footerText nullable: true
        email nullable: true
        facebook nullable: true
        twitter nullable: true
        featureListSectionName nullable: true
        accessToken nullable: true
    }

    def beforeValidate() {
        if (uuid == null) {
            //mint an UUID
            uuid = UUID.randomUUID().toString()
        }
    }

    static mapping = {
        autoTimestamp true
        glossary cascade: "all-delete-orphan"
        authorities cascade: "all-delete-orphan", fetch: 'join', batchSize: 20
        shortName index: true
        uuid index: true
        additionalOccurrenceResources fetch: 'join'
        tags fetch: 'join'
    }
}
