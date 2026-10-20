@Grab('org.codehaus.groovy:groovy-json:2.4.17')
@Grab(group = 'org.codehaus.groovy.modules.http-builder', module = 'http-builder', version = '0.7.1')
@Grab('com.xlson.groovycsv:groovycsv:1.0')

import groovy.json.JsonSlurper
import groovy.json.JsonBuilder
import groovyx.net.http.RESTClient
import java.net.URLEncoder
import static com.xlson.groovycsv.CsvParser.parseCsv

class CreateDraftProfile {
    static final String FILE_ENCODING = "utf-8"

    static void main (args) {
        def cli = new CliBuilder(usage: "groovy CreateDraftProfile -f <datadir> -o opusId -p <profileServiceBaseUrl> -u <emailAddress> -r <reportfile>")
        cli.f(longOpt: "dir", "source data directory", required: true, args: 1)
        cli.o(longOpt: "opusId", "UUID of the FOA Opus", required: true, args: 1)
        cli.p(longOpt: "profileServiceBaseUrl", "Base URL of the profile service", required: true, args: 1)
        cli.u(longOpt: "userName", "Email address of the ALA user importing script", required: true, args: 1)
        cli.r(longOpt: "reportFile", "File to write the results of the import to", required: false, args: 1)

        OptionAccessor opt = cli.parse(args)

        if(!opt) {
            cli.usage()
            return
        }

        String DEFAULT_ALA_AUTH_COOKIE_NAME = "ALA-Auth"
        String PROFILE_SERVICE_URL = opt.p
        String USER_EMAIL = opt.u
        String opusId = opt.o
        String DATA_DIR = opt.f
        String OUTPUT_FILE = opt.r ?: "draft.json"
        Map result  = [total: 0, numberOfSuccess: 0, numberOfFailure: 0, success: [], failure: []]

        File output = new File(OUTPUT_FILE);
        if (output.exists()) {
            output.delete()
            output.createNewFile()
        }

        def csv = parseCsv(new File("${DATA_DIR}/foa_export_name.csv").newReader(FILE_ENCODING))
        csv.each { line ->
            String profileId = URLEncoder.encode(line.NAME, FILE_ENCODING)
            profileId = profileId.replace('+', '%20')
            String PROFILE_REQUEST_URL = "$PROFILE_SERVICE_URL/opus/$opusId/profile/$profileId/toggleDraftMode/"
            try {
                println PROFILE_REQUEST_URL
                def service = new RESTClient(PROFILE_REQUEST_URL)
                def resp = service.post ( headers : ["Cookie": DEFAULT_ALA_AUTH_COOKIE_NAME + "=" + URLEncoder.encode(USER_EMAIL, FILE_ENCODING) + ";"] )
                if (resp.success) {
                    result.success.add(line.NAME)
                    println("Success $line.NAME")
                }
            } catch (Exception e) {
                result.failure.add(line.NAME)
                println("Failed to draft $line.NAME")
                println(e.stackTrace)
            }
        }

        result.numberOfSuccess = result.success.size()
        result.numberOfFailure = result.failure.size()
        result.total = result.numberOfSuccess + result.numberOfFailure
        output << (new JsonBuilder(result)).toString()
    }
}