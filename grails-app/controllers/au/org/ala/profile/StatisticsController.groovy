package au.org.ala.profile

import au.org.ala.profile.security.Role
import grails.converters.JSON

import java.text.SimpleDateFormat

class StatisticsController extends BaseController {

    AnalyticsService analyticsService
    StatisticsService statisticsService
    ReportService reportService

    def index() {
        if (!params.opusId) {
            badRequest "opusId is a required parameter"
        } else {
            Opus opus = getOpus()

            if (!opus) {
                notFound "No opus found for ${params.opusId}"
            } else {
                def statistics = []

                int profileCount = Profile.createCriteria().count {
                    eq("opus", opus)
                }
                statistics.add([
                        id     : 'profileCount',
                        name   : 'Profiles in collection',
                        value  : profileCount,
                        tooltip: "Total number of profiles in this collection"
                ])

                Map mismatchedNames = reportService.mismatchedNames(opus.uuid, 0, 1, true)
                statistics.add([
                        id     : 'mismatchedNames',
                        name   : 'Mismatched Names',
                        value  : mismatchedNames.recordCount,
                        tooltip: "Number of profiles where the name does not exist in the National Species List (NSL)"
                ])

                float mismatchedNamesAsPercent = profileCount == 0 ? 0 : (mismatchedNames.recordCount / profileCount * 100.0)
                statistics.add([
                        id     : 'mismatchedNamesPercent',
                        name   : 'Mismatched Names %',
                        value  : "${mismatchedNamesAsPercent.round(2)}%",
                        tooltip: "Percentage of profiles where the name does not exist in the National Species List (NSL)"
                ])

                int editorCount = opus.authorities.count {
                    it.role == Role.ROLE_PROFILE_AUTHOR  || it.role == Role.ROLE_PROFILE_EDITOR
                }
                statistics.add([
                        id     : 'editorCount',
                        name   : 'Collection authors and editors',
                        value  : editorCount,
                        tooltip: "Number of people who can edit profiles in this collection"
                ])

                Calendar from = Calendar.getInstance()
                from.set(Calendar.DAY_OF_MONTH, 1)
                from.set(Calendar.HOUR_OF_DAY, 0)
                from.set(Calendar.MINUTE, 0)
                from.set(Calendar.SECOND, 0)
                from.set(Calendar.MILLISECOND, 0)

                Date to = new Date()
                Map updatesThisMonth = reportService.recentUpdates(opus.uuid, from.getTime(), to, 0, 1, true)
                statistics.add([
                        id     : 'updatesThisMonth',
                        name   : 'Updates This Month',
                        value  : updatesThisMonth.recordCount,
                        tooltip: "Number of profiles which have been updated since ${new SimpleDateFormat("dd/MM/yyyy").format(from.getTime())}"
                ])

                Profile profile = statisticsService.lastEditedProfile(opus)
                statistics.add([
                        id     : 'lastUpdatedProfile',
                        name   : 'Most Recent Update',
                        value  : "${profile?.scientificName ?: ""}",
                        tooltip: "Updated by ${profile?.lastUpdatedBy} on ${new SimpleDateFormat("dd/MM/yyyy").format(profile?.lastUpdated)}"
                ])

                if (analyticsService.enabled()) {
                    try {
                        Map analyticsOpusData = analyticsService.analyticsByOpus(opus)
                        if (analyticsOpusData.mostViewedProfile.pagePath) {
                            statistics.add([
                                    id     : 'visitorCount',
                                    name   : 'Visitors',
                                    value  : "${analyticsOpusData.totalVisitorCount?.users ?: 0}",
                                    caveat : "This statistic may be up to 2 days old",
                                    tooltip: "The number of unique visitors to the collection. There have been ${analyticsOpusData.monthlyVisitorCount?.users ?: 0} visitors this month."
                            ])

                            statistics.add([
                                    id     : 'downloads',
                                    name   : 'Downloads',
                                    value  : "${analyticsOpusData.totalDownloadCount?.pageviews ?: 0}",
                                    caveat : "This statistic may be up to 2 days old",
                                    tooltip: "The number of PDFs downloaded from this collection. There have been ${analyticsOpusData.monthlyDownloadCount?.pageviews ?: 0} downloads this month."
                            ])

                            String[] mostViewedPath = analyticsOpusData.mostViewedProfile?.pagePath?.split("/")
                            String profileName = mostViewedPath?.size() >= 4 ? mostViewedPath[4] : null
                            statistics.add([
                                    id     : 'mostViewedProfile',
                                    name   : 'Most Viewed Profile',
                                    value  : "${profileName ?: ''} (${analyticsOpusData.mostViewedProfile?.pageviews ?: 0} times)",
                                    caveat : "This statistic may be up to 2 days old",
                                    tooltip: "The profile that has been viewed the most in this collection."
                            ])
                        }
                    } catch (Exception e) {
                        log.error("Failed to query Google Analytics", e)
                    }
                }

                render statistics as JSON
            }
        }
    }
}
