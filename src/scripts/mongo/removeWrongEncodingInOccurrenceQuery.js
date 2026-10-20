var profiles = db.getCollection('profile').find({occurrenceQuery: {$regex: 'q%3D'}});
var counter = 0;
while (profiles.hasNext()) {
    var profile = profiles.next();
    profile.occurrenceQuery = profile.occurrenceQuery.replace('q%3D', 'q=');
    db.profile.save(profile);
    counter ++;
}

print("Completed replacing - replaced instances are "+ counter);