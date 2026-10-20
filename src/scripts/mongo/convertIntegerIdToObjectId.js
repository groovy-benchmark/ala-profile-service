/*
 * Copyright (C) 2020 Atlas of Living Australia
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
 */
var updateMany = false;

function recursiveSearch(item) {
    var counter = 0,
        total = 0,
        updatedDoc = {};

    function updateDataTypeId(collection, id, doc) {
        db.getCollection(collection).remove({_id: id}, true);
        doc._id = new ObjectId();
        updatedDoc[doc._id] = true;
        db.getCollection(collection).save(doc);
        // print('updating doc with _id: ' + id + ' to ' + doc._id);
        return doc._id;
    };

    function updateForeignKey(item, newId, oldId) {
        var collection = item.collection;

        if (collection && newId != undefined && oldId != undefined && item.queryPath && item.setPath) {
            // print("Replacing documents in " + item.collection + " and id " + oldId + " with new id " + newId);
            var query = {}, set = {};
            query[item.queryPath] = oldId;
            set[item.setPath] = newId;
            db.getCollection(collection).update(query, {$set: set}, updateMany);
        }
    }

    function iterateDoc(collection, id, obj) {
        var toPrintObj = false;

        if (obj.hasOwnProperty('_id')) {
            // print("the field is _id : " + obj["_id"] + " typeof " + typeof obj["_id"]);
            var oldId = obj._id,
                newId = updateDataTypeId(collection, id, obj);
            toPrintObj = true;

            if (item.foreignKeyUpdate) {
                item.foreignKeyUpdate.forEach(function (fkItem) {
                    updateForeignKey(fkItem, newId, oldId);
                });
            }
        }

        if (toPrintObj) {
            return true;
        }

        return false;
    };

    total = db.getCollection(item.collection).find().count();
    db.getCollection(item.collection).find().limit(total).forEach(function (doc) {
        // print("Scanning document with Id: " + doc['_id']);
        var docContainUndefined
        if (!updatedDoc[doc._id]) {
            docContainUndefined = iterateDoc(item.collection, doc['_id'], doc, item)
        }

        if (!docContainUndefined) {
            print('Nothing to update for document with id: ' + doc['_id']);
        }
        counter++;

        if (counter % 1000 == 0) {
            print("Completed " + counter + " of " + total);
        }
    });


};

print("######## convertIntegerIdToObjectId.js");
// var collectionsToConvert = ['attribute', 'comment', 'authority', 'tag',  'status', 'opus', 'term', 'vocab', 'occurrenceResource']
var list = [
    // {
    //     collection: 'attribute'
    // },
    {
        collection: 'authority',
        foreignKeyUpdate: [
            {
                collection: 'opus',
                queryPath: 'authorities',
                setPath: 'authorities.$'
            }
        ]
    },
    {
        collection: 'comment',
        foreignKeyUpdate: [
            {
                collection: 'comment',
                queryPath: 'parent',
                setPath: 'parent'
            }
        ]
    },
    {
        collection: 'contributor',
        foreignKeyUpdate: [
            {
                collection: 'authority',
                queryPath: 'user',
                setPath: 'user'
            },
            {
                collection: 'comment',
                queryPath: 'author',
                setPath: 'author'
            },
            {
                collection: 'attribute',
                queryPath: 'creators',
                setPath: 'creators.$'
            },
            {
                collection: 'attribute',
                queryPath: 'editors',
                setPath: 'editors.$'
            }
        ]
    },
    {
        collection: 'glossary',
        foreignKeyUpdate: [
            {
                collection: 'glossaryItem',
                queryPath: 'glossary',
                setPath: 'glossary'
            },
            {
                collection: 'opus',
                queryPath: 'glossary',
                setPath: 'glossary'
            }
        ]
    },
    {
        collection: 'glossaryItem',
        // todo double check with prod data
        foreignKeyUpdate: [
            {
                collection: 'glossaryItem',
                queryPath: 'cf',
                setPath: 'cf.$'
            }
        ]
    },
    {
        collection: 'job'
    },
    {
        collection: 'name',
        foreignKeyUpdate: [
            {
                collection: 'profile',
                queryPath: 'draft.matchedName',
                setPath: 'draft.matchedName'
            }
        ]
    },
    {
        collection: 'nameRematch'
    },
    // todo double check
    {
        collection: 'occurrenceResource',
        foreignKeyUpdate: [
            {
                collection: 'opus',
                queryPath: 'additionalOccurrenceResources',
                setPath: 'additionalOccurrenceResources.$'
            }
        ]
    },
    {
        collection: 'opus',
        foreignKeyUpdate: [{
            collection: 'profile',
            queryPath: 'opus',
            setPath: 'opus'
        }]
    },
    // no need to do profile collection
    {
        collection: 'status'
    },
    {
        collection: 'tag',
        foreignKeyUpdate: [{
            collection: 'opus',
            queryPath: 'tags',
            setPath: 'tags.$'
        }]
    },
    {
        collection: 'term',
        foreignKeyUpdate: [
            {
                collection: 'profile',
                queryPath: 'authorship.category',
                setPath: 'authorship.$.category'
            },
            {
                collection: 'profile',
                queryPath: 'draft.authorship.category',
                setPath: 'draft.authorship.$.category'
            },
            {
                collection: 'attribute',
                queryPath: 'title',
                setPath: 'title'
            }

        ]
    },
    {
        collection: 'vocab',
        foreignKeyUpdate: [
            {
                collection: 'term',
                queryPath: 'vocab',
                setPath: 'vocab'
            }
        ]
    },

];
var collectionsToConvert = ['attribute']
list.forEach(function (collection) {
    recursiveSearch(collection);
})
