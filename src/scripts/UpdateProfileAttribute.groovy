/*
 * Copyright (C) 2021 Atlas of Living Australia
 * All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 * 
 * Created by Temi on 10/6/21.
 */
@Grab('org.codehaus.groovy:groovy-json:2.4.17')
@Grab(group = 'org.codehaus.groovy.modules.http-builder', module = 'http-builder', version = '0.7.1')
@Grab('com.xlson.groovycsv:groovycsv:1.0')
@Grab('org.apache.commons:commons-lang3:3.3.2')

import groovy.json.JsonBuilder
import groovyx.net.http.RESTClient
import groovy.json.JsonSlurper
import org.apache.commons.lang3.StringUtils

import static com.xlson.groovycsv.CsvParser.parseCsv
import static groovyx.net.http.ContentType.JSON
import static org.apache.commons.lang3.StringEscapeUtils.unescapeHtml4

class UpdateProfileAttribute {
    static final String FILE_ENCODING = "utf-8"
    static final String APPEND = "append"
    static final String OVERWRITE = "overwrite"
    static boolean IS_DECORATE_TEXT = true
    static final String DEFAULT_ALA_AUTH_COOKIE_NAME = "ALA-Auth"

    static String OPUS_ID
    static String PROFILE_ID
    static String DATA_DIR
    static String REPORT_FILE
    static String PROFILE_URL
    static String PROFILE_SERVICE_PROFILE_URL
    static String PROFILE_SERVICE_REPORT_URL
    static String ATTRIBUTE_OPTION
    static String USER_ID
    static String USER_DISPLAY_NAME
    static String OUTPUT_FILE
    static String IMPORT_OUTPUT_FILE
    static String ACCESS_TOKEN

    static void main(args) {
        def cli = new CliBuilder(usage: "groovy UpdateProfileAttribute -f <datadir> -o opusId -p <profileServiceBaseUrl> -u <emailAddress> -r <reportfile> -a <accessToken>")
        cli.f(longOpt: "dir", "source data directory", required: true, args: 1)
        cli.o(longOpt: "opusId", "UUID of the FOA Opus", required: true, args: 1)
        cli.p(longOpt: "profileServiceBaseUrl", "Base URL of the profile service", required: true, args: 1)
        cli.u(longOpt: "userId", "User id of a ALA user importing script", required: true, args: 1)
        cli.d(longOpt: "displayName", "Display name of a ALA user importing script", required: true, args: 1)
        cli.i(longOpt: "importFile", "Email address of the ALA user importing script", required: true, args: 1)
        cli.r(longOpt: "reportFile", "File to write the results of the import to", required: false, args: 1)
        cli.a(longOpt: "accessToken", "Bearer token to access profiles service", required: true, args: 1)

        OptionAccessor opt = cli.parse(args)
        println opt: opt
        if(!opt) {
            cli.usage()
            return
        }

        PROFILE_URL = opt.p
        OPUS_ID = opt.o
        USER_ID = opt.u
        USER_DISPLAY_NAME = opt.d
        DATA_DIR = opt.f
        IMPORT_OUTPUT_FILE = opt.i
        OUTPUT_FILE = opt.r ?: "updatedAttributes.json"
        ATTRIBUTE_OPTION = OVERWRITE
        ACCESS_TOKEN = opt.a ?: ""

        Map<Integer, String> attributeTitles = loadAttributeTitles()
        Map<Integer, Map<String, List<String>>> taxaAttributes = loadAttributes(attributeTitles)
        Map existingProfile = getExistingProfilesFromReport(IMPORT_OUTPUT_FILE)
        Map result = [total: 0, numberOfSuccess: 0, numberOfFailure: 0, numberOfSkipped: 0, success: [], failure: [], skipped: []]

        File output = new File(OUTPUT_FILE);
        if (output.exists()) {
            output.delete()
            output.createNewFile()
        }

        def csv = parseCsv(new File("${DATA_DIR}/taxon_TMAG_insects.csv").newReader(FILE_ENCODING))
        csv.each { taxon ->
            if (existingProfile[taxon.NAME.toLowerCase()]) {
                PROFILE_ID = URLEncoder.encode(taxon.NAME, FILE_ENCODING)
                PROFILE_ID = PROFILE_ID.replace('+', '%20')
                PROFILE_SERVICE_PROFILE_URL = "$PROFILE_URL/opus/$OPUS_ID/profile/$PROFILE_ID?latest=true"
                println PROFILE_SERVICE_PROFILE_URL

                def client = new RESTClient(PROFILE_SERVICE_PROFILE_URL)
                client.setHeaders(["Authorization": "Bearer ${ACCESS_TOKEN}"])
                def profile
                try {
                    def resp = client.get([:])
                    profile = resp.getData()
                    println new JsonBuilder(profile).toPrettyString()
                } catch (groovyx.net.http.HttpResponseException e) {
                    println "statusCode: " + e.statusCode
                    println "response data: " + e.response.data
                    return
                }

                String taxonId = taxon.TAXA_ID
                Map<String, List<String>> attrs = taxaAttributes.get(taxonId)
                attrs.each { k, v ->
                    def ATTRIBUTE_URL
                    def name = k
                    def attribute = findAttribute(profile, name)
                    println name
                    def body = [
                            title          : name,
                            text           : getAttributeText(attribute, v?.join('')),
                            significantEdit: false,
                            profileId      : profile.uuid,
                            uuid           : "",
                            userId         : USER_ID,
                            userDisplayName: USER_DISPLAY_NAME
                    ]

                    if (attribute) {
                        ATTRIBUTE_URL = "$PROFILE_URL/opus/$OPUS_ID/profile/$PROFILE_ID/attribute/$attribute.uuid"
                        body.uuid = attribute.uuid
                    } else {
                        ATTRIBUTE_URL = "$PROFILE_URL/opus/$OPUS_ID/profile/$PROFILE_ID/attribute"
                        body.userId = USER_ID
                        body.userDisplayName = USER_DISPLAY_NAME
                    }
                    println "updating $ATTRIBUTE_URL"

                    try {
                        if (body.text != attribute?.text) {
                            client = new RESTClient(ATTRIBUTE_URL)
                            client.setHeaders(["Authorization": "Bearer ${ACCESS_TOKEN}"])
                            def respData = client.post(body: body, requestContentType: JSON)
                            if (respData.success) {
                                println "Successfully updated $PROFILE_ID $name"
                                result.success.add([PROFILE_ID, name])
                            }
                        } else {
                            result.skipped.add([PROFILE_ID, name])
                            println("Content identical. Not updating $PROFILE_ID $name")
                        }
                    }
                    catch (Exception e) {
                        result.failure.add([PROFILE_ID, name])
                        println("Failed to draft $name")
                        e.printStackTrace()
                    }
                }
            }
        }

        result.numberOfSuccess = result.success.size()
        result.numberOfFailure = result.failure.size()
        result.numberOfSkipped = result.skipped.size()
        result.total = result.numberOfSuccess + result.numberOfFailure + result.numberOfSkipped

        output << (new JsonBuilder(result)).toString()
    }

    static findAttribute(Map profile, String name) {
        profile.attributes?.find {
            it.title.toLowerCase() == name.toLowerCase()
        }
    }

    static getAttributeText(Map attribute, String additionalContent) {
        if (attribute && attribute.plainText) {
            switch (ATTRIBUTE_OPTION) {
                case APPEND:
                    if (!attribute.text?.contains(additionalContent)) {
                        return attribute.text + "<br>--------------------<br>"+ decorateText(additionalContent)
                    }

                    return attribute.text
                    break
                case OVERWRITE:
                    return decorateText(additionalContent)
                    break
            }
        } else {
            return decorateText(additionalContent)
        }
    }

    static decorateText(text) {
        if (IS_DECORATE_TEXT) {
            if (!text?.toLowerCase().startsWith("<p>")) {
                return "<p>" + text + "</p>"
            }
        }

        return text
    }

    static Map<Integer, String> loadAttributeTitles() {
        Map<Integer, String> attributeTitles = [:]
        def csv = parseCsv(new File("${DATA_DIR}/attributes_TMAG_Insects.csv").newReader(FILE_ENCODING))
        csv.each { line ->
            try {
                String propertyName = line.PROPERTY_NAME?.replaceAll("_", " ")?.trim()
                propertyName = StringUtils.capitalize(propertyName)
                attributeTitles << [(line.PROPERTY_ID): propertyName]
            } catch (e) {
                println "Failed to extract attribute titles from line [${line}]"
                e.printStackTrace()
            }
        }

        attributeTitles
    }

    /**
     * Converts attribute CSV file to a Map of taxa ID, Attribute name and attribute text.
     * [ TAXA_ID: [ PROPERTY_NAME: [ VAL ] ] ]
     * @param attributeTitles
     * @return Map<Integer, Map<String, List<String> >>
     */
    static Map<Integer, Map<String, List<String>>> loadAttributes(Map<Integer, String> attributeTitles) {
        Map<Integer, Map<String, List<String>>> attributes = [:]
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

        attributes
    }

    static Map getExistingProfilesFromReport(String fileName) {
        File report = new File(fileName)
        Map content = new JsonSlurper().parseText(report.text)
        Map index = [:]
        content.profiles.each { name, metadata ->
            if (metadata.errors && metadata.errors.join("").contains("already exists")) {
                name = name.trim().toLowerCase()
                index[name] = true
            }
        }

        index
    }

    static cleanupText(str) {
        if (str) {
            str = unescapeHtml4(str)
        }

        str
    }
}