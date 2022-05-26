package org.panda_lang.reposilite.repository

import groovy.transform.CompileStatic
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.panda_lang.reposilite.ReposiliteConstants
import org.panda_lang.reposilite.ReposiliteContext
import org.panda_lang.reposilite.ReposiliteIntegrationTestSpecification
import org.panda_lang.reposilite.ReposiliteLauncher
import org.panda_lang.reposilite.config.Configuration
import org.panda_lang.reposilite.config.ConfigurationLoader
import org.panda_lang.reposilite.error.FailureService
import org.panda_lang.reposilite.repository.IRepository.View
import org.panda_lang.utilities.commons.FileUtils

import java.util.concurrent.ExecutorService

import static org.apache.http.HttpStatus.*
import static org.junit.jupiter.api.Assertions.*

@CompileStatic
final class LookupProxyTest extends ReposiliteIntegrationTestSpecification {
    {
        super.properties.putAll([
            'proxyConnectTimeout': '1000',
            'proxyReadTimeout':    '1000',
            'repositories':        'main,proxy',
            'repositories.proxy.allowUploads': 'false',
            'repositories.proxy.proxies':      'http://localhost:' + PORT + '/main-releases',
        ])
    }
    private static final String FILE_PATH = '/proxiedGroup/artifact/version/proxied.pom'
    private static final String FILE_CONTENT = 'proxied content'

    @BeforeEach
    void configure() throws IOException {
        def file = super.reposilite.repos.getRepo('main').getFile(View.RELEASES, FILE_PATH)
        file.getParentFile().mkdirs()
        file.createNewFile()
        FileUtils.overrideFile(file, FILE_CONTENT)
    }

    @Test
    void 'should return 404 and invalid artifact for missing version' () throws Exception {
        assertResponseWithMessage SC_NOT_FOUND, '/proxy/proxiedGroup/proxiedArtifact/notfound.pom', 'Invalid artifact path'
    }

    @Test
    void 'should return 404 and proxied artifact not found for group meta' () throws Exception {
        assertResponseWithMessage SC_NOT_FOUND, '/proxy/proxiedGroup/proxiedArtifact/maven-metadata.xml', 'Artifact not found in local and remote repository'
    }

    @Test
    void 'should return 200 and proxied file' () {
        def content = shouldReturnData(SC_OK, '/proxy' + FILE_PATH)
        assertEquals FILE_CONTENT, content
    }

    private static void assertResponseWithMessage(int status, String url, String message) {
        def content = shouldReturnData(status, url)
        assertTrue(content.contains("REPOSILITE_MESSAGE = '" + message + "'"));
    }

}
