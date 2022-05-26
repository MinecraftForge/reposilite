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
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.panda_lang.reposilite.ReposiliteIntegrationTestSpecification
import org.panda_lang.reposilite.auth.Token
import org.panda_lang.reposilite.error.ErrorDto
import org.panda_lang.utilities.commons.collection.Pair


import static org.apache.http.HttpStatus.*
import static org.junit.jupiter.api.Assertions.*

@CompileStatic
@TestMethodOrder(MethodOrderer.MethodName.class)
class LookupApiEndpointTest extends ReposiliteIntegrationTestSpecification {
    {
        super.properties.putAll([
            'repositories':                'main,private',
            'repositories.private.hidden': 'true'
        ])
    }

    @Test
    void 'root should return list of public repositories' () {
        def resp = shouldReturn200AndFileList('/api')
        assertNotNull resp
        assertEquals 2, resp.files.size()
        assertEquals(['main-releases', 'main-snapshots'], getNames(resp))
    }

    @Test
    void 'root should return list of all authenticated repositories' () {
        def token = super.reposilite.getAuth().createToken('/private', 'secret', 'rwm', 'password')
        def resp = shouldReturn200AndFileList('/api', token.alias, 'password')
        assertEquals 4, resp.files.size()
        assertEquals(['main-releases', 'main-snapshots', 'private-releases', 'private-snapshots'], getNames(resp))
    }

    @Test
    void 'explicit should return 200 and latest release file' () {
        def result = shouldReturn200AndFileDetails('/api/main-releases/reposilite/test/latest')
        assertEquals FileDetailsDto.DIRECTORY, result.type
        assertEquals '1.0.1', result.name
    }

    @Test
    void 'explicit should return 200 and latest snapshot file' () {
        def result = shouldReturn200AndFileDetails('/api/main-snapshots/reposilite/test/latest')
        assertEquals FileDetailsDto.DIRECTORY, result.type
        assertEquals '1.0.1-SNAPSHOT', result.name
    }

    @Test
    void 'explicit should return 404 if requested file is not found' () {
        def resp = shouldReturn404AndError('/api/main-releases/reposilite/test/unknown')
        assertEquals 'File not found', resp.message
    }

    @Test
    void 'explicit should return 200 and file dto' () {
        def result = shouldReturn200AndFileDetails('/api/main-releases/reposilite/test/1.0.0/test-1.0.0.jar')
        assertEquals FileDetailsDto.FILE, result.type
        assertEquals 'test-1.0.0.jar', result.name
    }

    @Test
    void 'explicit should return 200 and directory list' () {
        def result = shouldReturn200AndFileList('/api/main-releases/reposilite/test')
        assertEquals 4, result.files.size()
        assertEquals(['1.0.0', '1.0.1', 'maven-metadata.xml', 'maven-metadata.xml.md5'], getNames(result))
    }

    @Test
    void 'releases should return not implemented for non-explicit request' () {
        def resp = shouldReturn501AndError('/api/releases/reposilite/test')
        assertEquals 'Can not browse API in merged views. Must specify a repository and view', resp.message
    }

    @Test
    void 'all should return 404 for root repository, or repository not found' () {
        def resp = shouldReturn404AndError('/api/reposilite/test')
        assertEquals 'Can not find repo at: reposilite/test', resp.message
    }

    private static FileListDto shouldReturn200AndFileList(String uri) {
        return JSON_MAPPER.readValue(shouldReturnData(SC_OK, uri), FileListDto.class)
    }
    private static FileListDto shouldReturn200AndFileList(String uri, String username, String password) {
        return JSON_MAPPER.readValue(shouldReturnData(SC_OK, uri, username, password), FileListDto.class)
    }
    private static FileDetailsDto shouldReturn200AndFileDetails(String uri) {
        return JSON_MAPPER.readValue(shouldReturnData(SC_OK, uri), FileDetailsDto.class)
    }
    private static FileDetailsDto shouldReturn200AndFileDetails(String uri, String username, String password) {
        return JSON_MAPPER.readValue(shouldReturnData(SC_OK, uri, username, password), FileDetailsDto.class)
    }
    private static ErrorDto shouldReturn404AndError(String uri) {
        return JSON_MAPPER.readValue(shouldReturnData(SC_NOT_FOUND, uri), ErrorDto.class)
    }
    private static ErrorDto shouldReturn501AndError(String uri) {
        return JSON_MAPPER.readValue(shouldReturnData(SC_NOT_IMPLEMENTED, uri), ErrorDto.class)
    }
    private static List<String> getNames(FileListDto data) {
        return data.getFiles().stream().map{ it.name }.toList()
    }
}