/**
 * Add group vocabulary to
 */
load('../../uuid.js');
var vocabs = db.vocab.find({}).sort({_id:-1});
var maxId = vocabs.next()._id;
var index = 1;
var opuses = db.opus.find({groupVocabUuid: {$exists: false}});
// var opuses = db.opus.find({});

while(opuses.hasNext()){
    var opus = opuses.next();
    createVocab(opus);
}

function createVocab (opus) {
    var vocabId = UUID.generate();
    var result = db.vocab.insertOne({_id: NumberLong(maxId + index), name: opus.title + " Group Vocabulary", strict: true, uuid: vocabId, version: NumberInt(0)});
    if (result.insertedId) {
        index ++;
        db.opus.updateOne({uuid: opus.uuid}, {$set: {groupVocabUuid: vocabId}});
        print("Created vocab " + vocabId + " for opus " + opus.title);
    }
}