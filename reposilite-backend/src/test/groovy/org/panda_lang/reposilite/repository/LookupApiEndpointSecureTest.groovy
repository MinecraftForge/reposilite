/*
 * Copyright (c) 2020 Dzikoysk
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.panda_lang.reposilite.repository


import groovy.transform.CompileStatic
import java.util.stream.Collectors
import net.dzikoysk.cdn.CdnFactory
import net.dzikoysk.cdn.model.Configuration
import org.apache.http.HttpStatus
import org.junit.jupiter.api.Test
import org.panda_lang.reposilite.ReposiliteIntegrationTestSpecification
import org.panda_lang.reposilite.auth.Permission
import org.panda_lang.reposilite.auth.Token
import org.panda_lang.utilities.commons.collection.Pair

import static org.junit.jupiter.api.Assertions.*
import static org.panda_lang.reposilite.auth.Permission.*

import com.google.api.client.http.HttpResponse

@CompileStatic
class LookupApiEndpointSecureTest extends ReposiliteIntegrationTestSpecification {

    {
        super.properties.put('reposilite.repositories', 'releases,snapshots,.private')
        super.properties.put('reposilite.apiRequiresAuth', 'true')
    }

    @Test
    void 'should return 401 listing repositories' () {
        shouldReturn401('/api')
    }

    @Test
    void 'should return list of all authenticated repositories' () {
        def token = makeToken('*/', Permission.READ)
        def repositories = shouldReturn200AndJsonResponse('/api', token)

        def files = repositories.getSection('files').get()
        assertEquals 3, files.size()
        assertEquals 'releases', files.getSection(0).get().getString('name').get()
        assertEquals 'snapshots', files.getSection(1).get().getString('name').get()
        assertEquals 'private', files.getSection(2).get().getString('name').get()
    }

    @Test
    void 'should return 200 and latest file' () {
        def token = makeToken('/releases', Permission.READ)
        def result = shouldReturn200AndJsonResponse('/api/org/panda-lang/reposilite-test/latest', token)
        assertEquals 'directory', result.getString('type').get()
        assertEquals '1.0.1-SNAPSHOT', result.getString('name').get()
    }

    @Test
    void 'should return 401 getting latest file' () {
        shouldReturn401('/api/org/panda-lang/reposilite-test/latest')
    }

    @Test
    void 'should return 404 if requested file is not found' () {
        def token = makeToken('/releases', Permission.READ)
        def response = getAuthenticated('/api/org/panda-lang/reposilite-test/unknown', token.value.alias, token.key)
        assertEquals HttpStatus.SC_NOT_FOUND, response.getStatusCode()
        assertTrue response.parseAsString().contains('File not found')
    }

    @Test
    void 'should return 401 if requested file is not found' () {
        shouldReturn401('/api/org/panda-lang/reposilite-test/unknown')
    }

    @Test
    void 'should return 200 and file dto' () {
        def token = makeToken('/releases', Permission.READ)
        def result = shouldReturn200AndJsonResponse('/api/org/panda-lang/reposilite-test/1.0.0/reposilite-test-1.0.0.jar', token)
        assertEquals 'file', result.getString('type').get()
        assertEquals 'reposilite-test-1.0.0.jar', result.getString('name').get()
    }

    @Test
    void 'should return 401 if requested file exists' () {
        shouldReturn401('/api/org/panda-lang/reposilite-test/1.0.0/reposilite-test-1.0.0.jar')
    }

    @Test
    void 'should return 200 and directory dto' () {
        def token = makeToken('/releases', Permission.READ)
        def result = shouldReturn200AndJsonResponse('/api/org/panda-lang/reposilite-test', token)
        def files = result.getSection('files').get()
        assertEquals '1.0.1-SNAPSHOT', files.getSection(0).get().getString('name').get()
    }

    @Test
    void 'should return 401 if requested directory exists' () {
        shouldReturn401('/api/org/panda-lang/reposilite-test')
    }

    private Pair<String, Token> makeToken(String path, Permission... perms) {
        StringBuilder buf = new StringBuilder()
        for (Permission perm : perms)
            buf.append(perm.name)
        return super.reposilite.tokenService.createToken(path, 'secret', buf.toString())
    }

    private static HttpResponse shouldReturn200(String uri, Pair<String, Token> token) {
        def response = getRequest(uri)
        assertEquals HttpStatus.SC_OK, response.statusCode
        return response;
    }

    private Configuration shouldReturn200AndJsonResponse(String uri, Pair<String, Token> token) {
        def response = getAuthenticated(uri, token.value.alias, token.key)
        assertEquals HttpStatus.SC_OK, response.statusCode
        return CdnFactory.createJson().load(response.parseAsString())
    }

    private void shouldReturn401(String uri) {
        def response = getRequest(uri);
        assertEquals HttpStatus.SC_UNAUTHORIZED, response.statusCode;
    }

}