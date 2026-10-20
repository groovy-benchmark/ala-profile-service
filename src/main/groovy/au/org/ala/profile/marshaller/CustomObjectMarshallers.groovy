package au.org.ala.profile.marshaller

/**
 * Created by mar759 on 4/12/14.
 */
class CustomObjectMarshallers {

    List marshallers = []

    def register() {
        marshallers.each{ it.register() }
    }
}