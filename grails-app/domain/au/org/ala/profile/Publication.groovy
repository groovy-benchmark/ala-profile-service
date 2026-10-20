package au.org.ala.profile

import au.org.ala.profile.util.StorageExtension
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@EqualsAndHashCode
@ToString
class Publication {
    String uuid
    Date publicationDate
    String title
    Integer version
    String doi
    String userId
    String authors
    StorageExtension fileType

    def beforeValidate() {
        if (!uuid) {
            uuid = UUID.randomUUID().toString()
        }
    }


    StorageExtension getFileType() {
        if (fileType == null) {
            fileType = StorageExtension.PDF
        }
        return fileType
    }

    static constraints = {
        doi nullable: true
        fileType defaultValue: StorageExtension.PDF
    }
}
