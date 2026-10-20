package au.org.ala.profile

import au.ala.org.ws.security.RequireApiKey
import grails.converters.JSON

@RequireApiKey
class VocabController extends BaseController {

    VocabService vocabService

    def index() {
        def vocabs = Vocab.findAll()
        def vocabsToRender = []
        vocabs.each { vocab ->
            vocabsToRender << [
                    "name"   : "${vocab.name}",
                    "vocabId": "${vocab.uuid}",
                    "strict" : "${vocab.strict ?: false}"
            ]
        }

        render vocabsToRender.sort { it.name.toLowerCase() } as JSON
    }

    def show() {
        def vocab = Vocab.findByUuid(params.vocabId)
        if (vocab) {
            def termsToRender = []

            vocab.terms.sort().eachWithIndex { term, index ->
                termsToRender << [
                        name        : term.name,
                        termId      : term.uuid,
                        groupBy     : term.groupBy ? [name: term.groupBy.name, termId: term.groupBy.uuid, order: term.groupBy.order, required: term.groupBy.required, summary: term.groupBy.summary, containsName: term.groupBy.containsName ] : null,
                        dataType    : term.dataType,
                        listTerms   : term.listTerms,
                        constraintListVocab: term.constraintListVocab,
                        unit: term.unit,
                        order       : term.order == -1 ? index : term.order,
                        required    : term.required,
                        summary     : term.summary,
                        containsName: term.containsName
                ]
            }

            def payload = [name: vocab.name, strict: vocab.strict ?: false, terms: termsToRender.sort {
                [it.order, it.name.toLowerCase()]
            }]
            render payload as JSON
        } else {
            notFound()
        }
    }

    def update() {
        Vocab vocab
        if (params.vocabId) {
            vocab = Vocab.findByUuid(params.vocabId);

            if (!vocab) {
                notFound()
            }
        }

        def json = request.getJSON()

        Map result = vocabService.updateVocab(params.vocabId, json);

        if (!result.updated) {
            saveFailed()
        } else {
            success([updated: true, vocabId: result.vocab.uuid])
        }
    }

    def findUsagesOfTerm() {
        if (!params.vocabId || !params.term) {
            badRequest()
        } else {
            int usages = vocabService.findUsagesOfTerm(params.opusId, params.vocabId, params.term)

            render([usageCount: usages] as JSON)
        }
    }

    def replaceUsagesOfTerm() {
        def json = request.getJSON();
        if (!json) {
            badRequest()
        } else {
            Map<String, Integer> usages = vocabService.replaceUsagesOfTerm(params.opusId, json)

            render([usages: usages] as JSON)
        }
    }

}
