package au.org.ala.profile

import com.mongodb.BasicDBObject
import com.mongodb.client.result.UpdateResult
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoCollection
import grails.gorm.transactions.Transactional

import jakarta.persistence.PersistenceException

@Transactional
class VocabService extends BaseDataAccessService {

    MongoClient mongo
    def grailsApplication

    Map updateVocab(String vocabId, Map data) {
        log.debug("Updating vocabulary ${vocabId} with data ${data}")
        Vocab vocab
        if(vocabId){
            vocab = Vocab.findByUuid(vocabId)
        } else {
            vocab = new Vocab(name: data.name, terms: [])
        }

        List toDelete = []

        vocab.strict = data.strict as boolean

        if (data.deleteExisting) {
            vocab.terms.clear()
        }

        Set<String> retainedTermIds = []

        data.terms.each { item ->
            if(item.groupBy) {
                item.groupBy = Term.findByUuid(item.groupBy.termId)
            } else {
                item.groupBy = null
            }

            if (item.termId) {
                Term term = Term.findByUuid(item.termId)

                if (term) {
                    retainedTermIds << item.termId
                    term.name = item.name
                    term.order = item.order
                    term.required = item.required == null ? false : item.required.toBoolean()
                    term.summary = item.summary == null ? false : item.summary.toBoolean()
                    term.containsName = item.containsName == null ? false : item.containsName.toBoolean()
                    term.dataType = item.dataType ?: null
                    term.groupBy = item.groupBy
                    term.constraintListVocab = item.constraintListVocab ?: null
                    term.unit = item.unit ?: null
                }
            } else {
                // GRAILS-8061 beforeValidate does not get called on child records during a cascade save of the parent
                // Therefore, we cannot rely on the beforeValidate method of Term, which usually creates the UUID.
                Term term = new Term(uuid: UUID.randomUUID().toString(),
                        name: item.name,
                        order: item.order == null ?: vocab.terms.size() + 1,
                        required: item.required.toBoolean(),
                        summary: item.summary.toBoolean(),
                        containsName: item.containsName.toBoolean(),
                        groupBy: item.groupBy,
                        dataType: item.dataType ?: null,
                        constraintListVocab: item.constraintListVocab ?: null,
                        unit: item.unit
                )
                term.vocab = vocab
                vocab.terms << term
            }
        }

        vocab.terms.each { item ->
            if (!retainedTermIds.contains(item.uuid)) {
                Term term = Term.findByUuid(item.uuid);
                if (term) {
                    term.vocab = null
                    log.debug("Deleting term ${item.name}")
                    term.delete()
                    toDelete.add(item)
                }
            }
        }

        if (toDelete) {
            vocab.terms.removeAll(toDelete)
        }

        boolean updated = save vocab
        [updated: updated, vocab: vocab]
    }

    Term getOrCreateTerm(String name, String vocabId, String excludeTerm = null) {
        Term term

        Vocab vocab = Vocab.findByUuid(vocabId);

        if (vocab) {
            term = excludeTerm? Term.findByVocabAndUuidNotEqualAndNameIlike(vocab, excludeTerm, name) : Term.findByVocabAndNameIlike(vocab, name)

            if (!term) {
                if (vocab.strict) {
                    throw new IllegalStateException("Term ${name} does not exist in the vocabulary, and cannot be created because the vocabulary is Strict.")
                } else {
                    term = new Term(name: name, vocab: vocab)
                    boolean success = save term
                    if (!success) {
                        throw new PersistenceException("Failed to insert new term.")
                    }
                }
            }
        } else {
            throw new PersistenceException("Vocabulary with id ${vocabId} does not exist")
        }

        term
    }

    int findUsagesOfTerm(String opusId, String vocabId, String termUuid) {
        Vocab vocab = Vocab.findByUuid(vocabId)
        Term term = Term.findByVocabAndUuid(vocab, termUuid)
        Opus opus = Opus.findByUuid(opusId)
        Term constraintListVocabTerm = Term.findByConstraintListVocab(vocabId)

        // Check if this is acknowledgement term
        if (opus.authorshipVocabUuid == vocabId) {
            List<Profile> profiles = Profile.findAll {authorship.category == term.id || draft.authorship.category == term.id}

            return profiles.size()

        } else if (opus.attributeVocabUuid == vocabId) {
            List<Attribute> attributes = Attribute.findAllByTitle(term)

            attributes.size()

            // Draft for profiles store attributes as well
            List<Profile> profiles = Profile.findAll {
                eq("draft.attributes.title", term.id)
            }

            return (attributes.size() + profiles.size())

        } else if (opus.groupVocabUuid == vocabId) {
            vocab = Vocab.findByUuid(opus.attributeVocabUuid)
            if (vocab) {
                List<Term> terms = vocab.terms.findAll {
                    it.groupBy?.uuid == termUuid
                }

                return terms.size()
            }
        } else if (constraintListVocabTerm) {
//            todo: calculate exact number of profiles using this term.
            List<Attribute> attributes = Attribute.findAllByConstraintList(term.uuid)

            // Draft for profiles store attributes as well
            List<Profile> profiles = Profile.findAll {
                eq("draft.attributes.constraintList", term.uuid)
            }

            return (attributes.size() + profiles.size())
        } else {
            return 0
        }

    }

    Map<String, Integer> replaceUsagesOfTerm(String opusId, jsonMap) {

        def json = jsonMap.list
        log.debug("Replacing vocabulary term usages: ${json}")

        Map<String, Integer> replacedUsages = [:]

        Opus opus = Opus.findByUuid(opusId)

        json.each { replacement ->
             int replaced = replaceTerm(opus, replacement.vocabId, replacement.existingTermId, replacement.newTermName)

            replacedUsages << [(replacement.existingTermName): replaced]
        }

        replacedUsages
    }



    def replaceTerm = { opus, vocabId, existingTermId, newTermName ->

        def db = mongo.getDatabase(grailsApplication.config.getProperty('grails.mongodb.databaseName'))
        int replacedUsages = 0

        // There can be more than 1 terms having same term names. So, checking by term id is necessary.
        Term existingTerm = Term.findByVocabAndUuid(Vocab.findByUuid(vocabId), existingTermId)
        Term newTerm = getOrCreateTerm(newTermName, vocabId, existingTermId)

        //Make sure both existing and new terms are valid before updating
        if (existingTerm && newTerm) {

            // Check if this is acknowledgement term
            if (opus.authorshipVocabUuid == vocabId) {

                // Bulk update for profiles and profiles draft acknowledgement term as GORM update takes long time for many records.
                MongoCollection<Profile> profileCollection = mongo.getDatabase(grailsApplication.config.getProperty('grails.mongodb.databaseName')).getCollection('profile')
                def updateQuery = new BasicDBObject('$set', new BasicDBObject('authorship.$.category', newTerm.id))
                def searchQuery = new BasicDBObject(['authorship.category': existingTerm.id])
                UpdateResult updateResult = profileCollection.updateMany(searchQuery, updateQuery)
                if (updateResult && updateResult.getModifiedCount()) {
                    replacedUsages = updateResult.getModifiedCount()
                }

                def draftUpdateQuery = new BasicDBObject('$set', new BasicDBObject('draft.authorship.$.category', newTerm.id))
                def draftSearchQuery = new BasicDBObject(['draft.authorship.category': existingTerm.id])
                UpdateResult draftUpdateResult = profileCollection.updateMany(draftSearchQuery, draftUpdateQuery)
                if (draftUpdateResult && draftUpdateResult.getModifiedCount()) {
                    replacedUsages += draftUpdateResult.getModifiedCount()
                }

            } else {
                MongoCollection<Profile> profileCollection = mongo.getDatabase(grailsApplication.config.getProperty('grails.mongodb.databaseName')).getCollection('profile')
                MongoCollection<Attribute> attributeCollection = mongo.getDatabase(grailsApplication.config.getProperty('grails.mongodb.databaseName')).getCollection('attribute')

                def attributeUpdateQuery = new BasicDBObject('$set', new BasicDBObject('title', newTerm.id))
                def attributeSearchQuery = new BasicDBObject('title': existingTerm.id)
                UpdateResult attributeUpdateResult = attributeCollection.updateMany(attributeSearchQuery, attributeUpdateQuery)
                if (attributeUpdateResult && attributeUpdateResult.getModifiedCount()) {
                    replacedUsages = attributeUpdateResult.getModifiedCount()
                }

                def draftUpdateQuery = new BasicDBObject('$set', new BasicDBObject('draft.attributes.$.title', newTerm.id))
                def draftSearchQuery = new BasicDBObject(['draft.attributes.title': existingTerm.id])
                UpdateResult draftUpdateResult = profileCollection.updateMany(draftSearchQuery, draftUpdateQuery)
                if (draftUpdateResult && draftUpdateResult.getModifiedCount()) {
                    replacedUsages += draftUpdateResult.getModifiedCount()
                }

            }

            if (replacedUsages > 0) {
                existingTerm.delete()
            }
        }

        replacedUsages
    }
}
