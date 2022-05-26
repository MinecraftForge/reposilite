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

package org.panda_lang.reposilite.auth

import groovy.transform.CompileStatic
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.panda_lang.reposilite.ReposiliteIntegrationTestSpecification
import org.panda_lang.reposilite.error.ErrorDto
import org.panda_lang.reposilite.repository.FileListDto

import static org.apache.http.HttpStatus.*
import static org.junit.jupiter.api.Assertions.*

@CompileStatic
@TestMethodOrder(MethodOrderer.MethodName.class)
final class AuthEndpointTest extends ReposiliteIntegrationTestSpecification {
    @Test
    void 'should return 401 without credentials' () throws IOException {
        def response = getRequest('/api/auth')
        assertEquals SC_UNAUTHORIZED, response.statusCode
        assertTrue response.headers.containsKey('www-authenticate')
    }

    @Test
    void 'should return 401 for invalid credentials' () throws IOException {
        def password = reposilite.auth.createRandomPassword()
        reposilite.auth.createToken('/', 'admin', 'rwm', password)
        def response = getAuthenticated('/api/auth', 'admin', 'not the password')
        assertEquals SC_UNAUTHORIZED, response.statusCode
        assertTrue response.headers.containsKey('www-authenticate')
    }

    @Test
    void 'should return 200 and auth dto' () throws IOException {
        def password = reposilite.auth.createRandomPassword()
        reposilite.auth.createToken('/', 'admin', 'rwm', password)
        def response = shouldReturn200AndAuth('/api/auth', 'admin', password)
        assertEquals 'rwm', response.permissions
        assertEquals '/', response.path
        assertEquals(['main'], response.repositories)
    }

    private static ErrorDto shouldReturn404AndError(String uri, String username, String password) {
        return JSON_MAPPER.readValue(shouldReturnData(SC_NOT_FOUND, uri, username, password), ErrorDto.class)
    }
    private static AuthDto shouldReturn200AndAuth(String uri, String username, String password) {
        return JSON_MAPPER.readValue(shouldReturnData(SC_OK, uri, username, password), AuthDto.class)
    }
}