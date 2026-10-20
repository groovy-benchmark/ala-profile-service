/*
 * Copyright (C) 2017 Atlas of Living Australia
 * All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 * 
 * Created by Temi on 22/5/17.
 */
var fieldsToCheck = ["colourBy=", "fq="];
var regex = new RegExp(fieldsToCheck.join('|'), 'i');

var profiles = db.getCollection('profile').find({});
var profileCounter = 0, draftCounter = 0;

while (profiles.hasNext()) {
    var profile = profiles.next();
    var updateFields = addOrUpdateIsCustomMapConfig(profile);
    db.profile.update({_id:profile._id},{$set:updateFields});
}

print("Profiles with custom map config - " + profileCounter +". Draft profiles with custom map config - " + draftCounter);

function addOrUpdateIsCustomMapConfig(profile) {
    var updateFields = {isCustomMapConfig: false};
    if (profile.occurrenceQuery && isOccurrenceQueryCustomConfigured(profile.occurrenceQuery)) {
        updateFields.isCustomMapConfig = true;
        profileCounter ++;
    }

    if (profile.draft){
        updateFields["draft.isCustomMapConfig"] = false;
        if(profile.draft.occurrenceQuery && isOccurrenceQueryCustomConfigured(profile.draft.occurrenceQuery) ){
           updateFields["draft.isCustomMapConfig"] = true;
            draftCounter ++;
        }
    }

    return updateFields;
}

function isOccurrenceQueryCustomConfigured(occurrenceQuery) {
    var result = regex.exec(occurrenceQuery);
    return !!result
}