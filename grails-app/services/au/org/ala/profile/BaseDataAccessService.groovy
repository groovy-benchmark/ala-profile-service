package au.org.ala.profile

import grails.converters.JSON
import org.bson.conversions.Bson

class BaseDataAccessService {
    boolean save(entity, flush = true) {
        checkState entity

        entity.save(flush: flush)

        boolean saved

        if (entity.errors.allErrors.size() > 0) {
            log.error("Failed to save ${entity}")
            entity.errors.each { log.error(it.toString()) }
            saved = false
        } else {
            log.info("Saved ${entity}")
            saved = true
        }

        saved
    }

    boolean delete(entity, flush = true) {
        checkState entity

        boolean deleted

        entity.delete(flush: flush)

        if (entity.errors.allErrors.size() > 0) {
            log.error("Failed to delete entity ${entity}")
            entity.errors.each { log.error(it.toString()) }
            deleted = false
        } else {
            log.info("Entity ${entity} deleted")
            deleted = true
        }

        deleted
    }

    /**
     * Throws an IllegalArgumentException if the provided argument is null or empty
     *
     * @param arg The argument to check
     * @param an optional message to include in the IllegalArgumentException
     */
    void checkArgument(arg, message = "") {
        if (arg == null || (arg.getMetaClass() && arg.getMetaClass().respondsTo(arg, "isEmpty") && arg.isEmpty())) {
            throw new IllegalArgumentException(message)
        }
    }

    /**
     * Throws an IllegalStateException if the provided state evaluates to false with
     *
     * @param state The state to check
     * @param message an optional message to include in the IllegalArgumentException
     */
    void checkState(state, message = "") {
        if (!state) {
            throw new IllegalStateException(message)
        }
    }

    Contributor getOrCreateContributor(String name, String userId = null) {
        Contributor contributor = userId ? Contributor.findByUserId(userId) : Contributor.findByName(name)
        if (!contributor) {
            // name and userId are both required fields for a new Contributor, so do not attempt creation if they are not valid
            checkArgument userId
            checkArgument name
            contributor = new Contributor(userId: userId, name: name)
            save contributor
        }
        contributor
    }

    static String toJSON(target, defaultValue = "{}") {
        target = target ?: defaultValue
        (target as JSON).toString()
    }

    static Bson getBsonDocument(String json) {
        org.bson.Document.parse(json)
    }

    static Bson getBsonDocument(Map target) {
        getBsonDocument(toJSON(target))
    }

    static Bson getBsonDocument(List target) {
        getBsonDocument(toJSON(target))
    }

}
