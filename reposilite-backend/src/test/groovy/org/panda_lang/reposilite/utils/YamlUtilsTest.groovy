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
import org.junit.jupiter.api.io.TempDir

import static org.junit.jupiter.api.Assertions.assertEquals

@CompileStatic
class YamlUtilsTest {

    @TempDir
    protected File workingDirectory

    @Test
    void getRepositories() {
        def data = FilesUtils.getResource("/private_test.yml", null)
        def expected = new PackagePrivate("key!", "value!")
        def actual = YamlUtils.load(data, PackagePrivate.class)
        assertEquals expected.key, actual.key
        assertEquals expected.value, actual.value
    }



    static class PackagePrivate {
        String key
        String value

        PackagePrivate() {}

        PackagePrivate(String key, String value) {
            this.key = key
            this.value = value
        }

        public void setKey(String value) {
            this.key = value
        }
        public void setValue(String value) {
            this.value = value
        }
    }
}
