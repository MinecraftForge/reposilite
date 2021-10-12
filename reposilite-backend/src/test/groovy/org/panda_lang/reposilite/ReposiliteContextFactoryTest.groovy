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
import java.util.concurrent.Executors
import org.eclipse.jetty.server.HttpInput
import org.eclipse.jetty.server.HttpOutput
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.panda_lang.reposilite.auth.IAuthManager
import org.panda_lang.reposilite.repository.IRepositoryManager

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.mockito.ArgumentMatchers.anyString
import static org.mockito.Mockito.*

@CompileStatic
class ReposiliteContextFactoryTest {

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
            .repo("releases", {})
            .repo("snapshots", {})
            .build()

        AUTH_MANAGER = IAuthManager.builder()
            .dir(WORKING_DIRECTORY)
            .repo(REPOSITORY_MANAGER)
            .build();
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
    void 'should trim root /api path' () {
        def context = ReposiliteContext.create(AUTH_MANAGER, REPOSITORY_MANAGER, IP_HEADER, createContext('/api'))
        assertEquals '', context.normalized()
    }

    @Test
    void 'should trim root /api/ path' () {
        def context = ReposiliteContext.create(AUTH_MANAGER, REPOSITORY_MANAGER, IP_HEADER, createContext('/api/'))
        assertEquals '', context.normalized()
    }

    @Test
    void 'should add release repository' () {
        def context = ReposiliteContext.create(AUTH_MANAGER, REPOSITORY_MANAGER, IP_HEADER, createContext('/net/minecraftforge/test'))
        assertEquals 'releases/net/minecraftforge/test', context.normalized()
    }

    private static Context createContext() {
        return createContext('uri')
    }
    private static Context createContext(String uri) {
        def request = mock(HttpServletRequest.class, RETURNS_DEEP_STUBS)
        when(request.getRequestURI()).thenReturn(uri)
        when(request.getMethod()).thenReturn('GET')
        when(request.getRemoteAddr()).thenReturn("localhost")
        when(request.getInputStream()).thenReturn(mock(HttpInput.class))
        when(request.getHeaderNames()).thenReturn(Collections.enumeration(HEADERS.keySet()))
        when(request.headerNames).thenReturn(Collections.enumeration(HEADERS.keySet()))
        when(request.getHeader(anyString())).thenAnswer({ invocation ->
            def header = invocation.getArgument(0) as String
            return HEADERS.get(header)
        })

        def response = mock HttpServletResponse.class, RETURNS_DEEP_STUBS
        when(response.getOutputStream()).thenReturn(mock(HttpOutput.class))

        return ContextUtil.init(request, response, '*', [:], HandlerType.GET)
    }

}
