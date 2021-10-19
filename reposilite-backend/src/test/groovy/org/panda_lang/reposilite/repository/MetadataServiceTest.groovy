package org.panda_lang.reposilite.repository

import groovy.transform.CompileStatic
import org.junit.jupiter.api.Test
import org.panda_lang.reposilite.ReposiliteTestSpecification
import org.panda_lang.reposilite.repository.RepositoryManager
import org.panda_lang.utilities.commons.function.Result

import static org.junit.jupiter.api.Assertions.*

@CompileStatic
final class MetadataServiceTest extends ReposiliteTestSpecification {
    @Test
    void 'should error on invalid name' () {
        assertThrows IllegalArgumentException.class, {
            generate('reposilite/test/not-maven-metdata.xml')
        }
    }

    @Test
    void 'should not generate if no files exist' () {
        assertNull generate('reposilite/missing/maven-metadata.xml')
    }

    @Test
    void 'should merge artifact version list' () {
        def result = generate('reposilite/test/maven-metadata.xml')

        assertNotNull result
        assertTrue result.contains('1.0.0')
        assertTrue result.contains('1.0.1')
        assertTrue result.contains('1.0.0-SNAPSHOT')
        assertTrue result.contains('1.0.1-SNAPSHOT')
    }

    @Test
    void 'should return snapshot metadata content' () {
        def timestamp = '20211016.230946'
        def result = generate('reposilite/test/1.0.0-SNAPSHOT/maven-metadata.xml')
        assertNotNull result
        assertTrue result.contains('1.0.0-SNAPSHOT')
        assertTrue result.contains('<buildNumber>1</buildNumber>')
        assertTrue result.contains('<timestamp>' + timestamp + '</timestamp>')
        assertTrue result.contains('<extension>pom</extension>')
        assertTrue result.contains('<value>1.0.0-' + timestamp + '-1</value>')
    }

    @Test
    void 'should clear cache' () {
        def path = 'reposilite/test/maven-metadata.xml'
        generate(path)

        def metadataService = metadata()
        assertEquals 1, metadataService.getCacheSize()

        metadataService.clearMetadata(super.reposilite.getRepos().getRepo('main-releases').getFile(path))
        assertEquals 0, metadataService.getCacheSize()
    }

    @Test
    void 'should purge cache' () {
        def metadataService = metadata()
        assertEquals 0, metadataService.purgeCache()
        assertEquals 0, metadataService.getCacheSize()

        generateAll()
        assertEquals 3, metadataService.purgeCache()
        assertEquals 0, metadataService.getCacheSize()
    }

    @Test
    void 'should return current cache size' () {
        def metadataService = metadata()
        assertEquals 0, metadataService.getCacheSize()

        generateAll()
        assertEquals 3, metadataService.getCacheSize()
    }

    private void generateAll() {
        generate 'reposilite/test/maven-metadata.xml'
        generate 'reposilite/test/1.0.0-SNAPSHOT/maven-metadata.xml'
        generate 'reposilite/test/1.0.1-SNAPSHOT/maven-metadata.xml'
        generate 'reposilite/test/1.0.1/maven-metadata.xml' // should not generate this one, releases don't have metadata
    }


    private MetadataService metadata() {
        return ((RepositoryManager)super.reposilite.repos).@metadataService
    }

    private String generate(String path) {
        def manager = super.reposilite.repos
        def releases = manager.getRepo('main-releases')
        def snapshots = manager.getRepo('main-snapshots')
        def bytes = metadata().mergeMetadata(path, path, [releases, snapshots])
        return bytes == null ? null : new String(bytes)
    }

}
