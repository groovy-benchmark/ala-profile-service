package au.org.ala.profile

import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import org.springframework.beans.factory.annotation.Autowired

@Integration
@Rollback
class VocabServiceSpec extends BaseIntegrationSpec {
    @Autowired
    VocabService service

    def "test update vocab"() {
        given:
        String vocabId = "12345"
        Map data = [
                name          : "Updated Vocab",
                strict        : true,
                deleteExisting: true,
                terms         : [
                        [name: "Term 1", required: false, summary: false, containsName: false, dataType: 'text', groupBy: [termId: 'group1']],
                        [name: "Term 2", required: false, summary: false, containsName: false, dataType: 'text', groupBy: [termId: 'group1']],
                        [name: "Term 3", required: false, summary: false, containsName: false, dataType: 'text', groupBy: [termId: 'group1']],
                        [name: "Term 4", required: false, summary: false, containsName: false, dataType: 'text', groupBy: [termId: 'group1']]
                ]
        ]
        Vocab vocab = save new Vocab(uuid: vocabId, name: "Test Vocab", terms: [
                new Term(name: "Old Term 1", required: false, summary: false, containsName: false, dataType: 'text'),
                new Term(name: "Old Term 2", required: false, summary: false, containsName: false, dataType: 'text'),
                new Term(name: "Old Term 3", required: false, summary: false, containsName: false, dataType: 'text')
        ])

        Vocab groupBy = save new Vocab(uuid: 'groupVocab1', name: "Group vocab 1", terms: [
                new Term(uuid: 'group1', name: 'Group 1')
        ])

        when:
        Map result = service.updateVocab(vocabId, data)

        then:
        result.updated == true
        result.vocab.strict == true
        result.vocab.terms.size() == 4
        result.vocab.terms.collect { it.name } == ["Term 1", "Term 2", "Term 3", "Term 4"]
        result.vocab.terms[0].groupBy.name == 'Group 1'
    }

}
