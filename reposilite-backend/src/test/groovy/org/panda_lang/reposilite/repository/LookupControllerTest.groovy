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

import com.google.api.client.http.HttpResponse
import groovy.transform.CompileStatic
import org.apache.http.HttpStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.panda_lang.reposilite.ReposiliteIntegrationTestSpecification
import org.panda_lang.utilities.commons.FileUtils

import static org.apache.http.HttpStatus.*
import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertTrue

@CompileStatic
class LookupControllerTest extends ReposiliteIntegrationTestSpecification {
    {
        super.properties.put('reposilite.repositories', 'releases,snapshots,private')
        super.properties.put('reposilite.repositories.private.hidden', 'true')
    }

    @Test
    void 'should return 203 and frontend with directory access message' () {
        assertResponseWithMessage SC_NON_AUTHORITATIVE_INFORMATION, '/releases/org/panda-lang', 'Directory access'
    }

    @Test
    void 'should return 203 and frontend with unsupported request message' () {
        assertResponseWithMessage SC_NON_AUTHORITATIVE_INFORMATION, '/', 'Unsupported request'
    }

    @Test
    void 'should return 404 for missing group level meta' () {
        assertResponseWithMessage SC_NOT_FOUND, '/gav/1.0.0-SNAPSHOT/maven-metadata.xml', 'Artifact maven-metadata.xml not found'
    }

    @Test
    void 'should return 404 for missing file that is not a valid maven path' () {
        assertResponseWithMessage SC_NOT_FOUND, '/gav/1.0.0-SNAPSHOT/missing', 'Invalid artifact path'
    }

    @Test
    void 'should return 203 and frontend with missing artifact identifier' () {
        assertResponseWithMessage SC_NON_AUTHORITATIVE_INFORMATION, '/releases/groupId', 'Missing artifact identifier'
    }

    @Test
    void 'should return 404 and frontend with proxied repositories are not enabled message' () {
        assertResponseWithMessage SC_NOT_FOUND, '/releases/groupId/artifactId/version/file', 'Artifact file not found'
    }

    @Test
    void 'should return 200 and metadata file' () {
        def response = shouldReturn200AndData('/releases/org/panda-lang/reposilite-test/maven-metadata.xml')
        assertTrue response.contains('<version>1.0.0</version>')
    }

    @Test
    void 'should return 200 and latest version' () {
        def response = shouldReturn200AndData('/releases/org/panda-lang/reposilite-test/latest')
        assertEquals '1.0.1-SNAPSHOT', response
    }

    @Test
    void 'should return 404 and frontend with latest version not found' () {
        assertResponseWithMessage(SC_NOT_FOUND, '/releases/org/panda-lang/reposilite-test/reposilite-test-1.0.0.jar/latest', 'Latest version not found')
    }

    /* Why would he do this? Maven should request the correct file, we shouldn't need to re-write.
    @Test
    void 'should return 200 and resolved snapshot file' () {
        def response = shouldReturn200AndData('/releases/org/panda-lang/reposilite-test/1.0.0-SNAPSHOT/reposilite-test-1.0.0-SNAPSHOT.pom')
        assertTrue response.contains('<version>1.0.0-SNAPSHOT</version>')
    }
    */

    @Test
    void 'should return 404 and artifact not found message' () {
        assertResponseWithMessage(SC_NOT_FOUND, '/releases/org/panda-lang/reposilite-test/1.0.0/artifactId', 'Artifact artifactId not found')
    }

    @Test
    void 'should return 200 and requested file' () {
        def response = shouldReturn200AndData('/releases/org/panda-lang/reposilite-test/1.0.0/reposilite-test-1.0.0.pom')
        assertTrue response.contains("<version>1.0.0</version>")
    }

    @Test
    void 'should return 200 and head requested file' () {
        def response = REQUEST_FACTORY
                .buildHeadRequest(url('/releases/org/panda-lang/reposilite-test/1.0.0/reposilite-test-1.0.0.pom'))
                .execute()

        assertEquals HttpStatus.SC_OK, response.getStatusCode()
        assertTrue response.parseAsString().isEmpty()
    }

    @Test
    void 'should return 401 with unauthorized message' () {
        assertResponseWithMessage SC_UNAUTHORIZED, '/private/a/b', 'Unauthorized request'
    }

    private static void assertResponseWithMessage(int status, String url, String message) {
        def content = shouldReturnData(status, url)
        assertTrue(content.contains("REPOSILITE_MESSAGE = '" + message + "'"));
    }

    private static String shouldReturn200AndData(String url) {
        return shouldReturnData(SC_OK, url)
    }

}