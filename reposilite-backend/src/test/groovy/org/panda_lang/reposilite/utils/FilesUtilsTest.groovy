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

package org.panda_lang.reposilite.utils

import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable

import static org.junit.jupiter.api.Assertions.*

@CompileStatic
final class FilesUtilsTest {

    @Test
    void 'should convert display size to bytes count' () {
        assertEquals 1024, FilesUtils.displayToBytes("1024")
        assertEquals 1024, FilesUtils.displayToBytes("1024B")
        assertEquals 1024, FilesUtils.displayToBytes("1kb")
        assertEquals 1024 * 1024, FilesUtils.displayToBytes("1mb")
        assertEquals 1024 * 1024 * 1024, FilesUtils.displayToBytes("1gb")
    }

    @Test
    void 'should convert bytes to display' () {
        assertEquals "1023", FilesUtils.bytesToDisplay(1023)
        assertEquals "1KB", FilesUtils.bytesToDisplay(1024)
        assertEquals "1MB", FilesUtils.bytesToDisplay(1024 * 1024)
        assertEquals "1GB", FilesUtils.bytesToDisplay(1024 * 1024 * 1024)
    }

    @Test
    void 'should close closable' () {
        assertDoesNotThrow({ FilesUtils.close(null) } as Executable)
        assertDoesNotThrow({ FilesUtils.close(new ByteArrayInputStream(new byte[0])) } as Executable)

        assertDoesNotThrow({
            def input = new ByteArrayInputStream(new byte[0])
            input.close()
            FilesUtils.close(input)
        } as Executable)
    }

    @Test
    void 'should trim string correctly' () {
        assertEquals 'string', FilesUtils.trim('/string/', '/' as char)
        assertEquals 'string', FilesUtils.trim('string/', '/' as char)
        assertEquals 'string', FilesUtils.trim('///string/', '/' as char)
        assertEquals 'string', FilesUtils.trim('string///', '/' as char)
        assertEquals 'str/ing', FilesUtils.trim('///str/ing///', '/' as char)
        assertEquals '', FilesUtils.trim('', '/' as char)
        assertEquals '', FilesUtils.trim('////', '/' as char)
    }

    @Test
    void 'should validate repository names' () {
        assertThrows IllegalArgumentException.class, {
            FilesUtils.validateRepositoryName("UpperCase")
        }
        assertThrows IllegalArgumentException.class, {
            FilesUtils.validateRepositoryName("main-bad")
        }
        assertThrows IllegalArgumentException.class, {
            FilesUtils.validateRepositoryName("releases")
        }
        assertThrows IllegalArgumentException.class, {
            FilesUtils.validateRepositoryName("snapshots")
        }
        assertThrows IllegalArgumentException.class, {
            FilesUtils.validateRepositoryName("valid-releases")
        }
        assertThrows IllegalArgumentException.class, {
            FilesUtils.validateRepositoryName("valid-snapshots")
        }

        assertDoesNotThrow({
            FilesUtils.validateRepositoryName("valid")
        } as Executable)
    }
}
