@Grab(group = 'org.codehaus.groovy.modules.http-builder', module = 'http-builder', version = '0.7')
@Grab('org.apache.commons:commons-lang3:3.3.2')
@Grab('com.xlson.groovycsv:groovycsv:1.0')

import groovyx.net.http.RESTClient
import groovy.xml.MarkupBuilder

import java.text.SimpleDateFormat

import static org.apache.commons.lang3.StringEscapeUtils.unescapeHtml4
import static com.xlson.groovycsv.CsvParser.parseCsv
import static groovyx.net.http.ContentType.JSON

class MangroveWatchImport {


    static void main(args) {
        def cli = new CliBuilder(usage: "groovy MangroveWatchImport -f <datafile> -o opusId -p <profileServiceBaseUrl> -r <reportfile> -t <bearer token>")
        cli.f(longOpt: "file", "source data file", required: true, args: 1)
        cli.o(longOpt: "opusId", "UUID of the NSW Flora Opus", required: true, args: 1)
        cli.p(longOpt: "profileServiceBaseUrl", "Base URL of the profile service", required: true, args: 1)
        cli.r(longOpt: "reportFile", "File to write the results of the import to", required: false, args: 1)
        cli.t(longOpt: "token", "Bearer token", required: false, args: 1)
        OptionAccessor opt = cli.parse(args)

        if (!opt) {
            cli.usage()
            return
        }

        String OPUS_ID = opt.o
        String DATA_FILE = opt.f
        String REPORT_FILE = opt.r ?: "report.txt"
        String PROFILE_SERVICE_IMPORT_URL = "${opt.p}/import/profile"
        String PROFILE_SERVICE_REPORT_URL = "${opt.p}/"
        String TOKEN = opt.t ?: ""

        List profiles = []

        println "Processing file..."
        int count = 0

        Map<String, List<Integer>> scientificNames = [:]
        Map<Integer, String> invalidLines = [:]
        String FILE_ENCODING = "UTF-8"
        String speciesField = "Species Name",
                nameField = "Species Authority Name"


        File rpt = new File(REPORT_FILE)
        if (rpt.exists()) {
            rpt.delete()
            rpt.createNewFile()
        }

        List attributeNames = [
                "Plant Structure",
                "Stem Spines",
                "Stem Base",
                "Roots Exposed",
                "Milky Sap",
                "Leaf Position",
                "Leaf Form",
                "Leaflet No.",
                "Leaflet Position",
                "Leaf Shape",
                "Leaf Length",
                "Leaf Veins",
                "Leaf Margin",
                "Leaf Upper",
                "Leaf Beneath",
                "Leaf Apex",
                "Leaf Mucro",
                "Petiole Base",
                "Inflorescence Position",
                "Inflorescence Structure",
                "Inflorescence State",
                "Flower No",
                "Joints",
                "Peduncle-Pedicel L",
                "Peduncle-Pedicel L (range)",
                "Calyx Lobe Nos.",
                "Calyx Margin",
                "Calyx Surface",
                "Bracteoles",
                "Flower Shape",
                "Flower Size",
                "Petal Colour",
                "Petal No.",
                "Petal Surface Inner",
                "Petal Bristle N",
                "Petal Bristle N (range)",
                "Petal Spine",
                "Corolla Lobes",
                "Corolla Throat Surface",
                "Style L",
                "Style No.",
                "Stamen No",
                "Stamen No (list)",
                "Ovary Cells",
                "Fruit Shape",
                "Fruit Size",
                "Fruit Surface",
                "Propagule Stage",
                "Propagule Nos.",
                "Propagule Nos. (range)",
                "Propagule Nos. (list)",
                "Propagule Size",
                "Propagule Surface",
                "Radicle Surface",
                "Mangrove Or Associate",
                "Native?",
                "Species Name",
                "Genus",
                "Species",
                "Subspecies",
                "Variety",
                "Species Authority Name",
                "Common Name",
                "Synopsis Text",
                "Family Name",
                "Family Authority Name",
                "Genus Note Text",
                "Species Note Text",
                "Species Feature Text",
                "Derivation Text",
                "Phenology Text",
                "Distribution Text",
                "Growth Form Text",
                "Foliage Text",
                "Reproductive Part Text",
                "Dispersal Text",
                "Local Distribution Text",
                "Iucn Red List Text",
                "Distribution - Australia Text",
                "Distribution - World Countries",
                "Distribution - Australia NRM",
                "Genus Authority",
                "References",
                "Iucn Red List Comment"
        ]

        def csv = parseCsv(new File(DATA_FILE).newReader(FILE_ENCODING))
        csv.each { line ->
            List attributes = []
            String scientificName = line[speciesField]?.trim()
            String nameAuthority = line[nameField]?.trim()
            attributeNames.each { propertyName ->
                try {
                    def val = line.getProperty(propertyName)?.trim()
                    if (val) {
                        attributes << [title: propertyName, text: val, stripHtml: false]
                    }
                } catch (MissingPropertyException ex) {

                }
            }

            if (!scientificName) {
                invalidLines[count] = "Unable to determine scientfic name: ${line}..."
            } else if (!scientificNames.containsKey(scientificName)) {
                Map profile = [
                        scientificName              : scientificName,
                        nameAuthor: nameAuthority,
                        nslNomenclatureMatchStrategy: "NSL_SEARCH",
                        nslNomenclatureMatchData    : [scientificName],
                        attributes                  : attributes
                ]

                profiles << profile
            }

            scientificNames.get(scientificName, []) << count
        }

        Map opus = [
                opusId  : OPUS_ID,
                profiles: profiles
        ]

        println "Importing..."
        def service = new RESTClient(PROFILE_SERVICE_IMPORT_URL)
        def resp
        try {
            resp = service.post(body: opus, requestContentType: JSON, headers: [Authorization: "Bearer ${TOKEN}"])
        } catch (groovyx.net.http.HttpResponseException ex) {
            println ex.statusCode
            println ex.response.data
            return
        }


        String importId = resp.data.id

        println "Import report will be available at ${PROFILE_SERVICE_REPORT_URL}import/${importId}/report"

        int sleepTime = 5 * 60 * 1000
        println "${new SimpleDateFormat("HH:mm:ss.S").format(new Date())} Waiting for import to complete..."
        Thread.sleep(sleepTime)

        service = new RESTClient("${PROFILE_SERVICE_REPORT_URL}import/${importId}/report")
        resp = service.get(headers: [Authorization: "Bearer ${TOKEN}"]).data

        while (resp.status == "IN_PROGRESS") {
            println "${new SimpleDateFormat("HH:mm:ss.S").format(new Date())} Waiting for import to complete..."
            Thread.sleep(sleepTime)

            resp = service.get([:]).data
        }

        if (invalidLines) {
            rpt << "Invalid lines from source file:\n"
            invalidLines.each { k, v ->
                rpt << "\tLine ${k}: ${v}\n"
            }
        }

        if (scientificNames.any { k, v -> v.size() > 1 && k != null }) {
            rpt << "\n\nDuplicate scientific names (only the first record will be imported): \n"
            scientificNames.each { k, v ->
                if (v.size() > 1 && k) {
                    rpt << "\t${k}, on lines ${v}. Line ${v.first()} was imported.\n"
                }
            }
        }

        int success = 0
        int failed = 0
        int warnings = 0
        rpt << "\n\nImport results: \n"
        rpt << "\nStarted: ${resp.report.started}"
        rpt << "\nFinished: ${resp.report.finished}"

        resp.report.profiles.each { k, v ->
            if (v.status.startsWith("success")) {
                success++
            } else if (v.status.startsWith("warning")) {
                warnings++
            } else {
                failed++
            }
        }

        rpt << "\n\nImported ${success} of ${count} profiles with ${failed} errors and ${warnings} warnings\n\n"

        resp.report.profiles.each { k, v ->
            if (v.status.startsWith("warning")) {
                rpt << "\t${k} succeeded with ${v.warnings.size()} warnings:\n"
                v.warnings.each {
                    rpt << "\t\t${it}\n"
                }
            } else if (v.status.startsWith("error")) {
                rpt << "\t${k} failed with ${v.errors.size()} errors and ${v.warnings.size()} warnings:\n"
                rpt << "\t\tWarnings\n"
                v.warnings.each {
                    rpt << "\t\t\t${it}\n"
                }
                rpt << "\t\tErrors\n"
                v.errors.each {
                    rpt << "\t\t\t${it}\n"
                }
            }
        }

        println "Import finished. See ${rpt.absolutePath} for details"
    }

    static String clean(String str) {
        unescapeHtml4(str)
    }

}
