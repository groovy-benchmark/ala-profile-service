// https://mvnrepository.com/artifact/org.apache.httpcomponents/httpclient
@Grab(group = 'org.apache.httpcomponents', module = 'httpclient', version = '4.2.1')
@Grab(group = 'org.codehaus.groovy.modules.http-builder', module = 'http-builder', version = '0.7.1')
@Grab('com.xlson.groovycsv:groovycsv:1.0')
@Grab('org.apache.commons:commons-lang3:3.3.2')

import groovyx.net.http.RESTClient
import groovy.xml.MarkupBuilder
import org.apache.commons.lang3.StringUtils

import java.text.SimpleDateFormat

import static groovyx.net.http.ContentType.JSON
import static com.xlson.groovycsv.CsvParser.parseCsv
import static org.apache.commons.lang3.StringEscapeUtils.unescapeHtml4

/**
 * Creates profiles that do not exist on database i.e. ignores profiles that already exists.
 */
class FOAImport {

    static final String FILE_ENCODING = "utf-8"

    static String OPUS_ID
    static String DATA_DIR
    static String REPORT_FILE
    static String PROFILE_SERVICE_IMPORT_URL
    static String PROFILE_SERVICE_REPORT_URL
    static String DELIMITER
    static String ACCESS_TOKEN


    static void main(args) {
        def cli = new CliBuilder(usage: "groovy FOAImport -f <datadir> -o opusId -p <profileServiceBaseUrl> -d <delimiter default ,> -r <reportfile> -a <accessToken>")
        cli.f(longOpt: "dir", "source data directory", required: true, args: 1)
        cli.o(longOpt: "opusId", "UUID of the FOA Opus", required: true, args: 1)
        cli.p(longOpt: "profileServiceBaseUrl", "Base URL of the profile service", required: true, args: 1)
        cli.d(longOpt: "delimiter", "Data file delimiter (defaults to ,)", required: false, args: 1)
        cli.r(longOpt: "reportFile", "File to write the results of the import to", required: false, args: 1)
        cli.a(longOpt: "accessToken", "Bearer token to access profiles service", required: true, args: 1)

        OptionAccessor opt = cli.parse(args)

        if (!opt) {
            cli.usage()
            return
        }

        OPUS_ID = opt.o
        DATA_DIR = opt.f
        REPORT_FILE = opt.r ?: "report.txt"
        PROFILE_SERVICE_IMPORT_URL = "${opt.p}/import/profile"
        PROFILE_SERVICE_REPORT_URL = "${opt.p}/"
        DELIMITER = opt.d ?: ","
        ACCESS_TOKEN = opt.a ?: ""

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

        // Gets a map of attribute name and its id - [1: "NOMENCLATURE", 2: "NOTE", 3: "SOURCE"]
        Map<String, String> attributeTitles = loadAttributeTitles()

        Map<String, Map<String, List<String>>> taxaAttributes = loadAttributes(attributeTitles)

        Map<String, List<Map<String, String>>> images = loadImages()
        Map<String, List<String>> maps = loadMaps()

        List collectionImages = []

        def csv = parseCsv(new File("${DATA_DIR}/taxon_TMAG_insects.csv").newReader(FILE_ENCODING))
        csv.each { line ->
            if (count++ % 50 == 0) println "Processing taxa line ${count}..."

            String id = line.TAXA_ID
            String scientificName = line.NAME?.trim()

            String fullName = constructFullName(line.NAME, line.GENUS, line.SPECIES, line.INFRASPECIES_RANK, line.INFRASPECIES, line.AUTHOR)

            List attributes = []
            Map<String, List<String>> attrs = taxaAttributes.get(id)
            if (attrs) {
                attrs.each { k, v ->
                    if (v && k != "Author") {
                        StringBuilder text = new StringBuilder()
                        v.each { text.append "<p>${it}</p>" }

                        attributes << [title: k, text: text.toString(), creators: [], stripHtml: false]
                    }
                }
            }

            Map<String, String> classification = [:]
            classification.family = line.FAMILY
            classification.genus = line.GENUS

            StringBuilder volume = new StringBuilder("<p>")
            volume.append(cleanupText(line.VOLUME_REFERENCE))
            volume.append("</p>")
//            attributes << [title: "Source citation", text: volume.toString(), creators: [], stripHtml: false]

            String author = attrs ? attrs["Author"]?.join(", ") : null

            Map profile = [scientificName: scientificName,
                           classification: classification,
                           nameAuthor: line.AUTHOR,
                           fullName: fullName,
                           attributes: attributes,
                           nslNomenclatureIdentifier: line.NSL_ID,
                           authorship: author ? [[category: "Author", text: author]] : []
            ]

            if (!scientificNames.containsKey(scientificName.trim().toLowerCase())) {
                profiles << profile
            }

            scientificNames.get(scientificName.trim().toLowerCase(), []) << count
        }

        Map opus = [
                opusId  : OPUS_ID,
                profiles: profiles
        ]

        println "Creating image files..."
        createImageFiles(collectionImages)

        println "Importing ${profiles.size()} profiles..."
        println(PROFILE_SERVICE_IMPORT_URL)
        def service = new RESTClient(PROFILE_SERVICE_IMPORT_URL)
        service.setHeaders(["Authorization": "Bearer ${ACCESS_TOKEN}"])
        service.handler.failure = { resp ->     println "Unexpected failure: ${resp.statusLine}"   }

        def resp = service.post(body: opus, requestContentType: JSON)

        String importId = resp.data.id

        println "Import report will be available at ${PROFILE_SERVICE_REPORT_URL}import/${importId}/report"

//        int sleepTime = 5 * 60 * 1000
        int sleepTime = 1 * 30 * 1000
        println "${new SimpleDateFormat("HH:mm:ss.S").format(new Date())} Waiting for import to complete..."
        Thread.sleep(sleepTime)

        service = new RESTClient("${PROFILE_SERVICE_REPORT_URL}import/${importId}/report")
        service.setHeaders(["Authorization": "Bearer ${ACCESS_TOKEN}"])
        service.handler.failure = { re ->     println "Unexpected failure: ${re.statusLine}"   }
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

        if (invalidLines) {
            report << "Invalid lines from source file:\n"
            invalidLines.each {k, v ->
                report << "\tLine ${k}: ${v}\n"
            }
        }

        int duplicates = 0
        if (scientificNames.any {k, v -> v.size() > 1 && k != null}) {
            report << "\n\nDuplicate scientific names (only the first record will be imported): \n"
            scientificNames.each { k, v ->
                if (v.size() > 1 && k) {
                    report << "\t${k}, on lines ${v}. Line ${v.first()} was imported.\n"
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

    static constructFullName(String name, String genus, String species, String infraspecificRank, String infraspecies, String author) {
        String fullName

        if (infraspecies && infraspecificRank) {
            if (infraspecies == species) {
                fullName = "${genus} ${species} ${author} ${infraspecificRank} ${infraspecies}"
            } else if (!species && infraspecies && name.contains(infraspecificRank) && name.contains(infraspecies)) {
                fullName = "${name} ${author}"
            } else {
                fullName = "${genus} ${species} ${infraspecificRank} ${infraspecies} ${author}"
            }
        } else {
            fullName = "${genus} ${species} ${author}"
        }

        fullName
    }

    static Map<String, String> loadAttributeTitles() {
        Map<String, String> attributeTitles = [:]
        def csv = parseCsv(new File("${DATA_DIR}/attributes_TMAG_Insects.csv").newReader(FILE_ENCODING))
        csv.each { line ->
            try {
                String propertyName = line.PROPERTY_NAME?.replaceAll("_", " ")?.trim()
                propertyName = StringUtils.capitalize(propertyName)
                attributeTitles << [(line.PROPERTY_ID): propertyName]
            } catch (e) {
                println "Failed to extract attribute titles from line [${line}]"
            }
        }
        println "Loaded ${attributeTitles.size()} attribute titles"
        attributeTitles
    }

    /**
     * Converts attribute CSV file to a Map of taxa ID, Attribute name and attribute text.
     * [ TAXA_ID: [ PROPERTY_NAME: [ VAL ] ] ]
     * @param attributeTitles
     * @return Map<Integer, Map<String, List<String>>>
     */
    static Map<String, Map<String, List<String>>> loadAttributes(Map<String, String> attributeTitles) {
        Map<String, Map<String, List<String>>> attributes = [:]
        int count = 0
        def csv = parseCsv(new File("${DATA_DIR}/attributes_TMAG_Insects.csv").newReader(FILE_ENCODING))
        csv.each { line ->
            if (count++ % 50 == 0) println "Processing attribute line ${count}..."
            try {
                String title = attributeTitles[line.PROPERTY_ID]

                attributes.get(line.TAXA_ID, [:]).get(title, []) << cleanupText(line.VAL)
            } catch (e) {
                println "${e.message} - ${line}"
            }
        }

        println "Loaded ${attributes.size()} taxa attributes"
        attributes
    }

    static Map<Integer, List<Map<String, String>>> loadImages() {
//        Map<Integer, Map<String, String>> images = [:]
//
//        def csv = parseCsv(new File("${DATA_DIR}/figures.csv").newReader(FILE_ENCODING))
//        csv.each { line ->
//            try {
//                images << [(line.ID as Integer): [fileName: line.FILE_NAME,
//                                                  title: cleanupText(line.CAPTION),
//                                                  creator: cleanupText(line.ILLUSTRATOR)]]
//            } catch (e) {
//                println "Failed to extract figure from line [${line}]"
//            }
//        }
//
//        Map<Integer, List<Map<String, String>>> taxaImages = [:]
//
//        csv = parseCsv(new File("${DATA_DIR}/taxa_figures.csv").newReader(FILE_ENCODING))
//        csv.each { line ->
//            try {
//                taxaImages.get(line.TAXON_ID as Integer, []) << images[line.FIGURE_ID as Integer]
//            } catch (e) {
//                println "Failed to extract figure from line [${line}]"
//            }
//        }
//
//        taxaImages
    }

    static Map<Integer, List<String>> loadMaps() {
//        Map<Integer, List<String>> maps = [:]
//
//        def csv = parseCsv(new File("${DATA_DIR}/distribution_maps.csv").newReader(FILE_ENCODING))
//        csv.each { line ->
//            try {
//                maps.get(line.TAXON_ID as Integer, []) << line.FILE_NAME
//            } catch (e) {
//                println "Failed to extract figure from line [${line}]"
//            }
//        }
//
//        maps
    }

    static cleanupText(str) {
        if (str) {
            str = unescapeHtml4(str)
        }

        str
    }

    static createImageFiles(List collectionImages) {
        String now = new SimpleDateFormat("yyyyMMdd").format(new Date())

        File dir = new File("foa_images_${now}")
        if (dir.exists()) {
            dir.list().each {
                new File(dir, it).delete()
            }
            dir.delete()
        }
        dir.mkdir()

        File images = new File(dir, "images.csv")
        images << "coreID,identifier,title\n"

        File export = new File(dir, "export.csv")
        export << "dcterms:type,basisOfRecord,catalogNumber,scientificName\n"

        File metadata = new File(dir, "meta.xml")

        int row = 1
        collectionImages.each {
            images.append("\"${row}\",\"${it.identifier}\",\"${it.title}\",\"${it.creator ?: ""}\"\n", "UTF-8")
            export.append("\"Image\",\"HumanObservation\",\"${row}\",\"${it.scientificName}\"\n", "UTF-8")
            row++
        }

        MarkupBuilder xml = new MarkupBuilder(new FileWriter(metadata))

        xml.mkp.xmlDeclaration(version: "1.0", encoding: "utf-8")

        xml.archive(xmlns: "http://rs.tdwg.org/dwc/text/") {
            core(encoding: "UTF-8",
                    linesTerminatedBy: "\\r\\n",
                    fieldsTerminatedBy: ",",
                    fieldsEnclosedBy: "",
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
                field(index: 2, term: "http://purl.org/dc/terms/creator")
            }
        }
    }
}