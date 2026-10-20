@Grab('com.xlson.groovycsv:groovycsv:1.0')

@GrabResolver(name = 'ala', root = 'http://nexus.ala.org.au/content/groups/public/')
@Grab('au.org.ala:ala-name-matching:2.1')
@Grab('com.google.guava:guava:19.0')
@GrabExclude('org.apache.lucene:lucene-queries')

import au.org.ala.names.model.LinnaeanRankClassification
import au.org.ala.names.model.NameSearchResult
import au.org.ala.names.search.ALANameSearcher
import au.org.ala.names.search.HomonymException
import au.org.ala.names.search.ParentSynonymChildException
import com.google.common.net.UrlEscapers
import groovy.json.JsonSlurper
import org.apache.http.client.utils.URIBuilder

import java.util.concurrent.atomic.AtomicInteger

import static com.xlson.groovycsv.CsvParser.parseCsv
import static groovyx.gpars.GParsPool.withPool
import static org.apache.commons.httpclient.util.URIUtil.encodeWithinQuery
import static org.apache.commons.lang3.StringEscapeUtils.unescapeHtml4

class NameMatch {
    static ALANameSearcher currentALANames = new ALANameSearcher("/data/lucene/namematching13")

    static main(String[] args) {
        def cli = new CliBuilder(usage: "groovy NameCheck -a -n <name>")
        cli.a(longOpt: "matchAll", "match all NSL names", required: false, args: 0)
        cli.n(longOpt: "name", "match this name", required: false, args: 1)
        cli.l(longOpt: "nsl", "match this name against the NSL as well as ALA", required: false, args: 0)
        cli.u(longOpt: "umatchedNSL", "run the ALA unmatched names against the NSL", required: false, args: 0)

        OptionAccessor opt = cli.parse(args)

        if (!opt) {
            cli.usage()
            return
        }

        String name = opt.name
        boolean matchAll = opt.a as boolean
        boolean matchNameToNSL = opt.l as boolean
        boolean umatchedNSL = opt.u as boolean

        if (matchAll) {
            matchAllNslNames()
        } else if (umatchedNSL) {
            runUnmatchedALANamesAgainstNSL()
        } else {
            matchALAName(name, true)
            if (matchNameToNSL) {
                matchNSLName(name)
            }
        }
    }

    static runUnmatchedALANamesAgainstNSL() {
        long start = System.currentTimeMillis()

        def csv = parseCsv(new FileReader("/data/nslnames/unmatched.csv"))

        List nslMatch = Collections.synchronizedList([])
        List nslNoMatch = Collections.synchronizedList([])

        AtomicInteger i = new AtomicInteger(0)
//        withPool(1) {
            csv.each { fields ->
                i.incrementAndGet()

                String name = fields.NSLName

                Map match = matchNSLName(name)
                if (match) {
                    nslMatch << match.fullName
                } else {
                    nslNoMatch << name
                }
//            }
        }

        long finish = System.currentTimeMillis()
        println "Processed ${i} names in ${finish - start} millis - Matches: ${nslMatch.size()}; No match: ${nslNoMatch.size()}"
    }

    static matchAllNslNames() {
        long start = System.currentTimeMillis()

        File nslSimpleNames = new File("/data/nslnames/nslsimplename-2015-12-18-4237.csv")

        println "Reading CSV file ${nslSimpleNames.getAbsolutePath()}"
        def csv = parseCsv(new FileReader(nslSimpleNames))

        String targetName = "full_name_html"

        List matches = Collections.synchronizedList([])
        List exactMatches = Collections.synchronizedList([])
        List unmatched = Collections.synchronizedList([])
        List nameSameAuthorDifferent = Collections.synchronizedList([])
        List nameDifferent = Collections.synchronizedList([])
        List failures = Collections.synchronizedList([])

        println "Processing..."
        AtomicInteger i = new AtomicInteger(0)
        AtomicInteger hybrids = new AtomicInteger(0)
        AtomicInteger noNsl = new AtomicInteger(0)

        withPool(10) {
            csv.eachParallel { fields ->
                if (i.incrementAndGet() % 100 == 0) {
                    println "Processing ${i}..."
                }

                String nslName = removeHtml(fields[targetName])
                String nslSimpleName = removeHtml(fields.simple_name_html)
                String cleanedNsl = cleanupText(nslName)

                if (fields.hybrid == 't') {
                    hybrids.incrementAndGet()
                }
                if (nslName && cleanedNsl) {

                    List<String> outputs = [nslName]

                    Map matchedName = matchALAName(nslName)
                    outputs << matchedName.fullName


                    String line = outputs.join(",") + "\n"
                    if (!matchedName) {
                        unmatched << line
                    } else if (matchedName && !matchedName.exception) {
                        matches << line

                        String cleanedMatch = matchedName.clean?.fullName

                        if (cleanedNsl == cleanedMatch) {
                            exactMatches << line
                        } else {
                            if (matchedName.clean?.fullName?.startsWith(nslSimpleName)) {
                                nameSameAuthorDifferent << line
                            } else {
                                nameDifferent << line
                            }
                        }
                    } else {
                        failures << line
                    }


                } else {
                    noNsl.incrementAndGet()
                }
            }
        }

        writeToFile("unmatched", unmatched)
        writeToFile("nameDifferent", nameDifferent)
        writeToFile("nameSameAuthorDifferent", nameSameAuthorDifferent)

        println "hybrids: ${hybrids}"
        long finish = System.currentTimeMillis()
        println """Processed ${i.intValue()} names in ${finish - start} millis:
                   Failures: ${failures.size()};
                   Total Matches: ${matches.size()};
                        Exact Matches: ${exactMatches.size()};
                        Name mismatch: ${nameDifferent.size()};
                        Author mismatch: ${nameSameAuthorDifferent.size()};
                   No match: ${unmatched.size()}
                   Empty ${targetName}: ${noNsl.intValue()}"""
    }

    static writeToFile(String name, List rows) {
        File file = new File("/data/nslnames/${name}.csv")
        if (file.exists()) {
            file.delete()
            file.createNewFile()
        }
        file << "NSLName, ALAName\n"

        rows.each {
            file << it
        }
    }

    static String cleanupText(String text) {
        text ? text.replaceAll(/[\.,"'\(\)\-]/, "").replaceAll(" +", " ").trim() : ""
    }

    static Map matchALAName(String name, boolean print = false) {
        Map match = [:]

        if (!name) {
            return match
        }

        try {
            LinnaeanRankClassification rankClassification = new LinnaeanRankClassification()
            rankClassification.setScientificName(name)
//            rankClassification.setKingdom("Plantae")

            NameSearchResult result = currentALANames.searchForAcceptedRecordDefaultHandling(rankClassification, true, true)

            if (print) {
                println "ALA Match: ${result}\n"
            }

            if (result && result.getRankClassification()) {
                match.clean = [:]
                match.scientificName = result.getRankClassification().getScientificName()
                match.clean.scientificName = cleanupText(result.getRankClassification().getScientificName())
                match.author = result.getRankClassification().getAuthorship()
                match.clean.author = cleanupText(result.getRankClassification().getAuthorship())
                match.fullName = "${result.getRankClassification().getScientificName()} ${result.getRankClassification().getAuthorship() ?: ""}".trim()
                match.clean.fullName = cleanupText("${result.getRankClassification().getScientificName()} ${result.getRankClassification().getAuthorship() ?: ""}".trim())
            } else {
                List<NameSearchResult> matches = currentALANames.searchForRecords(name, null, rankClassification, 100, true, true)
                println matches
                if (matches?.size() > 1) {
                    match.alternatives = matches.collect { "${it.rank.rank}: ${it?.rankClassification?.scientificName}" }.join(", ")
                }
            }
        } catch (HomonymException e) {
            e.results?.each { println it }
        } catch (ParentSynonymChildException e) {
            e.results?.each { println it }
        } catch (Exception e) {
            match.exception = e.message
            e.printStackTrace()
        }

        match
    }

    static Map matchNSLName(String name) {
        Map match = [:]
        try {
            String url = "https://biodiversity.org.au/nsl/services/api/name/acceptable-name.json?name=${UrlEscapers.urlFormParameterEscaper().escape(name)}"
            def json = new JsonSlurper().parse(url.toURL(), 'UTF-8')
            println "NSL Match: ${json}\n"
            if (json.count == 1) {
                match.scientificName = json.names[0].simpleName
                match.scientificNameHtml = json.names[0].simpleNameHtml
                match.fullName = json.names[0].fullName
                match.fullNameHtml = json.names[0].fullNameHtml
                match.nameAuthor = match.fullNameHtml
                String linkUrl = json.names[0]._links.permalink.link
                match.nslIdentifier = linkUrl.substring(linkUrl.lastIndexOf("/") + 1)
                match.nslProtologue = json.names[0].primaryInstance[0]?.citationHtml
            } else {
                println("${json.count} NSL matches for ${name}")
            }
        } catch (Exception e) {
            match.exception = e.message
            println e.message
        }

        match
    }
    static removeHtml(str) {
        if (str) {
            str = unescapeHtml4(str)
            // preserve line breaks as new lines, remove all other html
            str = str.replaceAll(/<p\/?>|<br\/?>/, "\n").replaceAll(/<.+?>/, "").replaceAll(" +", " ").trim()
        }
        return str
    }

}