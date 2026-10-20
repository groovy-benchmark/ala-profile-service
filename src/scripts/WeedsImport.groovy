@Grab(group = 'org.codehaus.groovy.modules.http-builder', module = 'http-builder', version = '0.7')
@Grab('com.xlson.groovycsv:groovycsv:1.0')
@Grab('org.apache.commons:commons-lang3:3.3.2')
@Grab("org.jsoup:jsoup:1.8.2")

import groovyx.net.http.RESTClient
import org.apache.commons.lang.StringUtils
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

import java.text.SimpleDateFormat

import static groovyx.net.http.ContentType.JSON

class WeedsImport {

    static String OPUS_ID
    static String REPORT_FILE
    static String PROFILE_SERVICE_IMPORT_URL
    static String PROFILE_SERVICE_REPORT_URL
    static String ROOT_PAGE = "http://www.environment.gov.au/cgi-bin/biodiversity/invasive/weeds/weedspeciesindex.pl?id=701&IndexBy=sciname&startLetter="
    static String PROFILE_PAGE_PREFIX = "http://www.environment.gov.au/cgi-bin/biodiversity/invasive/weeds/"

    static void main(args) {
        def cli = new CliBuilder(usage: "groovy WeedsImport  -o opusId -p <profileServiceBaseUrl> -r <reportfile>")
        cli.o(longOpt: "opusId", "UUID of the FOA Opus", required: true, args: 1)
        cli.p(longOpt: "profileServiceBaseUrl", "Base URL of the profile service", required: true, args: 1)
        cli.r(longOpt: "reportFile", "File to write the results of the import to", required: false, args: 1)

        OptionAccessor opt = cli.parse(args)

        if (!opt) {
            cli.usage()
            return
        }

        OPUS_ID = opt.o
        REPORT_FILE = opt.r ?: "report.txt"
        PROFILE_SERVICE_IMPORT_URL = "${opt.p}/import/profile"
        PROFILE_SERVICE_REPORT_URL = "${opt.p}/"

        File report = new File(REPORT_FILE)
        if (report.exists()) {
            report.delete()
            report.createNewFile()
        }

        List profiles = []

        int count = 0

        ('A'..'Z').each { String letter ->
            Document listDoc = Jsoup.connect(ROOT_PAGE + letter).timeout(60000).get()

            def rows = listDoc.select("tr")
            List pages = rows.findResults { it.select("td > a")?.attr("href") }

            println "Processing ${pages.size()} pages for ${letter}"

            pages.each {
                if (!it) {
                    return
                }

                Document profilePage = Jsoup.connect("${PROFILE_PAGE_PREFIX}${it}").get()

                String scientificName = profilePage.select("h1")[1].text()
                println "${count++} Procesing ${scientificName} from ${it}"

                List attributes = []

                List<Element> attributeRows = profilePage.select("#tabdescription tr")
                List<String> ignoreRows = ["&nbsp;", "Photograph", "Distribution map", ""]
                attributeRows.eachWithIndex { Element row, int index ->
                    String title = row.child(0).text().trim().replaceAll(":", "")
                    title = index == 1 ? "Description" : title

                    String text = row.children().size() == 2 ? row.child(1).html().trim() : ""
                    text = text.replaceAll("<[a-zA-Z]+> *</[a-zA-Z]+>", "")
                    if (text.length() > 1 && !ignoreRows.contains(title) && title.length() > 1) {
                        attributes << [title: title, text: "<p>${text}</p>".toString()]
                    }
                }

                attributeRows = profilePage.select("#names tr")
                ignoreRows = ["&nbsp;", "Family", "Genus", "Species", "Scientific Name", "Australian Plant Name Index (APNI) link"]
                attributeRows.each { Element row ->
                    String title = row.child(0).text().trim().replaceAll(":", "")
                    String text = row.children().size() == 2 ? row.child(1).html().trim() : ""
                    text = text.replaceAll("<[a-zA-Z]+></[a-zA-Z]+>", "")
                    if (text.length() > 1 && !ignoreRows.contains(title) && title.length() > 1) {
                        attributes << [title: title, text: "<p>${text}</p>".toString()]
                    }
                }

                attributeRows = profilePage.select("#management tr")
                ignoreRows = ["&nbsp;"]
                attributeRows.each { Element row ->
                    String title = row.child(0).text().trim().replaceAll(":", "")
                    String text = row.children().size() == 2 ? row.child(1).html().trim() : ""
                    text = text.replaceAll("<[a-zA-Z]+></[a-zA-Z]+>", "")
                    if (title == "Weed declared") {
                        text = "<ul>"
                        List<String> states = row.child(1).select("table tr:first-child th")*.text()
                        List<String> values = row.child(1).select("table tr:last-child td")*.text()
                        [states, values].transpose().each {
                            text += "<li>${it[0]}: ${StringUtils.capitalize(it[1].toLowerCase())}</li>".toString()

                        }
                        text += "</ul>"
                        attributes << [title: title, text: text]
                    } else if (text.length() > 1 && !ignoreRows.contains(title) && title.length() > 1) {
                        attributes << [title: title, text: "<p>${text}</p>".toString()]
                    }
                }

                List<String> bibliography = profilePage.select("#resources table p")*.html()*.trim()
                String acknowledgement = profilePage.select("#resources table tr:last-child td:last-child")[0].text().trim()

                Map profile = [scientificName: scientificName,
                               fullName      : scientificName,
                               attributes    : attributes,
                               bibliography  : bibliography,
                               authorship    : acknowledgement ? [[category: "Profile acknowledgements and updates", text: acknowledgement]] : []
                ]

                profiles << profile
            }
        }

        Map opus = [
                opusId  : OPUS_ID,
                profiles: profiles
        ]

        println "Importing ${profiles.size()} profiles..."

        def service = new RESTClient(PROFILE_SERVICE_IMPORT_URL)

        def resp = service.post(body: opus, requestContentType: JSON)

        String importId = resp.data.id

        println "Import report will be available at ${PROFILE_SERVICE_REPORT_URL}import/${importId}/report"

        int sleepTime = 1 * 30 * 1000
        println "${new SimpleDateFormat("HH:mm:ss.S").format(new Date())} Waiting for import to complete..."
        Thread.sleep(sleepTime)

        service = new RESTClient("${PROFILE_SERVICE_REPORT_URL}import/${importId}/report")
        resp = service.get([:]).data

        while (resp.status == "IN_PROGRESS") {
            println "${new SimpleDateFormat("HH:mm:ss.S").format(new Date())} Waiting for import to complete..."
            Thread.sleep(sleepTime)

            resp = service.get([:]).data
        }

    }
}