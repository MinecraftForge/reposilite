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

import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.panda_lang.reposilite.ReposiliteIntegrationTestSpecification
import org.panda_lang.reposilite.auth.Permission
import org.panda_lang.reposilite.auth.Token
import org.panda_lang.reposilite.error.ErrorDto
import org.panda_lang.utilities.commons.collection.Pair

import static org.junit.jupiter.api.Assertions.*
import static org.panda_lang.reposilite.auth.Permission.*
import static org.apache.http.HttpStatus.*

@CompileStatic
@TestMethodOrder(MethodOrderer.MethodName.class)
class LookupApiEndpointSecureTest extends ReposiliteIntegrationTestSpecification {
    {
        super.properties.putAll([
            'repositories':                           'main-releases,main-snapshots,private',
            'repositories.main-releases.browseable':  'false',
            'repositories.main-snapshots.browseable': 'false',
            'repositories.private.hidden':            'true',
            'repositories.private.browseable':        'false'
        ])
    }

    @Test
    void 'root should return 401 listing repositories' () {
        shouldReturn401('/api')
    }

    @Test
    void 'root should return list of all authenticated repositories' () {
        def password = makeToken('/', 'username', Permission.READ)
        def resp = shouldReturn200AndFileList('/api', 'username', password)
        assertEquals 3, resp.files.size()
        assertEquals(['main-releases', 'main-snapshots', 'private'], getNames(resp))
    }

    @Test
    void 'explicit should return 200 and latest release file' () {
        def password = makeToken('/', 'username', Permission.READ)
        def result = shouldReturn200AndFileDetails('/api/main-releases/reposilite/test/latest', 'username', password)
        assertEquals FileDetailsDto.DIRECTORY, result.type
        assertEquals '1.0.1', result.name
    }

    @Test
    void 'explicit should return 200 and latest snapshot file' () {
        def password = makeToken('/', 'username', Permission.READ)
        def result = shouldReturn200AndFileDetails('/api/main-snapshots/reposilite/test/latest', 'username', password)
        assertEquals FileDetailsDto.DIRECTORY, result.type
        assertEquals '1.0.1-SNAPSHOT', result.name
    }

    @Test
    void 'explicit should return 401 getting latest file' () {
        shouldReturn401('/api/main-releases/reposilite/test/latest')
    }

    @Test
    void 'explicit should return 404 if requested file is not found' () {
        def password = makeToken('/main-releases/', 'username', Permission.READ)
        def resp = shouldReturn404AndError('/api/main-releases/reposilite/test/unknown', 'username', password)
        assertEquals 'File not found', resp.message
    }

    @Test
    void 'ecplicit should return 401 if requested file is not found without auth' () {
        shouldReturn401('/api/main-releases/reposilite/test/unknown')
    }

    @Test
    void 'explicit should return 401 if requested file exists without auth' () {
        shouldReturn401('/api/main-releases/reposilite/test/1.0.0/test-1.0.0.jar')
    }

    @Test
    void 'explicit should return 200 and file details' () {
        def password = makeToken('/main-releases/', 'username', Permission.READ)
        def result = shouldReturn200AndFileDetails('/api/main-releases/reposilite/test/1.0.0/test-1.0.0.jar', 'username', password)
        assertEquals FileDetailsDto.FILE, result.type
        assertEquals 'test-1.0.0.jar', result.name
    }

    @Test
    void 'ecplicit should return 200 and directory list' () {
        def password = makeToken('/main-releases/', 'username', Permission.READ)
        def result = shouldReturn200AndFileList('/api/main-releases/reposilite/test', 'username', password)
        assertEquals 4, result.files.size()
        assertEquals(['1.0.0', '1.0.1', 'maven-metadata.xml', 'maven-metadata.xml.md5'], getNames(result))
    }

    @Test
    void 'explicit should return 401 if requested directory exists without auth' () {
        shouldReturn401('/api/main-releases/reposilite/test')
    }

    private String makeToken(String path, String username, Permission... perms) {
        String password = super.reposilite.getAuth().createRandomPassword();
        StringBuilder buf = new StringBuilder()
        for (Permission perm : perms)
            buf.append(perm.name)
        super.reposilite.auth.createToken(path, username, buf.toString(), password)
        return password
    }

    private static FileListDto shouldReturn200AndFileList(String uri, String username, String password) {
        return JSON_MAPPER.readValue(shouldReturnData(SC_OK, uri, username, password), FileListDto.class)
    }
    private static FileDetailsDto shouldReturn200AndFileDetails(String uri, String username, String password) {
        return JSON_MAPPER.readValue(shouldReturnData(SC_OK, uri, username, password), FileDetailsDto.class)
    }
    private static ErrorDto shouldReturn404AndError(String uri, String username, String password) {
        return JSON_MAPPER.readValue(shouldReturnData(SC_NOT_FOUND, uri, username, password), ErrorDto.class)
    }

    private void shouldReturn401(String uri) {
        shouldReturn(SC_UNAUTHORIZED, uri)
    }

    private static List<String> getNames(FileListDto data) {
        return data.getFiles().stream().map{ it.name }.toList()
    }
}