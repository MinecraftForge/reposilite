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
import org.apache.http.HttpResponse
import org.apache.http.HttpStatus
import org.apache.http.auth.AuthenticationException
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpPut
import org.apache.http.entity.StringEntity
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.impl.client.HttpClients
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.panda_lang.reposilite.ReposiliteContext
import org.panda_lang.reposilite.ReposiliteIntegrationTestSpecification
import org.panda_lang.reposilite.repository.IRepository.View
import org.panda_lang.utilities.commons.IOUtils
import org.panda_lang.utilities.commons.StringUtils

import static org.junit.jupiter.api.Assertions.*

@CompileStatic
@TestMethodOrder(MethodOrderer.MethodName.class)
class DeployEndpointTest extends ReposiliteIntegrationTestSpecification {
    {
        super.properties.putAll([
            'repositories': 'isspecial,main,private,zero',

            'repositories.isspecial.prefixes': 'special/',

            'repositories.private.hidden': 'true',
            'repositories.private.allowUploads': 'false',

            'repositories.zero.diskQuota': '0MB'
        ])
    }

    private final HttpClient client = HttpClients.createDefault()
    private String PASSWORD;

    @BeforeEach
    void configure() {
        PASSWORD = super.reposilite.getAuth().createRandomPassword() // Random password to be sure we don't hardcode anything in the codebase.
        super.reposilite.getAuth().createToken('/', 'root', 'rwm', PASSWORD)
        super.reposilite.getAuth().createToken('/', 'read', 'r', PASSWORD)
        super.reposilite.getAuth().createToken('/main/auth/test', 'authtest', 'rwm', PASSWORD)
        super.reposilite.getAuth().createToken('/private', 'private', 'rwm', PASSWORD)
    }

    @Test
    void 'should return 507 and out of disk space message' () throws Exception {
        shouldReturnErrorWithGivenMessage '/zero-releases/groupId/artifactId/file', 'root', PASSWORD, 'content', HttpStatus.SC_INSUFFICIENT_STORAGE, 'Out of disk space'
    }

    @Test
    void 'should return 401 and permissions message' () throws Exception {
        shouldReturnErrorWithGivenMessage '/main-releases/groupId/artifactId/file', 'read', PASSWORD, 'content', HttpStatus.SC_UNAUTHORIZED, 'Cannot deploy artifact without write permission'
    }

    @Test
    void 'should return 405 and artifact deployment is disabled message' () throws Exception {
        shouldReturnErrorWithGivenMessage '/private-releases/groupId/artifactId/file', 'private', PASSWORD, 'content', HttpStatus.SC_METHOD_NOT_ALLOWED, 'Artifact deployment is disabled'
    }

    @Test
    void 'should return 401 and invalid credentials message' () throws Exception {
        shouldReturnErrorWithGivenMessage '/main-releases/groupId/artifactId/file', 'authtest', 'invalid password', 'content', HttpStatus.SC_UNAUTHORIZED, 'Invalid authorization credentials'
    }

    @Test
    void 'should return 200 and success message for metadata files' () throws IOException, AuthenticationException {
        shouldReturn200AndSuccessMessage '/main-releases/auth/test/maven-metadata.xml', 'authtest', PASSWORD, StringUtils.EMPTY
    }

    @Test
    void 'should return 200 and success message'() throws IOException, AuthenticationException {
        shouldReturn200AndSuccessMessage '/main-releases/auth/test/pom.xml', 'authtest', PASSWORD, 'maven metadata content'
    }

    @Test
    void 'should return 405 and error message for non-snapshot artifact'() throws IOException, AuthenticationException {
        shouldReturnErrorWithGivenMessage '/main-snapshots/auth/test/pom.xml', 'authtest', PASSWORD, 'content', HttpStatus.SC_METHOD_NOT_ALLOWED, 'Cannot deploy non-SNAPSHOT artifact to snapshot repo'
    }


    @Test
    void 'should return 405 and can not contain message' () throws Exception {
        shouldReturnErrorWithGivenMessage '/isspecial-releases/not/special', 'root', PASSWORD, 'content', HttpStatus.SC_METHOD_NOT_ALLOWED, 'Repository isspecial can not contain: not/special'
    }

    @Test
    void 'should return 200 and success message for special'() throws IOException, AuthenticationException {
        shouldReturn200AndSuccessMessage '/isspecial-releases/special/file', 'root', PASSWORD, 'content'
    }

    @Test
    void 'should successfully publish to main-releases from releases'() throws Exception {
        def file = super.reposilite.getRepos().getRepo('main').getFile(View.RELEASES, 'test/upload/artifact')
        assertFalse file.exists()
        shouldReturn200AndSuccessMessage '/releases/test/upload/artifact', 'root', PASSWORD, 'content'
        assertTrue file.exists()
    }

    @Test
    void 'should successfully publish to ispecial from releases'() throws Exception {
        def file = super.reposilite.getRepos().getRepo('isspecial').getFile(View.RELEASES, 'special/upload/artifact')
        assertFalse file.exists()
        shouldReturn200AndSuccessMessage '/releases/special/upload/artifact', 'root', PASSWORD, 'content'
        assertTrue file.exists()
    }

    private void shouldReturn200AndSuccessMessage(String uri, String username, String password, String content) throws IOException, AuthenticationException {
        def deployResponse = put(uri, username, password, content)
        assertEquals HttpStatus.SC_OK, deployResponse.getStatusLine().getStatusCode()

        if (StringUtils.isEmpty(content)) {
            return
        }

        assertEquals HttpStatus.SC_OK, getAuthenticated(uri, username, password).getStatusCode()
        assertEquals content, getAuthenticated(uri, username, password).parseAsString()
    }

    private void shouldReturnErrorWithGivenMessage(String uri, String username, String password, String content, int status, String message) throws Exception {
        def response = put(uri, username, password, content)
        assertEquals status, response.getStatusLine().getStatusCode()

        def result = IOUtils.convertStreamToString(response.getEntity().getContent()).get()
        assertNotNull result
        assertTrue result.contains(message)
    }

    private HttpResponse put(String uri, String username, String password, String content) throws IOException, AuthenticationException {
        def httpPut = new HttpPut(url(uri).toString())
        httpPut.setEntity(new StringEntity(content))
        httpPut.addHeader(new BasicScheme().authenticate(new UsernamePasswordCredentials(username, password), httpPut, null))
        return client.execute(httpPut)
    }

}