import grails.util.Environment
import org.apache.catalina.startup.Tomcat

import java.text.SimpleDateFormat

eventCreateWarStart = { warName, stagingDir ->
    ant.propertyfile(file: "${stagingDir}/WEB-INF/classes/application.properties") {
        entry(key: "app.build", value: new SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(new Date()))
    }
}

eventConfigureTomcat = { Tomcat tomcat ->
    if (Environment.current == Environment.DEVELOPMENT) {
        println "### Enabling AJP/1.3 connector"

        def ajpConnector = new Connector("org.apache.coyote.ajp.AjpProtocol")
        ajpConnector.port = 8010
        ajpConnector.protocol = 'AJP/1.3'
        ajpConnector.redirectPort = 8443
        ajpConnector.enableLookups = false
        ajpConnector.URIEncoding = 'UTF-8'
        ajpConnector.setProperty('redirectPort', '8443')
        ajpConnector.setProperty('protocol', 'AJP/1.3')
        ajpConnector.setProperty('enableLookups', 'false')
        ajpConnector.setProperty('URIEncoding', 'UTF-8')
        tomcat.service.addConnector ajpConnector

        println ajpConnector.toString()


        println "### Ending enabling AJP connector"


        println "### Enabling customised HTTP connector with unlimited POST size"

        // FOAImport.groovy sample data will endup in a massive call to profile-services which will exceed the
        // maximun post size unless we remove such restriction in this connector.

        // Looks like grails default connector is configured/created after this eventConfigureTomcat event so it was
        // not possible to tap into it hence the need to create an additional one
        def httpConnector = new Connector("org.apache.coyote.http11.Http11Protocol")
        httpConnector.port = 8008
        httpConnector.maxPostSize = 0 // Unlimited
        httpConnector.URIEncoding = 'UTF-8'
        httpConnector.setProperty('redirectPort', '8443')
        httpConnector.setProperty('protocol', 'HTTP/1.1')
        httpConnector.setProperty("maxPostSize", "0") // Unlimited
        httpConnector.setProperty('URIEncoding', 'UTF-8')
        tomcat.service.addConnector httpConnector

        println httpConnector.toString()

        println "### Ending enabling customised HTTP connector"


    }
}