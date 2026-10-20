@Grab('org.codehaus.groovy.modules.http-builder:http-builder:0.7.1')
@Grab('org.apache.commons:commons-lang3:3.3.2')

import groovyx.net.http.ContentType
import groovyx.net.http.RESTClient
import org.apache.commons.lang3.StringUtils
import org.apache.http.impl.client.LaxRedirectStrategy

import java.text.SimpleDateFormat

class PERTHImport {

	static File report

	static void main(args) {
		def cli = new CliBuilder(usage: "groovy PERTHImport -o <opusId> -f <perthServiceBaseUrl> -u <floraBaseUser> -p <floraBasePass> -s <profileServiceBaseUrl> [-r <reportFile>]")
		cli.o(longOpt: "opusId",
				"UUID of the PERTH Flora Opus",
				required: true, args: 1)
		cli.f(longOpt: "perthServiceBaseUrl",
				"Base URL of the PERTH web service (in FloraBase)",
				required: true, args: 1)
		cli.u(longOpt: "floraBaseUser",
				"Username of the FloraBase account to use to access web services",
				required: true, args: 1)
		cli.p(longOpt: "floraBasePass",
				"Password of the FloraBase account to use to access web services",
				required: true, args: 1)
		cli.s(longOpt: "profileServiceBaseUrl",
				"Base URL of the profile service",
				required: true, args: 1)
		cli.r(longOpt: "reportFile",
				"File to write the results of the import to",
				required: false, args: 1)

		OptionAccessor opt = cli.parse(args)
		if (!opt) {
			return
		}

		String OPUS_ID = opt.o
		String FLORABASE_USER = opt.u
		String FLORABASE_PASS = opt.p
		String PERTH_BASE_URL = StringUtils.removeEnd(opt.f, "/");
		String PROFILE_SERVICE_BASE_URL = StringUtils.removeEnd(opt.s, "/");
		String REPORT_FILE = opt.r ?: "report.txt"

		report = new File(REPORT_FILE)
		if (report.exists()) {
			report.delete()
			report.createNewFile()
		}

		Object data = requestData(PERTH_BASE_URL, FLORABASE_USER, FLORABASE_PASS)
		if (data) {
			Map opus = [
			        opusId: OPUS_ID,
					profiles: data
			]

			postOpus(opus, PROFILE_SERVICE_BASE_URL)
		}
	}

	static Object requestData(String url, String username, String password) {
		println(url)
		String PERTH_PROFILE_SERVICE = "/ws/profile/list"
		String PERTH_LOGIN_SERVICE = "${url}/login"

		RESTClient service = new RESTClient(url)
		service.client.setRedirectStrategy(new LaxRedirectStrategy())
		def response = service.post(
				requestContentType: ContentType.JSON,
				path: PERTH_LOGIN_SERVICE,
				query: [
						username: username,
						password: password,
						p: PERTH_PROFILE_SERVICE,
						process: 1
				])

		if (response.status == 200) {
			return response.data.profiles
		}
		else {
			println "Import failed with HTTP ${response.status}"
			return null
		}
	}

	static void postOpus(Map data, String baseUrl) {
		def importUrl = "${baseUrl}/import/profile"
		println(importUrl)
		RESTClient service = new RESTClient(importUrl)
		def response = service.post(body: data, requestContentType: ContentType.JSON)
		def importId = response.data.id

		def reportUrl = "${baseUrl}/import/${importId}/report"
		println "Import report will be available at ${reportUrl}"

		int sleepTime = 60 * 1000;
		println "${new SimpleDateFormat("HH:mm:ss.S").format(new Date())} Waiting for import to complete..."
		Thread.sleep(sleepTime)

		service = new RESTClient(reportUrl)
		response = service.get([:]).data

		while (response.status == "IN_PROGRESS") {
			println "${new SimpleDateFormat("HH:mm:ss.S").format(new Date())} Waiting for import to complete..."
			Thread.sleep(sleepTime)

			response = service.get([:]).data
		}

		// Dump some report output based on the json data available in response.report.
		// Cribbed mostly from NSWImport.groovy, but should perhaps be generalised.
		int count = data.profiles.size()
		int success = 0
		int failed = 0
		int warnings = 0
		report << "\n\nImport results: \n"
		report << "\nStarted: ${response.report.started}"
		report << "\nFinished: ${response.report.finished}"

		response.report.profiles.each { k, v ->
			if (v.status.startsWith("success")) {
				success++
			} else if (v.status.startsWith("warning")) {
				warnings++
			} else {
				failed++
			}
		}

		report << "\n\nImported ${success} of ${count} profiles with ${failed} errors and ${warnings} warnings\n\n"

		response.report.profiles.each { k, v ->
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
}
