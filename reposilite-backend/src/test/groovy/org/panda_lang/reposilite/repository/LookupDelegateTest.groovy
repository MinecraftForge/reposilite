package org.panda_lang.reposilite.repository

import groovy.transform.CompileStatic
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.io.TempDir
import org.panda_lang.reposilite.ReposiliteConstants
import org.panda_lang.reposilite.ReposiliteContext
import org.panda_lang.reposilite.ReposiliteIntegrationTestSpecification
import org.panda_lang.reposilite.ReposiliteLauncher
import org.panda_lang.reposilite.config.Configuration
import org.panda_lang.reposilite.config.ConfigurationLoader
import org.panda_lang.reposilite.error.FailureService
import org.panda_lang.utilities.commons.FileUtils

import java.util.concurrent.ExecutorService

import static org.apache.http.HttpStatus.*
import static org.junit.jupiter.api.Assertions.*

//TODO: Restructure integration tests so that each test can have its own config
@CompileStatic
@TestMethodOrder(MethodOrderer.MethodName.class)
final class LookupDelegateTest extends ReposiliteIntegrationTestSpecification {
    {
        super.properties.putAll([
            'repositories':                        'main-releases,delegate',
            'repositories.main-releases.delegate': 'delegate'
        ])
    }
    private static final String FILE_PATH = '/delegated/artifact/version/file'
    private static final String FILE_CONTENT = 'content'

    @BeforeEach
    void configure() throws IOException {
        def file = super.reposilite.repos.getRepo('delegate').getFile(FILE_PATH)
        file.getParentFile().mkdirs()
        file.createNewFile()
        FileUtils.overrideFile(file, FILE_CONTENT)
    }

    @Test
    void 'should return 200 and delegated file from releases request' () {
        def content = shouldReturnData(SC_OK, '/main-releases' + FILE_PATH)
        assertEquals FILE_CONTENT, content
    }

   @Test
   void 'should return 200 and delegated file from delegate request' () {
       def content = shouldReturnData(SC_OK, '/delegate' + FILE_PATH)
       assertEquals FILE_CONTENT, content
   }

   private static void assertResponseWithMessage(int status, String url, String message) {
       def content = shouldReturnData(status, url)
       assertTrue(content.contains("REPOSILITE_MESSAGE = '" + message + "'"));
   }
}
