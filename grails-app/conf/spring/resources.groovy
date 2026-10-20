import au.org.ala.profile.marshaller.*
import au.org.ala.profile.sanitizer.SanitizerPolicy
// Place your Spring DSL code here
beans = {
    xmlns task: "http://www.springframework.org/schema/task"
    task.'annotation-driven'('proxy-target-class': true, 'mode': 'proxy')

    customObjectMarshallers(CustomObjectMarshallers) {
        marshallers = [
                new AuditMessageMarshaller(),
                new AttributeMarshaller(),
                new OpusMarshaller(),
                new ProfileMarshaller(),
                new PublicationMarshaller(),
                new GlossaryMarshaller(),
                new CommentMarshaller(),
                new AttachmentMarshaller()
        ]
    }

    sanitizerPolicy(SanitizerPolicy)
    springConfig.addAlias "persistenceInterceptor", "mongoPersistenceInterceptor"
}
