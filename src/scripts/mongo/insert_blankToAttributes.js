/**
 * Script to update all the links in the old production data to have the _blank parameter added.
 * uncomment db save command before execution.
 */


/**
 *
 * Draft profiles store attribute values in Profile Collection,
 * the path to them is `draft.attributes.text`
 */
var profiles = db.profile.find({'draft.attributes.text': /a href/});
while (profiles.hasNext()) {
    var profile = profiles.next();
    print("----------------------------------------");
    print("profile name: " + profile.fullName);

    for (var i=0; i<profile.draft.attributes.length; i++) {
        var txt = profile.draft.attributes[i].text;

        if (txt && txt.indexOf("a href") >= 0) {
            print("***ORIGINAL TEXT: " + txt);
            var links = txt.split('<a ');
            for (var j=0; j<links.length; j++) {
                var link = links[j];
//          print("..." + link);
                if (link.startsWith('href') && link.indexOf('_blank') < 0){
                    links[j] = 'target="_blank" ' + link;
                }
            }

            profile.draft.attributes[i].text = links.join('<a ');
            print("UPDATED TEXT*** : " + profile.draft.attributes[i].text);
            // db.profile.save(profile);
        }
    }
}

/**
 *
 * For published profiles, their attribute values are stored in Attribute Collection
 */
var attributes = db.attribute.find({'text': /a href/});
while (attributes.hasNext()) {
    var attribute = attributes.next();
    print("----------------------------------------");
    print("profile: " + attribute.profile);
    print("***ORIGINAL TEXT: " + attribute.text);
    var links = attribute.text.split('<a ');
    for (var j=0; j<links.length; j++) {
//          print("..." + links[j]);
        if (links[j].startsWith('href') && links[j].indexOf('_blank') < 0){
            links[j] = 'target="_blank" ' + links[j];
        }
    }
    attribute.text = links.join('<a ');
    print("UPDATED TEXT*** : " + attribute.text);
    // db.attribute.save(attribute);
}