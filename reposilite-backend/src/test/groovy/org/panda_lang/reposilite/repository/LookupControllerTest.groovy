/*
 * Copyright (c) 2020 Dzikoysk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.panda_lang.reposilite.repository

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.google.api.client.http.HttpResponse
import groovy.transform.CompileStatic
import org.apache.http.HttpStatus
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.io.TempDir
import org.panda_lang.reposilite.ReposiliteIntegrationTestSpecification
import org.panda_lang.utilities.commons.FileUtils

import static org.apache.http.HttpStatus.*
import static org.junit.jupiter.api.Assertions.*

@CompileStatic
@TestMethodOrder(MethodOrderer.MethodName.class)
class LookupControllerTest extends ReposiliteIntegrationTestSpecification {
    {
        super.properties.putAll([
            'repositories':                   'filtered,main-releases,main-snapshots,private',
            'repositories.filtered.prefixes': 'special/',
            'repositories.private.hidden':    'true',
        ])
    }

    // Generic
    @Test
    void 'generic should return 203 and frontend with unsupported request message' () {
        assertResponseWithMessage SC_NON_AUTHORITATIVE_INFORMATION, '/', 'Unsupported request'
    }

    // ===========================================================================
    // Explicitly defining repo name
    // ===========================================================================
    @Test
    void 'explicit should return 302 and new location for directory access without slash' () {
        def path = '/main-releases/reposilite/test'
        def response = getRequest(path, false)
        assertEquals SC_MOVED_TEMPORARILY, response.statusCode
        assertEquals path + '/', response.headers.getLocation()
    }

    @Test
    void 'explicit should return 203 and frontend with directory access message' () {
        assertResponseWithMessage SC_OK, '/main-releases/reposilite/test/', 'Directory access'
    }

    @Test
    void 'explicit should return 404 for missing group level meta' () {
        assertResponseWithMessage SC_NOT_FOUND, '/main-releases/reposilite/maven-metadata.xml', 'File not found'
    }

    @Test
    void 'explicit should return 404 for missing artifact level meta' () {
        assertResponseWithMessage SC_NOT_FOUND, '/main-releases/reposilite/missing/maven-metadata.xml', 'File not found'
    }

    @Test
    void 'explicit should return 404 for missing version level meta' () {
        assertResponseWithMessage SC_NOT_FOUND, '/main-releases/reposilite/missing/1.0.0/maven-metadata.xml', 'File not found'
    }

    @Test
    void 'explicit should return artifact level meta xml' () {
        def meta = shouldReturn200AndXml '/main-releases/reposilite/test/maven-metadata.xml'
        assertIterableEquals(['1.0.0', '1.0.1'], getVersions(meta))
    }

    @Test
    void 'explicit should return 404 for missing file that is not a valid maven path' () {
        assertResponseWithMessage SC_NOT_FOUND, '/main-releases/group/artifact/missing', 'Invalid artifact path'
    }

    @Test
    void 'explicit should return 404 for missing file with proxies disabled message' () {
        assertResponseWithMessage SC_NOT_FOUND, '/main-releases/groupId/artifactId/version/file', 'File not found'
    }

    @Test
    void 'explicit should return 200 and requested file' () {
        def response = shouldReturn200AndData '/main-releases/reposilite/missing/1.0.0/missing-1.0.0-known.txt'
        assertEquals 'known artifact contents', response
    }

    @Test
    void 'explicit should return 200 and head requested file' () {
        def response = REQUEST_FACTORY
            .buildHeadRequest(url('/main-releases/reposilite/test/1.0.0/test-1.0.0.jar'))
            .execute()

        assertEquals SC_OK, response.statusCode
        assertTrue response.parseAsString().isEmpty()
    }

    @Test
    void 'explicit should return 401 for private with unauthorized message' () {
        assertResponseWithMessage SC_UNAUTHORIZED, '/private/reposilite/private/1.0.0/private-1.0.0.jar', 'Unauthorized request'
    }

    @Test
    void 'explicit should return 200 for private with auth' () {
        def password = reposilite.auth.createRandomPassword()
        reposilite.auth.createToken('/', 'admin', 'rwm', password)
        def content = shouldReturn200AndData '/private/reposilite/private/1.0.0/private-1.0.0-known.txt', 'admin', password
        assertEquals 'known artifact contents', content
    }

    @Test
    void 'explicit should return 404 and impossible artifact for non-matching path' () {
        assertResponseWithMessage SC_NOT_FOUND, '/filtered/not/special/', 'Impossible artifact'
    }
    // ===========================================================================
    // releases view
    // ===========================================================================
    @Test
    void 'releases should return 404 for existing meta, outside of the current view' () {
        assertResponseWithMessage SC_NOT_FOUND, '/releases/reposilite/test/1.0.0-SNAPSHOT/maven-metadata.xml', 'File not found'
    }

    @Test
    void 'releases should return 404 for existing artifact, outside of the current view' () {
        assertResponseWithMessage SC_NOT_FOUND, '/releases/reposilite/missingg/1.0.1/missing-1.0.1.jar', 'File not found'
    }

    @Test
    void 'releases should return artifact level meta xml with limited scope' () {
        def meta = shouldReturn200AndXml '/releases/reposilite/test/maven-metadata.xml'
        assertIterableEquals(['1.0.0', '1.0.1'], getVersions(meta))
    }

    @Test
    void 'releases should return 200 and requested file' () {
        def response = shouldReturn200AndData('/releases/reposilite/missing/1.0.0/missing-1.0.0-known.txt')
        assertEquals 'known artifact contents', response
    }

    @Test
    void 'releases should return 200 and head requested file' () {
        def response = REQUEST_FACTORY
            .buildHeadRequest(url('/releases/reposilite/test/1.0.0/test-1.0.0.jar'))
            .execute()

        assertEquals SC_OK, response.statusCode
        assertTrue response.parseAsString().isEmpty()
    }

    @Test
    void 'releases should return 200 and known file from filtered repo' () {
        def response = shouldReturn200AndData '/releases/special/test/1.0.0/test-1.0.0-known.txt'
        assertEquals 'known artifact contents', response
    }

    @Test
    void 'releases should return 404 for private without auth' () {
        assertResponseWithMessage SC_NOT_FOUND, '/releases/reposilite/private/1.0.0/private-1.0.0.jar', 'File not found'
    }

    @Test
    void 'releases should return 200 for private with auth' () {
        def password = reposilite.auth.createRandomPassword()
        reposilite.auth.createToken('/', 'admin', 'rwm', password)
        def content = shouldReturn200AndData('/releases/reposilite/private/1.0.0/private-1.0.0-known.txt', 'admin', password)
        assertEquals 'known artifact contents', content
    }
    // ===========================================================================
    // snapshots view
    // ===========================================================================
    @Test
    void 'snapshots should return 404 for existing artifact, outside of the current view' () {
        assertResponseWithMessage SC_NOT_FOUND, '/snapshots/reposilite/missingg/1.0.0/missing-1.0.0.jar', 'File not found'
    }

    @Test
    void 'snapshots should return artifact level meta xml with limited scope' () {
        def meta = shouldReturn200AndXml '/snapshots/reposilite/test/maven-metadata.xml'
        assertIterableEquals(['1.0.0-SNAPSHOT', '1.0.1-SNAPSHOT'], getVersions(meta))
    }

    @Test
    void 'snapshots should return 200 and requested file' () {
        def response = shouldReturn200AndData('/snapshots/reposilite/missing/1.0.1/missing-1.0.1-known.txt')
        assertEquals 'known artifact contents', response
    }

    @Test
    void 'snapshots should return 200 and head requested file' () {
        def response = REQUEST_FACTORY
            .buildHeadRequest(url('/snapshots/reposilite/missing/1.0.1/missing-1.0.1-known.txt'))
            .execute()

        assertEquals SC_OK, response.statusCode
        assertTrue response.parseAsString().isEmpty()
    }

    @Test
    void 'snapshots should return 200 and known file from filtered repo' () {
        def response = shouldReturn200AndData '/snapshots/special/test/1.0.0/test-1.0.0-known.txt'
        assertEquals 'known artifact contents', response
    }
    //============================================================================================================
    // 'all' view
    //============================================================================================================
    @Test
    void 'all should return 302 and new location for directory access without slash' () {
        def path = '/reposilite/test'
        def response = getRequest(path, false)
        assertEquals SC_MOVED_TEMPORARILY, response.statusCode
        assertEquals path + '/', response.headers.getLocation()
    }

    @Test
    void 'all should return 203 and frontend with directory access message' () {
        assertResponseWithMessage SC_OK, '/reposilite/test/', 'Directory access'
    }
    @Test
    void 'all should return 404 for missing group level meta' () {
        assertResponseWithMessage SC_NOT_FOUND, '/reposilite/maven-metadata.xml', 'File not found'
    }

    @Test
    void 'all should return 404 for missing artifact level meta' () {
        assertResponseWithMessage SC_NOT_FOUND, '/reposilite/missing/maven-metadata.xml', 'File not found'
    }

    @Test
    void 'all should return 404 for missing version level meta' () {
        assertResponseWithMessage SC_NOT_FOUND, '/reposilite/missing/1.0.0/maven-metadata.xml', 'File not found'
    }

    @Test
    void 'all should return 404 for missing file that is not a valid maven path' () {
        assertResponseWithMessage SC_NOT_FOUND, '/snapshots/group/artifact/missing', 'Invalid artifact path'
    }

    @Test
    void 'all should return 404 for missing file with proxies disabled message' () {
        assertResponseWithMessage SC_NOT_FOUND, '/snapshots/groupId/artifactId/version/file', 'File not found'
    }

    @Test
    void 'all should return artifact level meta xml all versions' () {
        def meta = shouldReturn200AndXml '/reposilite/test/maven-metadata.xml'
        assertIterableEquals(['1.0.0', '1.0.0-SNAPSHOT', '1.0.1', '1.0.1-SNAPSHOT'], getVersions(meta))
    }

    @Test
    void 'all should return 200 and requested file from main-releases' () {
        def response = shouldReturn200AndData('/reposilite/missing/1.0.0/missing-1.0.0-known.txt')
        assertEquals 'known artifact contents', response
    }
    @Test
    void 'all should return 200 and requested file from main-snapshots' () {
        def response = shouldReturn200AndData('/reposilite/missing/1.0.1/missing-1.0.1-known.txt')
        assertEquals 'known artifact contents', response
    }

    @Test
    void 'all should return 200 and requested file from filtered' () {
        def response = shouldReturn200AndData('/special/test/1.0.0/test-1.0.0-known.txt')
        assertEquals 'known artifact contents', response
    }

    @Test
    void 'all should return 404 for existing file in private without error' () {
        assertResponseWithMessage SC_NOT_FOUND, '/reposilite/private/1.0.0/private-1.0.0.jar', 'File not found'
    }

    @Test
    void 'all should return 200 and head requested file' () {
        def response = REQUEST_FACTORY
            .buildHeadRequest(url('/reposilite/test/1.0.0/test-1.0.0.jar'))
            .execute()

        assertEquals SC_OK, response.statusCode
        assertTrue response.parseAsString().isEmpty()
    }
    //============================================================================================================


    // HELPERS
    private static void assertResponseWithMessage(int status, String url, String message) {
        assertMessage shouldReturnData(status, url), message
    }
    private static void assertResponseWithMessage(int status, String url, String message, String username, String password) {
        assertMessage shouldReturnData(status, url, username, password), message
    }

    private static void assertMessage(String content, String message) {
        def prefix = "REPOSILITE_MESSAGE = '"
        int idx = content.indexOf(prefix)
        assertTrue idx != -1
        int idx2 = content.indexOf("'", idx + prefix.length())
        assertTrue idx2 != -1
        def actual = content.substring(idx + prefix.length(), idx2)
        assertEquals message, actual
    }

    private static String shouldReturn200AndData(String url) {
        return shouldReturnData(SC_OK, url)
    }
    private static String shouldReturn200AndData(String url, String username, String password) {
        return shouldReturnData(SC_OK, url, username, password)
    }

    private static LinkedHashMap<String, ?> shouldReturn200AndXml(String url) {
        def data = shouldReturnData(SC_OK, url)
        return (LinkedHashMap<String, ?>)new XmlMapper().readValue(data, Object.class)
    }

    private static List<String> getVersions(LinkedHashMap<String, ?> meta) {
        assertNotNull meta
        def versioning = (Map<String, ?>)meta.get('versioning')
        assertNotNull versioning
        def versions = (Map<String, ?>)versioning.get('versions')
        assertNotNull versions
        def list = (List<String>)versions.get('version')
        assertNotNull list
        // TODO: Make sure this is sorted by the merger?
        Collections.sort(list);
        return list
    }

}