/*
 * Copyright (c) 2020 Ole Ludwig
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

package org.panda_lang.reposilite.repository

import groovy.transform.CompileStatic

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.io.TempDir
import org.panda_lang.reposilite.repository.IRepository.View
import org.panda_lang.utilities.commons.FileUtils
import java.nio.channels.FileChannel
import java.nio.file.OpenOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

import static org.junit.jupiter.api.Assertions.*

@CompileStatic
@TestMethodOrder(MethodOrderer.MethodName.class)
class RepositoryStorageTest {
    @TempDir
    protected static File WORKING_DIRECTORY
    private static RepositoryManager REPOSITORY_MANAGER

    @BeforeAll
    static void prepare() {
        REPOSITORY_MANAGER = (RepositoryManager)IRepositoryManager.builder()
            .dir(WORKING_DIRECTORY)
            .quota('0')
            .executor(new ThreadPoolExecutor(0, Integer.MAX_VALUE, 1, TimeUnit.SECONDS, new SynchronousQueue<>()))
            .scheduled(Executors.newSingleThreadScheduledExecutor())
            .repo('main', {})
            .build()
    }

    @Test
    void 'should add size of written file to the disk quota'() {
        def releases = REPOSITORY_MANAGER.getRepo('main')
        def initialUsage = REPOSITORY_MANAGER.quota.usage
        def string = 'test'
        def expectedUsage = initialUsage + string.bytes.length

        REPOSITORY_MANAGER.@storage.storeFile(stream(string), releases, 'file', View.RELEASES)

        assertEquals expectedUsage, REPOSITORY_MANAGER.quota.usage
    }

    /* This test never worked because locks are JVM wide, so we would share it.
     * This is to resolve multiple reposilite JVMs using the same backend data storage
     * https://github.com/dzikoysk/reposilite/commit/9dd20174bf09dcc201696dd7e142d4eb3862f975
     * Honestly, I dont think this is something we care about for the time being, so we'll address it later.
    @Test
    void 'should retry deployment of locked file' () {
        def releases = REPOSITORY_MANAGER.getRepo('releases')
        def storage = REPOSITORY_MANAGER.@storage
        def content = 'new content'
        def path = 'a/b/c.txt'

        def file = releases.getFile(path)
        file.getParentFile().mkdirs()
        FileUtils.overrideFile(file, 'test')

        def channel = FileChannel.open(file.toPath(), [ StandardOpenOption.WRITE ] as OpenOption[])
        def lock = channel.lock()

        def start = System.currentTimeMillis()
        new Thread({
            Thread.sleep(1000L)
            lock.release()
        }).start()

        def ret = storage.storeFile(stream(content), releases, path)
        def end = System.currentTimeMillis()
        def read = file.text
        //System.out.println("Time: " + start + " " + end + " " + (end - start))
        assertEquals content, read
        assertTrue end - start >= 1000, 'Stored file too fast'
    }
    */

    private static InputStream stream(String data) {
        return new ByteArrayInputStream(data.bytes)
    }
}
