@Grab(group = 'org.codehaus.groovy.modules.http-builder', module = 'http-builder', version = '0.7')
@Grab('com.xlson.groovycsv:groovycsv:1.0')
@Grab('org.apache.commons:commons-lang3:3.3.2')
@Grab("org.jsoup:jsoup:1.8.2")

import groovyx.net.http.RESTClient
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

import java.text.SimpleDateFormat

import static groovyx.net.http.ContentType.JSON


class TASImport {

    static String OPUS_ID
    static String REPORT_FILE
    static String PROFILE_SERVICE_IMPORT_URL
    static String PROFILE_SERVICE_REPORT_URL
    static String FLORA_OF_TAS_ROOT_PAGE = "http://demo1.tmag.tas.gov.au/treatments/current_accounts.html"
    static String FLORA_OF_TAS_FAMILY_PAGE = "http://demo1.tmag.tas.gov.au/treatments/"
    static List CONNECTING_TERMS = [" subsp.", " var.", " f.", " subg.", " sect.", " subsect.", "section", "series"]

    static void main(args) {
        def cli = new CliBuilder(usage: "groovy TASImport  -o opusId -p <profileServiceBaseUrl> -r <reportfile>")
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

        Document listDoc = Jsoup.connect(FLORA_OF_TAS_ROOT_PAGE).timeout(60000).get()

        def rows = listDoc.select("tr")
        List families = rows.findResults { it.select("td > a")?.attr("href") }

        println "Processing ${families.size()} families"

        List profiles = []

        int count = 0

        Map<String, List<Integer>> scientificNames = [:]

        families.each {
            String familyName = it
            println "Processing family ${it}..."

            Document familyDoc = Jsoup.connect("${FLORA_OF_TAS_FAMILY_PAGE}${it}").get()

            List toImport = familyDoc.select("p.header1, p.header2, p.header3, p.text, p.author, p.italics_common")

            List references = familyDoc.select("p.references")

            Map<String, List> blocks = [:].withDefault { [] }

            String lastHeading = null
            toImport.each {
                if (it.attr("class") =~ "header[12]" || (it.attr("class") == "header3") && !it.children().isEmpty() && it.children().toList().get(0).attr("class") == "bold") {
                    lastHeading = cleanHeading(it)

                    if (!lastHeading) {
                        println "No name for ${it.text()}"
                        return
                    }

                    blocks << [(lastHeading): []]
                    blocks[lastHeading].addAll(references)
                } else {
                    blocks[lastHeading] << it
                }
            }

            String author
            blocks.each { heading, lines ->
                String scientificName = heading
                if (scientificName == "REFERENCES") {
                    return
                } else if (!scientificName) {
                    println "No name. Lines = ${lines}"
                    return
                }

                List attributes = []
                Element authorElement = lines.find { it.attr("class") == "author" }
                lines.remove(authorElement)
                author = authorElement?.text()?.replaceAll("\\[[23]\\]", "") ?: author

                attributes << [title: "Common Name", text: findAndRemoveItem(lines, "italics_common"), creators: [], stripHtml: false]

                attributes << [title: "Key Reference", text: findAndRemoveItem(lines, "text", "Key reference:"), creators: [], stripHtml: false]
                attributes << [title: "Key Reference", text: findAndRemoveItem(lines, "text", "Key references:"), creators: [], stripHtml: false]

                attributes << [title: "Synonymy", text: findAndRemoveItem(lines, "text", "Synonymy:"), creators: [], stripHtml: false]

                attributes << [title: "External Resources", text: findAndRemoveItem(lines, "text", "External resources:"), creators: [], stripHtml: false]

                attributes << [title: "Illustrations", text: findAndRemoveItem(lines, "text", "Illustrations:"), creators: [], stripHtml: false]

                attributes << [title: "Notes", text: findAndRemoveItem(lines, "text", "Tas."), creators: [], stripHtml: false]
                attributes << [title: "Notes", text: findAndRemoveItem(lines, "text", "Tas,"), creators: [], stripHtml: false]

                List bibElements = lines.findAll { it.attr("class") == "references" }
                lines.removeAll(bibElements)
                List bibliography = bibElements.collect {
                    '<p>' + it.html() + '</p>'
                }

                String description = "<p>${lines.collect { it.html() }.join("</p><p>")}</p>"
                attributes << [title: "Description", text: description, creators: [], stripHtml: false]

                Map profile = [scientificName: scientificName,
                               fullName      : scientificName,
                               attributes    : attributes,
                               bibliography  : bibliography,
                               authorship    : author ? [[category: "Author", text: author]] : []
                ]

                if (!scientificNames.containsKey(scientificName.trim().toLowerCase())) {
                    profiles << profile
                }

                scientificNames.get(scientificName.trim().toLowerCase(), []) << familyName
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

        report << "\n\nImport results \n"
        report << "-------------- \n"
        report << "\nStarted: ${resp.report.started}"
        report << "\nFinished: ${resp.report.finished}"

        int duplicates = 0
        if (scientificNames.any { k, v -> v.size() > 1 && k != null }) {
            report << "\n\nDuplicate scientific names (only the first record will be imported): \n"
            scientificNames.each { k, v ->
                if (v.size() > 1 && k) {
                    report << "\t${k}, in families ${v}. Family ${v.first()} was imported.\n"
                    duplicates++
                }
            }
        }

        int success = 0
        int failed = 0
        int warnings = 0

        resp.report.profiles.each { k, v ->
            if (v.status.startsWith("success")) {
                success++
            } else if (v.status.startsWith("warning")) {
                warnings++
            } else {
                failed++
            }
        }

        report << "\n\nImported ${success + warnings} of ${count} profiles with ${warnings} warning(s), ${duplicates} duplicates and ${failed} error(s)\n\n"

        resp.report.profiles.each { k, v ->
            if (v.status.startsWith("warning")) {
                report << "\t${k} succeeded with ${v.warnings.size()} warnings:\n"
                v.warnings.each {
                    report << "\t\t${it}\n"
                }
            } else if (v.status.startsWith("error")) {
                report << "\t${k} failed with ${v.errors.size()} error(s) and ${v.warnings.size()} warning(s):\n"
                report << "\t\tWarnings\n"
                v.warnings.each {
                    report << "\t\t\t${it}\n"
                }
                report << "\t\tErrors\n"
                v.errors.each {
                    report << "\t\t\t${it}\n"
                }
            }
        }


        println "Import finished. See ${report.absolutePath} for details"
    }

    static String cleanHeading(Element headingElement) {
        String fullText = headingElement.text()

        String connectingTerm = ""
        CONNECTING_TERMS.each {
            if (fullText.contains(it)) {
                if (it == "series") {
                    connectingTerm = "ser."
                } else if (it == "section") {
                    connectingTerm = "sect."
                } else {
                    connectingTerm = it
                }
            }
        }

        String heading = headingElement.select(".bold").collect { it.text() }.join(" ")
        if (!heading) {
            heading = fullText
        }
        heading = heading.replaceFirst(/^.*? (.*?)/, "\1")
                .replaceAll("\\*", "")
                .replaceAll("\\[1\\]", "")
                .replaceAll(/^ *[12\?](.*)$/, "\1")
                .replaceAll("†", "").trim()
                .replaceAll(/^[12\?]/, "")
                .replaceAll(" +", " ").trim()

        List parts = heading.split(" ")
        if (!connectingTerm.isEmpty() && parts.size() > 2) {
            parts.add(parts.size() - 1, connectingTerm.trim())
        }

        parts.join(" ")
    }

    static String findAndRemoveItem(List<Element> lines, String name, String prefix = null) {
        Element item = lines.find {
            it.attr("class") == name && (!prefix || it.text().startsWith(prefix))
        }

        String line = null
        if (item) {
            lines.remove(item)

            line = "<p>${item.html()}</p>"

            if (prefix) {
                line = line.replace(prefix, "").replace("<span class='italics'>${prefix.replaceAll(":", "")}</span>:", "").trim()
            }
        }

        line
    }
}