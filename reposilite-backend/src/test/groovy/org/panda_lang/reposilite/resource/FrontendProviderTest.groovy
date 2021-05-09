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

package org.panda_lang.reposilite.resource

import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test
import org.panda_lang.reposilite.config.Configuration

import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue

import org.apache.http.HttpStatus

@CompileStatic
class FrontendProviderTest {

    private static final FrontendProvider FRONTEND_SERVICE = FrontendProvider.load(new Configuration(), null)

    @Test
    void forMessage () {
        assertTrue FRONTEND_SERVICE.forMessage(HttpStatus.SC_NOT_FOUND, "test message").contains("test message")
        assertFalse FRONTEND_SERVICE.forMessage(HttpStatus.SC_OK, "test message").contains("other message")
    }

    @Test
    void getApp () {
        assertTrue FRONTEND_SERVICE.getApp().contains("Vue")
    }

}