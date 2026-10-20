/**
 * https://github.com/AtlasOfLivingAustralia/profile-hub/issues/717
 * Latest version of biocache-service does not create occurrence records via API.
 * Update all collections that has this feature enabled.
 */
db.opus.update({keepImagesPrivate: false}, {$set: {keepImagesPrivate: true}}, {multi: true});
