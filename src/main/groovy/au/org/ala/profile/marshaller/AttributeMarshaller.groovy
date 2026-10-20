package au.org.ala.profile.marshaller

import au.org.ala.profile.Attribute
import au.org.ala.profile.Opus
import au.org.ala.profile.Term
import au.org.ala.profile.Profile
import au.org.ala.profile.util.Utils
import grails.converters.JSON
import au.org.ala.profile.NumberRange

class AttributeMarshaller {

    void register() {
        JSON.registerObjectMarshaller(Attribute) { Attribute attr ->
            return [
                    uuid                  : attr.uuid,
                    title                 : attr.title.name,
                    order                 : attr.title.order,
                    required              : attr.title.required,
                    containsName          : attr.title.containsName,
                    summary               : attr.title.summary,
                    text                  : getContent(attr),
                    numbers               : attr.numbers,
                    numberRange           : attr.numberRange ? marshalNumberRange(attr.numberRange) : null,
                    constraintListVocab   : attr.title.constraintListVocab,
                    constraintList        : attr.constraintList,
                    constraintListExpanded: attr.constraintListExpanded?.collect {
                        marshalTerm(it)
                    },
                    source                : attr.source,
                    plainText             : Utils.cleanupText(attr.text),
                    creators              : attr.creators?.collect { it.name },
                    editors               : attr.editors?.collect { it.name },
                    original              : attr.original,
                    dataType              : attr.title.dataType ?: 'text',
                    groupName             : attr.title?.groupBy?.name ?: "",
                    groupBy               : attr.title?.groupBy ? marshalTerm(attr.title?.groupBy) : null,
                    unit                  : attr.title.unit,
                    profile               : attr.profile ? marshalProfile(attr.profile) : null
            ]
        }
    }

    def marshalProfile(Profile profile) {
        return [
                uuid          : profile.uuid,
                scientificName: profile.scientificName,
                opus          : marshalOpus(profile.opus)
        ]
    }

    def marshalOpus(Opus opus) {
        return [
                uuid     : opus.uuid,
                title    : opus.title,
                shortName: opus.shortName
        ]
    }

    def marshalTerm(Term term) {
        [
                name        : term.name,
                order       : term.order,
                required    : term.required,
                containsName: term.containsName,
                summary     : term.summary,
                uuid        : term.uuid
        ]
    }

    def marshalNumberRange(NumberRange range) {
        [
                from         : range.from,
                to           : range.to,
                fromInclusive: range.fromInclusive,
                toInclusive  : range.toInclusive
        ]
    }

    def getContent(Attribute attribute) {
        String content
        switch (attribute.title.dataType) {
            case 'number':
                content = attribute.numbers?.join(', ') ?: '';
                break;
            case 'range':
                content = attribute.numberRange ? "From - ${attribute.numberRange.to} <br> To - ${attribute.numberRange.from}" : "";
                break;
            case 'list':
            case 'singleselect':
                if (attribute.constraintList) {
                    def list = attribute.constraintListExpanded?.sort();
                    def names = attribute.constraintListExpanded?.collect {
                        it.name
                    }

                    content = names.join(', ');
                }
                break;
            case 'text':
            default:
                content = attribute.text
                break
        }

        content
    }
}