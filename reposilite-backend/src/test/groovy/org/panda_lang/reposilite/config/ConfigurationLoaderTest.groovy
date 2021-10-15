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

package org.panda_lang.reposilite.config

import groovy.transform.CompileStatic
import net.dzikoysk.cdn.CdnFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.panda_lang.utilities.commons.FileUtils
import org.panda_lang.utilities.commons.text.Joiner

import static org.junit.jupiter.api.Assertions.*

@CompileStatic
class ConfigurationLoaderTest {
    @TempDir
    protected File WORKING_DIRECTORY

    @BeforeEach
    void before() {
        cleanup()
    }

    @AfterEach
    void cleanup() {
        List<String> toClear = []
        System.properties.keys().each { k ->
            if (k instanceof String && k.startsWith('reposilite'))
                toClear.add(k)
        }
        toClear.each { System.clearProperty(it) }
    }

    Configuration cfg(Map<String, String> props) {
        props.each {k, v -> System.setProperty('reposilite.' + k, v) }
        return ConfigurationLoader.tryLoad('', WORKING_DIRECTORY.getAbsolutePath())
    }

    @Test
    void 'should load file with custom properties' () {
        def conf = cfg([
            'hostname':     'localhost',    // String type
            'port':         '8080',         // Integer type
            'debugEnabled': 'true',         // Boolean type
            'repositories': ' ',            // Skip empty
            'repositories.releases.proxies': 'http://a.com,b.com',  // List<String> type
        ])

        assertEquals "localhost", conf.hostname
        assertEquals 8080, conf.port
        assertTrue conf.debugEnabled
        assertEquals Arrays.asList("http://a.com/", "b.com/"), conf.repositories.get('releases').proxies
        assertFalse conf.repositories.isEmpty()
    }

    @Test
    void 'should load custom map entries' () {
        def conf = cfg(['repositories': 'test'])
        assertEquals 1, conf.repositories.size()
        assertTrue conf.repositories.containsKey('test')
    }

    @Test
    void 'should load empty map' () {
        def conf = cfg(['repositories': '{}'])
        assertTrue conf.repositories.isEmpty()
    }

    @Test
    void 'should load custom config' () {
        def file = new File(WORKING_DIRECTORY, 'random.cdn')
        ConfigurationLoader.createCdn().render(new Configuration(), file)
        FileUtils.overrideFile(file, FileUtils.getContentOfFile(file).replace('port: 80', 'port: 7'))

        def conf = ConfigurationLoader.tryLoad(file.getAbsolutePath(), WORKING_DIRECTORY.getAbsolutePath())
        assertEquals 7, conf.port
    }

    @Test
    void 'should not load other file types' () {
        def file = new File(WORKING_DIRECTORY, 'random.properties')
        ConfigurationLoader.createCdn().render(new Configuration(), file)
        assertThrows RuntimeException.class, { ConfigurationLoader.load(file.getAbsolutePath(), WORKING_DIRECTORY.getAbsolutePath()) }
    }

    @Test
    void 'should sanitize proxies' () {
        def config = cfg(['repositories.snapshots.proxies': 'https://without.slash,https://with.slash/'])
        assertEquals Arrays.asList('https://without.slash/', 'https://with.slash/'), config.repositories.get('snapshots').proxies
    }

    @Test
    void 'should sanitize prefixes' () {
        def config = cfg(['repositories.snapshots.prefixes': 'without.slash,with.slash/,/with.extra.slash/'])
        assertEquals Arrays.asList('without.slash/', 'with.slash/', 'with.extra.slash/'), config.repositories.get('snapshots').prefixes
    }

    @Test
    void 'should sanitize base path' () {
        def config = cfg(['basePath': 'noslash'])
        assertEquals '/noslash/', config.basePath
    }

    @Test
    void 'should error with proxy and delegate' () {
        assertThrows IllegalStateException.class, {
            cfg([
                'repositories.releases.delegate': 'snapshots',
                'repositories.releases.proxies':  'localhost'
            ])
        }
    }

    @Test
    void 'should error with invalid delegate' () {
        assertThrows IllegalStateException.class, {
            cfg(['repositories.releases.delegate': 'unknown repo'])
        }
    }

}