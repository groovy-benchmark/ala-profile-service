package au.org.ala.profile.util

enum ProfileSortOption {
    NAME, TAXONOMY

    static ProfileSortOption byName(String name) {
        ProfileSortOption option = null

        values().each {
            if (it.name() == name?.toUpperCase()) {
                option = it
            }
        }

        option
    }

    static getDefault() {
        return TAXONOMY
    }
}