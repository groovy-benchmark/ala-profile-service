package au.org.ala.profile.util

enum NSLNomenclatureMatchStrategy {
    APC_OR_LATEST, // for flora collections
    TEXT_CONTAINS,
    LATEST,
    NSL_SEARCH,
    NONE // don't set the nomenclature at all


    static final DEFAULT = APC_OR_LATEST
}