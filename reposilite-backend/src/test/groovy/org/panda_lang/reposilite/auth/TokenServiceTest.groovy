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

package org.panda_lang.reposilite.auth

import groovy.transform.CompileStatic
import java.util.concurrent.Executors
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.panda_lang.reposilite.repository.IRepositoryManager

import static org.junit.jupiter.api.Assertions.*

import org.junit.jupiter.api.BeforeAll

@CompileStatic
class TokenServiceTest {
    @TempDir
    protected static File WORKING_DIRECTORY
    protected static IRepositoryManager REPOSITORY_MANAGER
    protected IAuthManager AUTH_MANAGER

    @BeforeAll
    static void prepare () {
        REPOSITORY_MANAGER = IRepositoryManager.builder()
            .dir(WORKING_DIRECTORY)
            .quota('0')
            .executor(Executors.newSingleThreadExecutor())
            .scheduled(Executors.newSingleThreadScheduledExecutor())
            .repo("releases", {})
            .repo("snapshots", {})
            .build()
    }

    @BeforeEach
    void before() {
        AUTH_MANAGER = IAuthManager.builder()
            .dir(WORKING_DIRECTORY)
            .repo(REPOSITORY_MANAGER)
            .build()
    }

    @Test
    void 'should save and load' () {
        def temp = IAuthManager.builder()
            .dir(WORKING_DIRECTORY)
            .repo(REPOSITORY_MANAGER)
            .build()
        temp.createToken('path', 'alias', 'rw', 'password')
        temp.save()
        AUTH_MANAGER.load() // uses the same file

        assertNotNull AUTH_MANAGER.getToken('alias')
        assertEquals 'path', AUTH_MANAGER.getToken('alias').getPath()
    }

    @Test
    void 'should create token' () {
        def token = AUTH_MANAGER.createToken('path', 'alias', 'rw', 'password')
        assertNotNull AUTH_MANAGER.getToken('alias')

        assertEquals 'path', token.getPath()
        assertEquals 'alias', token.getAlias()
        assertTrue TokenService.B_CRYPT_TOKENS_ENCODER.matches('password', token.getToken())

        def customToken = AUTH_MANAGER.createToken('custom_path', 'custom_alias', 'rw', 'secret')
        assertNotNull AUTH_MANAGER.getToken('custom_alias')
        assertEquals 'custom_path', customToken.getPath()
        assertEquals 'custom_alias', customToken.getAlias()
        assertTrue TokenService.B_CRYPT_TOKENS_ENCODER.matches('secret', customToken.getToken())
    }

    @Test
    void 'should add token' () {
        def token = AUTH_MANAGER.createToken('path', 'alias', 'secret', 'rw')
        assertEquals token, AUTH_MANAGER.getToken('alias')
    }

    @Test
    void 'should delete token' () {
        assertNull AUTH_MANAGER.deleteToken('random')

        AUTH_MANAGER.createToken('path', 'alias', 'token', 'password')
        def token = AUTH_MANAGER.deleteToken('alias')
        assertNotNull token
        assertEquals 'alias', token.getAlias()

        assertNull AUTH_MANAGER.getToken('alias')
    }

    @Test
    void 'should get token' () {
        assertNull AUTH_MANAGER.getToken('random')
        AUTH_MANAGER.createToken('path', 'alias', 'rw', 'password')
        assertNotNull AUTH_MANAGER.getToken('alias')
    }

    @Test
    void 'should count tokens' () {
        def tokenService = ((AuthManager)AUTH_MANAGER).@auth.getTokenService()
        assertEquals 0, tokenService.getTokens().size()

        AUTH_MANAGER.createToken('a', 'a', 'rw', 'p')
        AUTH_MANAGER.createToken('b', 'b', 'rw', 'p')
        assertEquals 2, tokenService.getTokens().size()

        tokenService.deleteToken('a')
        assertEquals 1, tokenService.getTokens().size()
    }

    @Test
    void 'should get all tokens' () {
        def tokenService = ((AuthManager)AUTH_MANAGER).@auth.getTokenService()
        assertIterableEquals Collections.emptyList(), tokenService.getTokens()

        def token = AUTH_MANAGER.createToken('path', 'alias', 'rw', 'p')
        assertIterableEquals Collections.singletonList(token), tokenService.getTokens()
    }

}