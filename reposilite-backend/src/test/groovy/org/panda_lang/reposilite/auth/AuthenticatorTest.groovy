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
import java.util.concurrent.Executors
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.panda_lang.reposilite.repository.IRepositoryManager
import org.panda_lang.utilities.commons.collection.Maps

import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach

@CompileStatic
class AuthenticatorTest {
    @TempDir
    protected static File WORKING_DIRECTORY
    protected static IRepositoryManager REPOSITORY_MANAGER
    protected IAuthManager AUTH_MANAGER
    protected Token AUTH_TOKEN
    protected String BASIC

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
        def username = 'alias'
        def password = 'secret' //AUTH_MANAGER.createRandomPassword()
        AUTH_TOKEN = AUTH_MANAGER.createToken('/auth/test', username, 'rw', password)
        BASIC = 'Basic ' + Base64.encoder.encodeToString((username + ':' + password).bytes)
    }

    @Test
    void 'should not auth without authorization header' () {
        assertTrue AUTH_MANAGER.getSession(Collections.emptyMap()).isErr()
    }

    @Test
    void 'should not auth using other auth method' () {
        assertTrue AUTH_MANAGER.getSession(Maps.of("Authorization", "Bearer " + AUTH_TOKEN.getToken())).isErr()
    }

    @Test
    void 'should not auth using invalid basic format' () {
        assertTrue AUTH_MANAGER.getSession(Maps.of("Authorization", "Basic")).isErr()
    }

    @Test
    void 'should not auth using null credentials' () {
        assertTrue AUTH_MANAGER.getSession((String) null).isErr()
    }

    @Test
    void 'should not auth using credentials with invalid format' () {
        assertTrue AUTH_MANAGER.getSession("alias " + AUTH_TOKEN.getToken()).isErr()
        assertTrue AUTH_MANAGER.getSession("alias:" + AUTH_TOKEN.getToken() + ":whatever").isErr()
        assertTrue AUTH_MANAGER.getSession(":" + AUTH_TOKEN.getToken()).isErr()
    }

    @Test
    void 'should not auth using invalid credentials' () {
        assertTrue AUTH_MANAGER.getSession("admin:admin").isErr()
        assertTrue AUTH_MANAGER.getSession("alias:another_secret").isErr()
        assertTrue AUTH_MANAGER.getSession("alias:" + TokenService.B_CRYPT_TOKENS_ENCODER.encode("secret")).isErr()
    }

    @Test
    void 'should auth' () {
        assertTrue AUTH_MANAGER.getSession("alias:secret").isOk()
        assertTrue AUTH_MANAGER.getSession(Maps.of("Authorization", BASIC)).isOk()
    }

    @Test
    void 'should auth context' () {
        assertTrue AUTH_MANAGER.getSession(Maps.of("Authorization", BASIC)).isOk()
    }

    @Test
    void 'should not auth invalid uri' () {
        def auth = AUTH_MANAGER.getSession(Maps.of("Authorization", BASIC))
        assertTrue auth.isOk()
        assertFalse auth.get().hasPermissionTo("auth")
    }

    @Test
    void 'should auth uri' () {
        def auth = AUTH_MANAGER.getSession(Maps.of("Authorization", BASIC))
        assertTrue auth.isOk()
        assertTrue auth.get().hasPermissionTo("auth/test")
    }

}