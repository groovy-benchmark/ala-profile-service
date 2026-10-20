var opusId = 3;
var profiles = db.getCollection('profile').find({opus: opusId, 'privateImages':{$exists: true}})

var privateImages = ['Profile id,Image id,Scientific Name,Title,Description,Rights Holder,Rights,Licence,Creator,Original File Name,Created']

const escapeComma = function(data) {
    if (data.includes(',')) {
        return '\"'+data+'\"';
    }
    return data;
}
profiles.forEach(p => p.privateImages.forEach(i => privateImages.push(p.uuid + "," + i.imageId + "," + p.scientificName + "," + escapeComma(i.title) + ","
    + escapeComma(i.description) + "," + escapeComma(i.rightsHolder) + "," + escapeComma(i.rights) + "," + escapeComma(i.licence) + "," + escapeComma(i.creator)
    + "," + escapeComma(i.originalFileName) + "," + i.created)));

privateImages.forEach(i => print(i));