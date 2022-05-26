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

package org.panda_lang.reposilite

import groovy.transform.CompileStatic
import io.javalin.http.Context
import io.javalin.http.HandlerType
import io.javalin.http.util.ContextUtil
import java.util.Base64
import java.util.concurrent.Executors
import org.eclipse.jetty.server.HttpInput
import org.eclipse.jetty.server.HttpOutput
import org.junit.jupiter.api.*
import org.junit.jupiter.api.io.TempDir
import org.panda_lang.reposilite.auth.IAuthManager
import org.panda_lang.reposilite.repository.IRepositoryManager
import org.panda_lang.reposilite.repository.IRepository.View
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import static org.junit.jupiter.api.Assertions.*
import static org.mockito.ArgumentMatchers.anyString
import static org.mockito.Mockito.*

@TestMethodOrder(MethodOrderer.MethodName.class)
@CompileStatic
class ReposiliteContextTest {

    private static final String IP_HEADER = 'Test-Ip-Header'
    private static final Map<String, String> HEADERS = [
        (IP_HEADER): 'forwarded address',
        'Custom': 'Value'
    ]

    @TempDir
    protected static File WORKING_DIRECTORY
    private static IRepositoryManager REPOSITORY_MANAGER
    private static IAuthManager AUTH_MANAGER

    @BeforeAll
    static void prepare() {
        REPOSITORY_MANAGER = IRepositoryManager.builder()
            .dir(WORKING_DIRECTORY)
            .quota('0')
            .executor(Executors.newSingleThreadExecutor())
            .scheduled(Executors.newSingleThreadScheduledExecutor())
            .repo('filtered', {
                it.prefix('/special/')
            })
            .repo("main", {})
            .build()

        AUTH_MANAGER = IAuthManager.builder()
            .dir(WORKING_DIRECTORY)
            .repo(REPOSITORY_MANAGER)
            .build();

        AUTH_MANAGER.createToken("/releases/", "auth", "rwm", "password");
    }

    @Test
    void 'should detect forwarded ip address' () {
        def context = ReposiliteContext.create(AUTH_MANAGER, REPOSITORY_MANAGER, IP_HEADER, createContext())
        assertEquals 'forwarded address', context.address()
    }

    @Test
    void 'should map standard context' () {
        def context = ReposiliteContext.create(AUTH_MANAGER, REPOSITORY_MANAGER, IP_HEADER, createContext())
        assertEquals 'uri', context.uri()
        assertEquals 'GET', context.method()
        assertEquals 'forwarded address', context.address()
        assertEquals HEADERS, context.headers()
        assertNotNull context.input()
    }

    @Test
    void 'should have valid session' () {
        def context = ReposiliteContext.create(AUTH_MANAGER, REPOSITORY_MANAGER, IP_HEADER, createContext('/api', 'auth', 'password'))
        assertTrue context.session().isOk()
    }

    @Test
    void 'should have invalid session' () {
        def context = ReposiliteContext.create(AUTH_MANAGER, REPOSITORY_MANAGER, IP_HEADER, createContext('/api', 'auth', 'wrong'))
        assertTrue context.session().isErr()
    }

    @Test
    void 'should trim root api path' () {
        def context = ReposiliteContext.create(AUTH_MANAGER, REPOSITORY_MANAGER, IP_HEADER, createContext('/api'))
        assertEquals '', context.filepath()
        context = ReposiliteContext.create(AUTH_MANAGER, REPOSITORY_MANAGER, IP_HEADER, createContext('/api/'))
        assertEquals '', context.filepath()
    }

    @Test
    void 'should have main and filtered repositories for releases' () {
        def expected = Arrays.asList(REPOSITORY_MANAGER.getRepo('filtered'), REPOSITORY_MANAGER.getRepo('main'))
        def context = ReposiliteContext.create(AUTH_MANAGER, REPOSITORY_MANAGER, IP_HEADER, createContext('/releases/'))
        assertEquals '', context.filepath()
        assertIterableEquals expected, context.repos()
        assertEquals View.RELEASES, context.view()
        context = ReposiliteContext.create(AUTH_MANAGER, REPOSITORY_MANAGER, IP_HEADER, createContext('/releases'))
        assertEquals '', context.filepath()
        assertEquals View.RELEASES, context.view()
        assertIterableEquals expected, context.repos()
    }

    @Test
    void 'should have main repo for main-releases' () {
        def expected = Arrays.asList(REPOSITORY_MANAGER.getRepo('main'))
        def context = ReposiliteContext.create(AUTH_MANAGER, REPOSITORY_MANAGER, IP_HEADER, createContext('/main-releases/'))
        assertEquals '', context.filepath()
        assertIterableEquals expected, context.repos()
        assertEquals View.RELEASES, context.view()
    }

    @Test
    void 'should have main-snapshots and filtered repositories for snapshots' () {
        def expected = Arrays.asList(REPOSITORY_MANAGER.getRepo('filtered'), REPOSITORY_MANAGER.getRepo('main'))
        def context = ReposiliteContext.create(AUTH_MANAGER, REPOSITORY_MANAGER, IP_HEADER, createContext('/snapshots/'))
        assertEquals '', context.filepath()
        assertEquals View.SNAPSHOTS, context.view()
        assertIterableEquals expected, context.repos()
        context = ReposiliteContext.create(AUTH_MANAGER, REPOSITORY_MANAGER, IP_HEADER, createContext('/snapshots'))
        assertEquals '', context.filepath()
        assertEquals View.SNAPSHOTS, context.view()
        assertIterableEquals expected, context.repos()
    }

    @Test
    void 'should have main repo for main-snapshots' () {
        def expected = Arrays.asList(REPOSITORY_MANAGER.getRepo('main'))
        def context = ReposiliteContext.create(AUTH_MANAGER, REPOSITORY_MANAGER, IP_HEADER, createContext('/main-snapshots/'))
        assertEquals '', context.filepath()
        assertIterableEquals expected, context.repos()
        assertEquals View.SNAPSHOTS, context.view()
    }

    @Test
    void 'should have main repo and all view for main' () {
        def expected = Arrays.asList(REPOSITORY_MANAGER.getRepo('main'))
        def context = ReposiliteContext.create(AUTH_MANAGER, REPOSITORY_MANAGER, IP_HEADER, createContext('/main/'))
        assertEquals '', context.filepath()
        assertIterableEquals expected, context.repos()
        assertEquals View.ALL, context.view()
    }

    @Test
    void 'should have main repo and all view for main with path' () {
        def expected = Arrays.asList(REPOSITORY_MANAGER.getRepo('main'))
        def context = ReposiliteContext.create(AUTH_MANAGER, REPOSITORY_MANAGER, IP_HEADER, createContext('/main/some/path'))
        assertEquals 'some/path', context.filepath()
        assertIterableEquals expected, context.repos()
        assertEquals View.ALL, context.view()
    }

    @Test
    void 'should have all repositories at root' () {
        def context = ReposiliteContext.create(AUTH_MANAGER, REPOSITORY_MANAGER, IP_HEADER, createContext('/'))
        assertEquals '', context.filepath()
        assertEquals View.ALL, context.view()
        assertIterableEquals REPOSITORY_MANAGER.getRepos(), context.repos()
    }

    /*
     * TODO: Prefilter repos? This has issues  with the lookup endpoint for paths above the prefixes
    @Test
    void 'should have main-release and main-snapshots repositories' () {
        def context = ReposiliteContext.create(AUTH_MANAGER, REPOSITORY_MANAGER, IP_HEADER, createContext('/without/repo'))
        assertEquals 'without/repo', context.filepath()
        assertIterableEquals Arrays.asList(REPOSITORY_MANAGER.getRepo('main-releases'), REPOSITORY_MANAGER.getRepo('main-snapshots')), context.repos()
    }

    @Test
    void 'should have only filtered repository' () {
        def context = ReposiliteContext.create(AUTH_MANAGER, REPOSITORY_MANAGER, IP_HEADER, createContext('/special/path'))
        assertEquals 'filtered/special/minecraftforge/test', context.normalized()
        assertEquals Arrays.asList(REPOSITORY_MANAGER.getRepo('filtered')), context.repos()
    }
    */

    @Test
    void 'should not allow path escapes' () {
        assertNull ReposiliteContext.sanitize('~/home')
        assertNull ReposiliteContext.sanitize('../../../../monkas')
        assertNull ReposiliteContext.sanitize('C:\\')
        assertEquals 'test/path', ReposiliteContext.sanitize('////test/path')
    }

    /*
    @Test
    void 'should not reqrite path' () {
        assertEquals "main-releases/with/repo/", ReposiliteContext.normalizeUri(REPOSITORY_MANAGER, "main-releases/with/repo/").get()
    }

    @Test
    void 'should add main-releases repository normalize' () {
        assertEquals "main-releases/without/repo/", ReposiliteContext.normalizeUri(REPOSITORY_MANAGER, "/without/repo/").get()
    }

    @Test
    void 'should add filtered repository normalize' () {
        assertEquals "filtered/special/without/repo/", ReposiliteContext.normalizeUri(REPOSITORY_MANAGER, "/special/without/repo/").get()
    }
    */

    /* TODO: Actually write a better test for this
    @Test
    void 'should properly handle context values' () {
        def headers = [ "header": "value" ]

        def context = new ReposiliteContext(
                "uri",
                "method",
                "address",
                headers,
                { new ByteArrayInputStream() })

        assertEquals "uri", context.uri()
        assertEquals "method", context.method()
        assertEquals "address", context.address()
        assertEquals headers, context.headers()
        assertNotNull context.input()
    }
    */

    private static Context createContext() {
        return createContext('uri')
    }
    private static Context createContext(String uri) {
        return createContext(uri, null, null)
    }
    private static Context createContext(String uri, String username, String password) {
        def headers = new HashMap<>(HEADERS)
        if (username != null)
            headers.put('Authorization', 'Basic ' + Base64.encoder.encodeToString((username + ':' + password).bytes))
        def request = mock(HttpServletRequest.class, RETURNS_DEEP_STUBS)
        when(request.getRequestURI()).thenReturn(uri)
        when(request.getMethod()).thenReturn('GET')
        when(request.getRemoteAddr()).thenReturn("localhost")
        when(request.getInputStream()).thenReturn(mock(HttpInput.class))
        when(request.getHeaderNames()).thenReturn(Collections.enumeration(headers.keySet()))
        when(request.headerNames).thenReturn(Collections.enumeration(headers.keySet()))
        when(request.getHeader(anyString())).thenAnswer({ invocation ->
            def header = invocation.getArgument(0) as String
            return headers.get(header)
        })

        def response = mock HttpServletResponse.class, RETURNS_DEEP_STUBS
        when(response.getOutputStream()).thenReturn(mock(HttpOutput.class))

        return ContextUtil.init(request, response, '*', [:], HandlerType.GET)
    }

}
