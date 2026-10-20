package au.org.ala.profile.util

enum JobType {
    PDF

    static JobType byName(String name) {
        values().find { it.name() == name?.toUpperCase() }
    }
}