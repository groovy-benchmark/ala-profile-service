package au.org.ala.profile.marshaller

import au.org.ala.profile.Comment
import grails.converters.JSON

class CommentMarshaller {
    void register() {
        JSON.registerObjectMarshaller(Comment) { Comment comment ->
            return [
                    uuid       : comment.uuid,
                    text       : comment.text,
                    author     : [userId: comment.author.userId, name: comment.author.name],
                    dateCreated: comment.dateCreated,
                    children   : comment.children
            ]
        }
    }

}
