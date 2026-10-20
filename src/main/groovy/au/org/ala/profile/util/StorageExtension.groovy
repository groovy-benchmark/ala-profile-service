package au.org.ala.profile.util

/**
 * Created by shi131 on 26/02/2016.
 */
enum StorageExtension {
    PDF('.pdf'),
    ZIP('.zip')

    String extension

    StorageExtension(String extension) {
        this.extension = extension
    }
}