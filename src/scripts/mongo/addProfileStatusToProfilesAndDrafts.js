/*
 * Copyright (C) 2021 Atlas of Living Australia
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
 * Created by Temi on 27/7/21.
 */

var count = db.getCollection('profile').find({$or: [{"profileStatus":{$exists: false}},{"profileStatus":{$type:10}}]}).count();
print("number of profiles without profileStatus " + count);

db.getCollection('profile').update({$or: [{"profileStatus":{$exists: false}},{"profileStatus":{$type:10}}]}, {$set: {"profileStatus":"Partial"}}, {multi:false})

count = db.getCollection('profile').find({$or: [{"profileStatus":{$exists: false}},{"profileStatus":{$type:10}}]}).count();
print("number of profiles without profileStatus after update " + count);

// draft
count = db.getCollection('profile').find({draft: {$exists: true}, $or: [{"draft.profileStatus":{$exists: false}},{"draft.profileStatus":{$type:10}}]}).count();
print("number of draft profiles without profileStatus " + count);

db.getCollection('profile').update({draft: {$exists: true}, $or: [{"draft.profileStatus":{$exists: false}},{"draft.profileStatus":{$type:10}}]}, {$set: {"draft.profileStatus":"Partial"}}, {multi:true})

count = db.getCollection('profile').find({draft: {$exists: true}, $or: [{"draft.profileStatus":{$exists: false}},{"draft.profileStatus":{$type:10}}]}).count();
print("number of draft profiles without profileStatus after update " + count);