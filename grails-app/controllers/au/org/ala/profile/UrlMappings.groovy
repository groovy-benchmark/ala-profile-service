package au.org.ala.profile

class UrlMappings {

    static mappings = {

        // Public API
        "/api/v1/opus/$opusId/export" controller: "export", action: [GET: "exportCollection"]
        "/api/v1/opus/$opusId/count" controller: "export", action: [GET: "countProfiles"]

        "/api/v1/profiles/" controller: "export", action: [GET: "getProfiles"]

        // 'Internal' ALA API
        "/admin/search/reindex" controller: "search", action: [POST: "reindex"]
        "/admin/names/clearCache" controller: "name", action: [POST: "clearCache"]
        "/admin/rematchNames" controller: "admin", action: [POST: "rematchNames"]
        "/admin/tag/$tagId?" controller: "admin", action: [GET: "getTag", PUT: "createTag", POST: "updateTag", DELETE: "deleteTag"]
        "/admin/backupCollections" controller: "admin", action: [POST: "backupCollections"]
        "/admin/restoreCollections" controller: "admin", action: [POST: "restoreCollections"]

        "/audit/object/$entityId" controller: "audit", action: [GET: "auditTrailForObject"]

        "/audit/user/$userId" controller: "audit", action: [GET: "auditTrailForUser"]

        "/tags" controller: "opus", action: [GET: "getTags"]
        "/tags/$tagAbbrev/opuses"  controller: "opus", action: [GET: "listOpusesByTagAbbrev"]

        "/opus/$opusId/glossary/item/" controller: "opus", action: [PUT: "createGlossaryItem"]
        "/opus/$opusId/glossary/item/$glossaryItemId" controller: "opus", action: [DELETE: "deleteGlossaryItem", POST: "updateGlossaryItem"]
        "/opus/$opusId/glossary" controller: "opus", action: [GET: "getGlossary"]
        "/opus/$opusId/glossary/$prefix" controller: "opus", action: [GET: "getGlossary"]
        "/opus/$opusId/glossary" controller: "opus", action: [POST: "saveGlossaryItems"]

        "/opus/$opusId/additionalStatuses" controller: "opus", action: [POST: 'updateAdditionalStatuses']
        "/opus/$opusId/about" controller: "opus", action: [GET: "about", PUT: "updateAbout"]
        "/opus/$opusId/masterList" controller: "opus", action: [POST: 'updateMasterList']
        "/opus/$opusId/masterList/keybaseItems" controller: "opus", action: 'getMasterListKeybaseItems'
        "/opus/$opusId/masterList/sync" controller: "opus", action: [POST: 'syncMasterList']
        "/opus/$opusId/masterList/isSyncing" controller: "opus", action: [GET: 'isMasterListSyncing']
        "/opus/$opusId/florulaList" controller: "opus", action: [POST: 'updateFlorulaListForUser']
        "/opus/$opusId/vocab/" controller: "vocab", action: [GET: "index", POST: "update"]
        "/opus/$opusId/vocab/$vocabId" controller: "vocab", action: [GET: "show", POST: "update"]
        "/opus/$opusId/vocab/usages/find" controller: "vocab", action: "findUsagesOfTerm"
        "/opus/$opusId/vocab/usages/replace" controller: "vocab", action: [POST: "replaceUsagesOfTerm"]

        "/opus/" controller: "opus", action: [GET: "index", PUT: "create", POST: "create"]

        "/opus/$opusId/updateUsers" controller: "opus", action: [POST: "updateUserAccess"]
        "/opus/$opusId/access/token" controller: "opus", action: [POST: "generateAccessToken", PUT: "generateAccessToken", DELETE: "revokeAccessToken"]

        "/opus/$opusId/supportingCollections/respond/$requestingOpusId/$requestAction" controller: "opus", action: [POST: "respondToSupportingOpusRequest"]
        "/opus/$opusId/supportingCollections/update" controller: "opus", action: [POST: "updateSupportingOpuses"]

        "/opus/$opusId/attachment/$attachmentId/download" controller: "opus", action: [GET: "downloadAttachment"]
        "/opus/$opusId/attachment/$attachmentId" controller: "opus", action: [GET: "getAttachmentMetadata", DELETE: "deleteAttachment"]
        "/opus/$opusId/attachment/" controller: "opus", action: [GET: "getAttachmentMetadata", POST: "saveAttachment"]
        "/attachment/categories" controller: "opus", action: [GET: "getCategories"]

        "/opus/$opusId" controller: "opus", action: [GET: "show", POST: "updateOpus", DELETE: "deleteOpus"]

        "/profile/search" controller: "search", action: "search"
        "/profile/search/scientificName" controller: "search", action: "findByScientificName"
        "/profile/search/taxon/name" controller: "search", action: "findByClassificationNameAndRank"
        "/profile/search/taxon/name/total" controller: "search", action: "totalByClassificationNameAndRank"
        "/profile/search/taxon/nameAndTotal" controller: "search", action: "findProfilesByClassificationNameAndRankAndGetTotalProfilesCount"
        "/profile/search/taxon/level" controller: "search", action: "groupByRank"
        "/profile/search/taxon/levels" controller: "search", action: "getRanks"
        "/profile/search/filter" controller: "search", action: [GET: "filterProfiles"]
        "/profile/search/autocomplete" controller: "search", action: [GET: "autocomplete"]
        "/profile/search/lookup" controller: "search", action: [POST: "lookupByField"]
        "/profile/search/contains" controller: "search", action: [GET: "nameContains"]
        "/profile/search/children" controller: "search", action: "getImmediateChildren"
        "/profile/search/browse" controller: "search", action: [GET: "browse"]
        "/profile/search/browse/preview" controller: "search", action: [GET: "browsePreview"]
        "/profile/search/warmup" controller: "search", action: [GET: "warmup"]
        "/profile/search/warmup/quick" controller: "search", action: [GET: "warmupQuick"]
        "/profile/search/throttled" controller: "search", action: [GET: "throttledSearch"]
        "/profile/search/throttled/preview" controller: "search", action: [GET: "throttledSearchPreview"]

        "/opus/$opusId/profile/" controller: "profile", action: [GET: "getProfiles", PUT: "createProfile"]

        "/opus/$opusId/profile/$profileId" controller: "profile", action: [GET: "getByUuid", DELETE: "deleteProfile", POST: "updateProfile"]

        "/opus/$opusId/profile/$profileId/toggleDraftMode/" controller: "profile", action: [POST: "toggleDraftMode"]
        "/opus/$opusId/profile/$profileId/discardDraftChanges/" controller: "profile", action: [POST: "discardDraftChanges"]
        "/opus/$opusId/profile/$profileId/rename/" controller: "profile", action: [POST: "renameProfile"]

        "/opus/$opusId/profile/$profileId/attribute/" controller: "attribute", action: [GET: "index", PUT: "create", POST: "create"]
        "/opus/$opusId/profile/$profileId/attribute/$attributeId" controller: "attribute", action: [GET: "show", PUT: "update", DELETE: "delete", POST: "update"]


        "/opus/$opusId/profile/$profileId/document/" controller: "profile", action: [POST:"updateDocument", DELETE: "deleteDocument"]
        "/opus/$opusId/profile/$profileId/document/list" controller: "profile", action: [GET:"listDocuments"]
        "/opus/$opusId/profile/$profileId/document/$id?(.$format)?" controller: "profile", action: [POST:"updateDocument", DELETE: "deleteDocument"]
        "/opus/$opusId/profile/$profileId/primaryMultimedia" controller: "profile", action: [POST: "setPrimaryMultimedia"]
        "/opus/$opusId/profile/$profileId/status" controller: "profile", action: [POST: "setStatus"]


        "/opus/$opusId/profile/$profileId/links" controller: "profile", action: [POST: "saveLinks"]

        "/opus/$opusId/profile/$profileId/bhl" controller: "profile", action: [POST: "saveBHLLinks"]

        "/opus/$opusId/profile/$profileId/recordStagedImage" controller: "profile", action: [POST: "recordStagedImage"]
        "/opus/$opusId/profile/$profileId/recordPrivateImage" controller: "profile", action: [POST: "recordPrivateImage"]

        "/opus/$opusId/profile/$profileId/authorship" controller: "profile", action: [POST: "saveAuthorship"]

        "/opus/$opusId/profile/$profileId/bibliography" controller: "profile", action: [POST: "saveBibliography"]

        "/opus/$opusId/profile/$profileId/specimen" controller: "profile", action: [POST: "saveSpecimens"]

        "/opus/$opusId/profile/$profileId/classification" controller: "profile", action: [GET: "classification"]
        "/opus/$opusId/profile/$profileId/publication" controller: "profile", action: [GET: "listPublications"]
        "/opus/$opusId/profile/$profileId/publication/$publicationId/file" controller: "profile", action: [GET: "getPublicationFile"]

        "/opus/$opusId/profile/$profileId/profileSettings" controller: "profile", action: [GET: "getProfileSettings", POST: "saveProfileSettings"]

        "/opus/$opusId/profile/$profileId/comment" controller: "comment", action: [GET: "getComments", PUT: "addComment"]
        "/opus/$opusId/profile/$profileId/comment/$commentId" controller: "comment", action: [GET: "getComment", POST: "updateComment", DELETE: "deleteComment"]

        "/opus/$opusId/profile/$profileId/attachment/$attachmentId/download" controller: "profile", action: [GET: "downloadAttachment"]
        "/opus/$opusId/profile/$profileId/attachment/$attachmentId" controller: "profile", action: [GET: "getAttachmentMetadata", DELETE: "deleteAttachment"]
        "/opus/$opusId/profile/$profileId/attachment/" controller: "profile", action: [GET: "getAttachmentMetadata", POST: "saveAttachment"]

        "/opus/$opusId/profile/$profileId/duplicate" controller: "profile", action: [PUT: "duplicateProfile"]

        "/opus/$opusId/archive/$profileId" controller: "profile", action: [POST: "archiveProfile"]
        "/opus/$opusId/restore/$profileId" controller: "profile", action: [POST: "restoreArchivedProfile"]
        "/opus/$opusId/reindex" controller: "opus", action: [POST: "reindex"]

        "/opus/$opusId/image/$imageId/metadata" controller: "image", action: [GET: "getImageInfo", POST: "updateMetadata"]
        "/image/$imageId" controller: "image", action: [GET: "getImageInfo"]
        "/image/remote/preview" controller: "image", action: [GET: "remoteImage"]
        "/image/remote/registered" controller: "image", action: [GET: "registeredImage"]

        "/checkName" controller: "profile", action: [GET: "checkName"]

        "/report/archivedProfiles" controller: "report", action: [GET: "archivedProfiles"]
        "/report/draftProfiles" controller: "report", action: [GET: "draftProfiles"]
        "/report/mismatchedNames" controller: "report", action: [GET: "mismatchedNames"]
        "/report/profiles" controller: "report", action: [GET: "profileLookup", POST: "profileLookup"]
        "/report/profiles/listed" controller: "report", action: [GET: "profileLookupListed"]
        "/report/profiles/export" controller: "report", action: [GET: "profileExport"]
        "/report/profiles/page" controller: "report", action: [GET: "profilePage"]
        "/report/profiles/paced" controller: "report", action: [GET: "profilePaced"]
        "/report/profiles/paced/preview" controller: "report", action: [GET: "profilePacedPreview"]
        "/report/recentChanges" controller: "report", action: [GET: "recentChanges"]
        "/report/recentComments" controller: "report", action: [GET: "recentComments"]

        "/job/$jobType/next" controller: "job", action: [GET: "getNextPendingJob"]
        "/job/$jobType/$jobId" controller: "job", action: [DELETE: "deleteJob", POST: "updateJob"]
        "/job/$jobType" controller: "job", action: [GET: "listAllPendingJobs", PUT: "createJob"]
        "/job/" controller: "job", action: [GET: "listAllPendingJobs", PUT: "createJob"]

        "/statistics/" controller: "statistics", action: [GET: "index"]

        "/import/profile" controller: "import", action: [POST: "profile"]
        "/import/$importId/report" controller: "import", action: [GET: "report"]
        "/import/image/check" controller: "import", action: [POST: "checkImageUrl"]
        "/import/image/check/restricted" controller: "import", action: [POST: "checkImageUrlRestricted"]
        "/import/names/match" controller: "import", action: [POST: "matchNames"]
        "/import/names/match/registered" controller: "import", action: [POST: "matchNamesRegistered"]
        "/import/names/dump" controller: "import", action: [POST: "previewNameDump"]
        "/import/names/dump/restricted" controller: "import", action: [POST: "previewNameDumpRestricted"]
        "/import/bie/profile" controller: "import", action: [POST: "bieProfile"]

        "/status/" controller: "status", action: [GET: "status"]
        "/status/$component" controller: "status", action: [GET: "status"]

        "/publication/$publicationId" controller: "profile", action: [GET: "getPublicationDetails"]

        "/user/details" controller: "userDetails", action: [GET: "getUserDetails"]

        "500" view: '/error'
        "404"(view: "/notFound")
        "403"(view: "/notAuthorised")
        "401"(view: "/notAuthorised")
        "/notAuthorised"(view: "/notAuthorised")
        "/error"(view: "/error")
        "/notFound"(view: "/notFound")

        "/" view: "/index"
    }
}
