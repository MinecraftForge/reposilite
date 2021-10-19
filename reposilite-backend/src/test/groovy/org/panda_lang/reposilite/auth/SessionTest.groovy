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
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.panda_lang.reposilite.config.Configuration
import org.panda_lang.reposilite.error.FailureService
import org.panda_lang.reposilite.repository.IRepositoryManager

import java.util.concurrent.Executors

import static org.junit.jupiter.api.Assertions.*

@CompileStatic
class SessionTest {

    @TempDir
    protected static File WORKING_DIRECTORY
    protected static IRepositoryManager REPOSITORY_MANAGER
    protected IAuthManager AUTH_MANAGER;

    @BeforeAll
    static void prepare () {
        REPOSITORY_MANAGER = IRepositoryManager.builder()
            .dir(WORKING_DIRECTORY)
            .quota('0')
            .executor(Executors.newSingleThreadExecutor())
            .scheduled(Executors.newSingleThreadScheduledExecutor())
            .repo("main-releases", {})
            .repo("main-snapshots", {})
            .build();
    }

    @BeforeEach
    void before() {
        AUTH_MANAGER = IAuthManager.builder()
            .dir(WORKING_DIRECTORY)
            .repo(REPOSITORY_MANAGER)
            .build();
    }

    @Test
    void 'has permission' () {
        def configuration = new Configuration()
        configuration.repositories = new LinkedHashMap()

        def token = AUTH_MANAGER.createToken('/a/b/c', 'alias', 'rw', 'token')
        def signin = AUTH_MANAGER.getSession('alias:token')

        assertTrue signin.isOk()
        def standardSession = signin.get()

        assertTrue standardSession.hasPermissionTo('/a/b/c')
        assertTrue standardSession.hasPermissionTo('/a/b/c/d')

        assertFalse standardSession.hasPermissionTo('/a/b/')
        assertFalse standardSession.hasPermissionTo('/a/b/d')
    }

    @Test
    void 'has permission with wildcard' () {
        def token = AUTH_MANAGER.createToken('*/b/c', 'alias', 'rw', 'token')
        def signin = AUTH_MANAGER.getSession('alias:token')

        assertTrue signin.isOk()
        def wildcardSession = signin.get()

        assertTrue wildcardSession.hasPermissionTo('/main-releases/b/c')
        assertTrue wildcardSession.hasPermissionTo('/main-releases/b/c/d')
        assertTrue wildcardSession.hasPermissionTo('/main-snapshots/b/c')

        assertFalse wildcardSession.hasPermissionTo('/main-releases/b')
        assertFalse wildcardSession.hasPermissionTo('/main-snapshots/b')
        assertFalse wildcardSession.hasPermissionTo('/custom/b/c')
    }

    @Test
    void 'has root permission' () {
        def standardToken = AUTH_MANAGER.createToken('/', 'alias', 'rw', 'token')
        def signin = AUTH_MANAGER.getSession('alias:token')
        assertTrue signin.isOk()
        def standardRootSession = signin.get()

        def wildcardToken = AUTH_MANAGER.createToken('*', 'wild', 'rw', 'token')
        signin = AUTH_MANAGER.getSession('wild:token')
        assertTrue signin.isOk()
        def wildcardRootSession = signin.get()

        assertTrue standardRootSession.hasPermissionTo('/')
        assertFalse wildcardRootSession.hasPermissionTo('/')
        assertTrue wildcardRootSession.hasPermissionTo('/main-releases')
        assertTrue wildcardRootSession.hasPermissionTo('/main-snapshots')
    }

    @Test
    void 'should contain only authorized repositories' () {
        def token = AUTH_MANAGER.createToken('/main-releases', 'alias', 'rw', 'token')
        def signin = AUTH_MANAGER.getSession('alias:token')
        assertTrue signin.isOk()
        def session = signin.get()
        assertEquals Collections.singletonList(REPOSITORY_MANAGER.getRepo('main-releases')), session.getRepositories()
    }

    @Test
    void 'should return empty list for unknown repository in path' () {
        def token = AUTH_MANAGER.createToken('/unknown_repository', 'alias', 'rw', 'token')
        def signin = AUTH_MANAGER.getSession('alias:token')
        assertTrue signin.isOk()
        def session = signin.get()
        assertEquals Collections.emptyList(), session.getRepositories()
    }

}