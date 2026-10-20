@Grab(group = 'org.codehaus.groovy.modules.http-builder', module = 'http-builder', version = '0.7')
@Grab('org.apache.commons:commons-lang3:3.3.2')
@Grab('com.xlson.groovycsv:groovycsv:1.0')

import groovyx.net.http.RESTClient

import java.text.SimpleDateFormat

import static com.xlson.groovycsv.CsvParser.parseCsv

import static org.apache.commons.lang3.StringEscapeUtils.unescapeHtml4

import static groovyx.net.http.ContentType.JSON

class NTImport {

    static void main(args) {
        def cli = new CliBuilder(usage: "groovy NTImport -d <datadir> -o opusId -p <profileServiceBaseUrl>")
        cli.d(longOpt: "dir", "source data dir", required: true, args: 1)
        cli.o(longOpt: "opusId", "UUID of the NSW Flora Opus", required: true, args: 1)
        cli.p(longOpt: "profileServiceBaseUrl", "Base URL of the profile service", required: true, args: 1)

        OptionAccessor opt = cli.parse(args)

        if (!opt) {
            cli.usage()
            return
        }

        String OPUS_ID = opt.o
        String DATA_FILE = opt.d
        String REPORT_FILE = "report.txt"
        String PROFILE_SERVICE_IMPORT_URL = "${opt.p}/import/profile"
        String PROFILE_SERVICE_REPORT_URL = "${opt.p}/"

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

        List<File> files = [new File("${DATA_FILE}/vol1.csv"), new File("${DATA_FILE}/vol2.csv")]

        List<String> attributeColumns = ["Description",
                                         "Diagnostic Characters",
                                         "Similar Taxa",
                                         "Habitat",
                                         "Phenology Flowering",
                                         "Phenology Fruiting",
                                         "Scope Geography",
                                         "Distribution International",
                                         "Distribution International",
                                         "Flora Information Notes",
                                         "Specimens",
                                         "Chromosome",
                                         "Licence",
                                         "Taxonomic Literature",
                                         "Etymology",
                                         "Seed Collection",
                                         "Seed Treatment",
                                         "Seed Propagation General",
                                         "Seed Propagation General",
                                         "Cutting Collection",
                                         "Cutting Treatment",
                                         "Cutting Propagation General",
                                         "Division Collection",
                                         "Division Propagation General",
                                         "Grafting Information"
        ]

        files.each {
            println "Processing file ${it.getName()}..."

            def csv = parseCsv(new FileReader(it))
            csv.each { fields ->
                if (count++ % 50 == 0) println "Processing line ${count}..."

                String taxonName = fields["Taxon Name"]
                if (taxonName.split(" ").length > 1) {
                    taxonName = taxonName.substring(taxonName.indexOf(" ")).trim()
                }


                List attributes = []

                attributeColumns.each {
                    String value = clean(fields[it])
                    if (value && value != "-") {
                        attributes << [title: it, text: value, stripHtml: false]
                    }
                }

                if (!scientificNames.containsKey(taxonName)) {
                    Map profile = [scientificName              : taxonName,
                                   nslNomenclatureMatchStrategy: "APC_OR_LATEST",
                                   attributes                  : attributes,
                                   fullName                    : taxonName]

                    profiles << profile
                }

                scientificNames.get(taxonName, []) << count
            }
        }

        Map opus = [
                opusId  : OPUS_ID,
                profiles: profiles
        ]

        println "Importing to ${PROFILE_SERVICE_IMPORT_URL}..."
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
            invalidLines.each { k, v ->
                report << "\tLine ${k}: ${v}\n"
            }
        }

        if (scientificNames.any { k, v -> v.size() > 1 && k != null }) {
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

}
