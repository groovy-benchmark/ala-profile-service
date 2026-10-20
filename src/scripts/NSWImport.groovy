@Grab(group = 'org.codehaus.groovy.modules.http-builder', module = 'http-builder', version = '0.7')
@Grab('org.apache.commons:commons-lang3:3.3.2')

import groovyx.net.http.RESTClient
import groovy.xml.MarkupBuilder

import java.text.SimpleDateFormat

import static org.apache.commons.lang3.StringEscapeUtils.unescapeHtml4

import static groovyx.net.http.ContentType.JSON

class NSWImport {
    static final String NSW_FLORA_IMAGE_URL_PREFIX = "http://plantnet.rbgsyd.nsw.gov.au/HerbLink/multimedia/"


    static void main(args) {
        def cli = new CliBuilder(usage: "groovy NSWImport -f <datafile> -o opusId -p <profileServiceBaseUrl> -d <delimiter default ~> -r <reportfile>")
        cli.f(longOpt: "file", "source data file", required: true, args: 1)
        cli.o(longOpt: "opusId", "UUID of the NSW Flora Opus", required: true, args: 1)
        cli.p(longOpt: "profileServiceBaseUrl", "Base URL of the profile service", required: true, args: 1)
        cli.d(longOpt: "delimiter", "Data file delimiter (defaults to ~)", required: false, args: 1)
        cli.r(longOpt: "reportFile", "File to write the results of the import to", required: false, args: 1)

        OptionAccessor opt = cli.parse(args)

        if (!opt) {
            cli.usage()
            return
        }

        String NSW_OPUS_ID = opt.o
        String DATA_FILE = opt.f
        String REPORT_FILE = opt.r ?: "report.txt"
        String PROFILE_SERVICE_IMPORT_URL = "${opt.p}/import/profile"
        String PROFILE_SERVICE_REPORT_URL = "${opt.p}/"
        String DELIMITER = opt.d ?: "~"

        List profiles = []

        println "Processing file..."
        int count = 0

        Map<String, List<Integer>> scientificNames = [:]
        Map<Integer, String> invalidLines = [:]

        File report = new File(REPORT_FILE)
        if (report.exists()) {
            report.delete()
            report.createNewFile()
        }

        List collectionImages = []

        new File(DATA_FILE).eachLine { line ->
            if (count++ % 50 == 0) println "Processing line ${count}..."

            def fields = clean(line).split(DELIMITER)

            List attributes = []

            String cl = (fields[0] == "{cl}" && fields.length >= 36) ? fields[35] : ""
            String family = fields[1]
            String subFamily = (fields[0] == "{sf}" && fields.length >= 42) ? fields[41] : ""
            String species = fields[2..4].join(" ").trim()
            String nameAuthor = fields[22]

            if (!species && subFamily) {
                species = subFamily
            }
            if (!species && family) {
                species = family
            }
            if (!species && cl) {
                species = cl
            }

            String scientificName = "${species}".trim()

            String contributor = fields[6]

            List<String> images = fields[24..33].findAll { it }
            if (images) {
                images?.each {
                    collectionImages << [scientificName: scientificName, title: scientificName, identifier: NSW_FLORA_IMAGE_URL_PREFIX + it]
                }
            }

            String suppliedTaxonomy = fields[1..4].join("<p/>").trim()
            attributes << [title: "Supplied Taxonomy", text: suppliedTaxonomy, creators: [contributor], stripHtml: false]

            String commonName = fields[5]
            if (commonName) {
                attributes << [title: "Common Name", text: commonName, creators: [contributor], stripHtml: false]
            }

            String habit = fields[7]
            if (habit) {
                attributes << [title: "Habit", text: habit, creators: [contributor], stripHtml: false]
            }

            String leaves = fields[8]
            if (leaves) {
                attributes << [title: "Leaves", text: leaves, creators: [contributor], stripHtml: false]
            }

            String flowers = fields[9]
            if (flowers) {
                attributes << [title: "Flowers", text: flowers, creators: [contributor], stripHtml: false]
            }

            String fruit = fields[10]
            if (fruit) {
                attributes << [title: "Fruit", text: fruit, creators: [contributor], stripHtml: false]
            }

            String flowering = fields[11]
            if (flowering) {
                attributes << [title: "Flowering", text: flowering, creators: [contributor], stripHtml: false]
            }

            String occurrence = fields[12..15].join("<p/>").trim()
            if (occurrence) {
                attributes << [title: "Occurrence", text: occurrence, creators: [contributor], stripHtml: false]
            }

            String otherNotes = (fields[18..19] + fields[21]).join("<p/>").trim()
            if (otherNotes) {
                attributes << [title: "Notes", text: otherNotes, creators: [contributor], stripHtml: false]
            }

            String synonyms = fields[17]
            if (synonyms) {
                attributes << [title: "Synonyms", text: synonyms, creators: [contributor], stripHtml: false]
            }

            String taxonConcept = fields[23]
            if (taxonConcept) {
                attributes << [title: "Taxon Concept", text: taxonConcept, creators: [contributor], stripHtml: false]
            }

            if (!scientificName) {
                invalidLines[count] = "Unable to determine scientfic name: ${line.substring(0, Math.min(line.size(), 100))}..."
            } else if (!scientificNames.containsKey(scientificName)) {
                Map profile = [scientificName              : scientificName,
                               nslNomenclatureMatchStrategy: "NSL_SEARCH",
                               nslNomenclatureMatchData    : [taxonConcept],
                               nameAuthor                  : nameAuthor,
                               attributes                  : attributes,
                               fullName                    : "${scientificName} ${nameAuthor}".trim()]

                profiles << profile
            }

            scientificNames.get(scientificName, []) << count
        }

        Map opus = [
                opusId  : NSW_OPUS_ID,
                profiles: profiles
        ]

        println "Creating images files..."
        createImageFiles(collectionImages)

        println "Importing..."
        def service = new RESTClient(PROFILE_SERVICE_IMPORT_URL)

        def resp = service.post(body: opus, requestContentType: JSON)

        String importId = resp.data.id

        println "Import report will be available at ${PROFILE_SERVICE_REPORT_URL}import/${importId}/report"

        int sleepTime = 5 * 60 * 1000
        println "${new SimpleDateFormat("HH:mm:ss.S").format(new Date())} Waiting for import to complete..."
        Thread.sleep(sleepTime)

        service = new RESTClient("${PROFILE_SERVICE_REPORT_URL}import/${importId}/report")
        resp = service.get([:]).data

        while (resp.status == "IN_PROGRESS") {
            println "${new SimpleDateFormat("HH:mm:ss.S").format(new Date())} Waiting for import to complete..."
            Thread.sleep(sleepTime)

            resp = service.get([:]).data
        }

        if (invalidLines) {
            report << "Invalid lines from source file:\n"
            invalidLines.each {k, v ->
                report << "\tLine ${k}: ${v}\n"
            }
        }

        if (scientificNames.any {k, v -> v.size() > 1 && k != null}) {
            report << "\n\nDuplicate scientific names (only the first record will be imported): \n"
            scientificNames.each { k, v ->
                if (v.size() > 1 && k) {
                    report << "\t${k}, on lines ${v}. Line ${v.first()} was imported.\n"
                }
            }
        }

        int success = 0
        int failed = 0
        int warnings = 0
        report << "\n\nImport results: \n"
        report << "\nStarted: ${resp.report.started}"
        report << "\nFinished: ${resp.report.finished}"

        resp.report.profiles.each { k, v ->
            if (v.status.startsWith("success")) {
                success++
            } else if (v.status.startsWith("warning")) {
                warnings++
            } else {
                failed++
            }
        }

        report << "\n\nImported ${success} of ${count} profiles with ${failed} errors and ${warnings} warnings\n\n"

        resp.report.profiles.each { k, v ->
            if (v.status.startsWith("warning")) {
                report << "\t${k} succeeded with ${v.warnings.size()} warnings:\n"
                v.warnings.each {
                    report << "\t\t${it}\n"
                }
            } else if (v.status.startsWith("error")) {
                report << "\t${k} failed with ${v.errors.size()} errors and ${v.warnings.size()} warnings:\n"
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

    static String clean(String str) {
        unescapeHtml4(str)
    }

    static createImageFiles(List collectionImages) {
        String now = new SimpleDateFormat("yyyyMMdd").format(new Date())

        File dir = new File("nsw_images_${now}")
        if (dir.exists()) {
            dir.list().each {
                new File(dir, it).delete()
            }
            dir.delete()
            println "Deleted dir"
        }
        dir.mkdir()

        File images = new File(dir, "images.csv")
        images << "coreID,identifier,title\n"

        File export = new File(dir, "export.csv")
        export << "dcterms:type,basisOfRecord,catalogNumber,scientificName\n"

        File metadata = new File(dir, "meta.xml")

        int row = 1
        collectionImages.each {
            images << "\"${row}\",\"${it.identifier}\",\"${it.title}\"\n"
            export << "\"Image\",\"HumanObservation\",\"${row}\",\"${it.scientificName}\"\n"
            row++
        }

        MarkupBuilder xml = new MarkupBuilder(new FileWriter(metadata))

        xml.mkp.xmlDeclaration(version: "1.0", encoding: "utf-8")

        xml.archive(xmlns: "http://rs.tdwg.org/dwc/text/") {
            core(encoding: "UTF-8",
                    linesTerminatedBy: "\\r\\n",
                    fieldsTerminatedBy: ",",
                    fieldsEnclosedBy: '"',
                    ignoreHeaderLines: "1",
                    rowTye: "http://rs.tdwg.org/dwc/terms/Occurrence") {
                files() {
                    location("export.csv")
                }
                id(index: 2)
                field(index: 0, term: "http://purl.org/dc/terms/type")
                field(index: 1, term: "http://rs.tdwg.org/dwc/terms/basisOfRecord")
                field(index: 2, term: "http://rs.tdwg.org/dwc/terms/catalogNumber")
                field(index: 3, term: "http://rs.tdwg.org/dwc/terms/scientificName")
            }
            extension(encoding: "UTF-8",
                    linesTerminatedBy: "\\r\\n",
                    fieldsTerminatedBy: ",",
                    fieldsEnclosedBy: '"',
                    ignoreHeaderLines: "1",
                    rowTye: "http://rs.gbif.org/terms/1.0/Image") {
                files() {
                    location("images.csv")
                }
                coreid(index: 0)
                field(index: 1, term: "http://purl.org/dc/terms/identifier")
                field(index: 2, term: "http://purl.org/dc/terms/title")
            }
        }
    }
}
