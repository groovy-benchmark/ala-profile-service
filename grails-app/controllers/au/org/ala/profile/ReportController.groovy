package au.org.ala.profile
import grails.converters.JSON

class ReportController extends BaseController {
    def reportService

    def profileLookup() {
        if (!params.opusId) {
            badRequest "opusId is a required parameter"
        } else {
            Opus opus = getOpus()

            if (!opus) {
                notFound "No opus found for ${params.opusId}"
            } else {
                // a JSON body carries nested match documents unambiguously; plain query parameters remain supported
                Map filters = request.JSON ? (request.JSON as Map) : params
                Map report = reportService.lookupProfiles(opus.uuid, filters)

                render report as JSON
            }
        }
    }

    def profileLookupListed() {
        if (!params.opusId) {
            badRequest "opusId is a required parameter"
        } else {
            Opus opus = getOpus()

            if (!opus) {
                notFound "No opus found for ${params.opusId}"
            } else {
                Map report = reportService.lookupProfilesListed(opus.uuid, params)

                render report as JSON
            }
        }
    }

    def profileExport() {
        if (!params.opusId) {
            badRequest "opusId is a required parameter. You can also optionally supply max and offset."
        } else {
            Opus opus = getOpus()

            if (!opus) {
                notFound "No opus found for ${params.opusId}"
            } else {
                int max = params.max ? params.max as int : 25
                int startFrom = params.offset ? params.offset as int : 0

                Map report = reportService.exportProfiles(opus.uuid, max, startFrom)

                render report as JSON
            }
        }
    }

    def profilePage() {
        if (!params.opusId) {
            badRequest "opusId is a required parameter. You can also optionally supply max and offset."
        } else {
            Opus opus = getOpus()

            if (!opus) {
                notFound "No opus found for ${params.opusId}"
            } else {
                int max = params.max ? params.max as int : 25
                int startFrom = params.offset ? params.offset as int : 0

                Map report = reportService.pageProfiles(opus.uuid, max, startFrom)

                render report as JSON
            }
        }
    }

    def profilePaced() {
        if (!params.opusId) {
            badRequest "opusId is a required parameter. You can also optionally supply batches and pauseMs."
        } else {
            Opus opus = getOpus()

            if (!opus) {
                notFound "No opus found for ${params.opusId}"
            } else {
                int batches = params.batches ? params.batches as int : 1
                long pauseMs = params.pauseMs ? params.pauseMs as long : 100

                Map report = reportService.pacedProfileExport(opus.uuid, batches, pauseMs)

                render report as JSON
            }
        }
    }

    def profilePacedPreview() {
        if (!params.opusId) {
            badRequest "opusId is a required parameter. You can also optionally supply batches and pauseMs."
        } else {
            Opus opus = getOpus()

            if (!opus) {
                notFound "No opus found for ${params.opusId}"
            } else {
                int batches = params.batches ? params.batches as int : 1
                long pauseMs = params.pauseMs ? params.pauseMs as long : 100

                Map report = reportService.pacedProfileExportPreview(opus.uuid, batches, pauseMs)

                render report as JSON
            }
        }
    }

    def draftProfiles() {
        if (!params.opusId) {
            badRequest "opusId is a required parameter"
        } else {
            Opus opus = getOpus()

            if (!opus) {
                notFound "No opus found for ${params.opusId}"
            } else {
                Map report = reportService.draftProfiles(opus.uuid)

                render report as JSON
            }
        }
    }

    def archivedProfiles() {
        if (!params.opusId) {
            badRequest "opusId is a required parameter"
        } else {
            Opus opus = getOpus()

            if (!opus) {
                notFound "No opus found for ${params.opusId}"
            } else {
                Map report = reportService.archivedProfiles(opus.uuid)

                render report as JSON
            }
        }
    }

    def mismatchedNames() {
        if (!params.opusId) {
            badRequest "opusId is a required parameter"
        } else {
            Opus opus = getOpus()

            if (!opus) {
                notFound "No opus found for ${params.opusId}"
            } else {
                int max = params.max && params.max != "null" ? params.max as int : -1
                int startFrom = params.offset ? params.offset as int : 0

                Map report = reportService.mismatchedNames(opus.uuid, max, startFrom, false)

                render report as JSON
            }
        }
    }

    def recentChanges() {
        if (!params.opusId || !params.from || !params.to) {
            badRequest "opusId, from and to date are required parameters"
        } else {
            Opus opus = getOpus()

            int max = params.max && params.max != "null" ? params.max as int : -1
            int startFrom = params.offset ? params.offset as int : 0
            boolean countOnly = params.countOnly?.toBoolean()

            try {
                Date from = new Date(params.from);
                Date to = new Date(params.to);
                if (!opus) {
                    notFound "No opus found for ${params.opusId}"
                } else {
                    Map report = reportService.recentUpdates(
                            opus.uuid, from, to, max, startFrom, countOnly);
                    render report as JSON
                }
            } catch (Exception e) {
                badRequest "Provided date is not in the correct format"
            }
        }
    }

    def recentComments() {
        if (!params.opusId || !params.from || !params.to) {
            badRequest "opusId, from and to date are required parameters"
        } else {
            Opus opus = getOpus()

            int max = params.max && params.max != "null" ? params.max as int : -1
            int startFrom = params.offset ? params.offset as int : 0
            boolean countOnly = params.countOnly?.toBoolean()

            try {
                Date from = new Date(params.from)
                Date to = new Date(params.to)
                if (!opus) {
                    notFound "No opus found for ${params.opusId}"
                } else {
                    Map report = reportService.recentComments(opus, from, to, max, startFrom, countOnly)
                    render report as JSON
                }
            } catch (Exception e) {
                log.error("Recent comments report failed for ${opus.shortName}, ${params.from}, ${params.to}", e)
                badRequest "Provided date is not in the correct format"
            }
        }
    }

    def nslMatchingReport() {
        if (!params.opusId) {
            badRequest "opusId is a required parameter. It can be a comma-separated list to produce a report for multiple collections"
        } else {
            boolean mismatchOnly = params.mismatchOnly?.toBoolean()

            List opusIds = params.opusId.split(",")

            Map nslMatches = [:].withDefault { [] }

            opusIds.each {
                Opus opus = Opus.findByUuid(it)

                List<Profile> profiles = Profile.findAllByOpus(opus)

                profiles.sort { it.scientificName }

                profiles.each {
                    nslMatches[it.scientificName] << [it.nslNameIdentifier, it.nslNomenclatureIdentifier]
                }
            }

            StringBuilder csv = new StringBuilder()

            List header = ["Profile"]
            opusIds.each {
                header << "${it}[nameId,nomenId]"
            }
            csv.append("${header.join(",")}\n")

            nslMatches.each { k, v ->
                boolean mismatch = false

                opusIds.eachWithIndex { def entry, int i ->
                    int next = i < opusIds.size() - 1 ? i + 1 : i
                    mismatch |= v && v[i] && v[next] && (v[i][0] != v[next][0] || v[i][1] != v[next][1])
                }

                if (!mismatchOnly || (mismatchOnly && mismatch)) {
                    csv.append(k)
                    csv.append(",")
                    csv.append(v.join(","))
                    if (!mismatchOnly && mismatch) {
                        csv.append(",*****")
                    }
                    csv.append("\n")
                }

            }

            response.setContentType("text/plain")
            render csv
        }
    }
}
