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

package org.panda_lang.reposilite.repository;

import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.panda_lang.reposilite.Reposilite;
import org.panda_lang.reposilite.ReposiliteConfiguration;
import org.panda_lang.reposilite.console.ReposiliteCommand;

import picocli.CommandLine.Command;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public final class MetadataService implements ReposiliteConfiguration {
    private final MetadataXpp3Reader XML_READER = new MetadataXpp3Reader();
    private final MetadataXpp3Writer XML_WRITER = new MetadataXpp3Writer();

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<String, List<CacheEntry>> cacheInputs = new ConcurrentHashMap<>();
    private final BiConsumer<String, Exception> errorHandler;

    public MetadataService(BiConsumer<String, Exception> errorHandler) {
        this.errorHandler = errorHandler;
    }

    /*
     * Merges all maven-metadata.xml files found at the specified location.
     *
     * https://maven.apache.org/ref/3.3.3/maven-repository-metadata/
     * This specifies three locations for metadata:
     *   1) Unversioned artifact: /group/artifact/maven-metadata.xml
     *     This list all versions for this artifact
     *   2) Snapshot artifact: /group/artifact/version-SNAPSHOT/maven-metadata.xml
     *     Lists the files associated with the latest snapshot version.
     *     I don't think we should need to screw with this.
     *   3) Maven Plugin Group: /group/maven-metadata.xml
     *     This I dont think we can generate... So request from the proxy, or 404
     */
    public byte[] mergeMetadata(String key, String filepath, List<IRepository> repos) {
        if (!filepath.endsWith("/maven-metadata.xml"))
            throw new IllegalArgumentException("Invalid maven-metadata.xml filename: " + filepath);

        CacheEntry cached = cache.get(key);
        if (cached != null)
            return cached.data;

        // As there is no way to tell if a path is an actual artifact from the url, we need to check if
        // a metadata file already exists. This way we don't create metadata files for directory listings.
        List<String> inputs = new ArrayList<>();
        List<File> existing = new ArrayList<>();
        for (IRepository repo : repos) {
            File meta = repo.getFile(filepath);
            inputs.add(meta.getAbsolutePath());
            if (meta.exists())
                existing.add(meta);
        }

        if (existing.isEmpty())
            return null;

        if (existing.size() == 1) {
            try {
                return addCache(key, Files.readAllBytes(existing.get(0).toPath()), inputs);
            } catch (IOException e) {
                return null; // TODO: Actually do something about this error?
            }
        }

        Metadata ret = null;

        for (File path : existing) {
            try (FileInputStream fis = new FileInputStream(path)) {
                Metadata input = XML_READER.read(fis);
                if (ret == null)
                    ret = input;
                else
                    ret.merge(input);
            } catch (IOException | XmlPullParserException e) {
                errorHandler.accept("Error parsing " + path.getAbsolutePath(), e);
                return null; // TODO: Actually do something about this error?
            }
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            XML_WRITER.write(bos, ret);
        } catch (IOException e) {
            // Really, an IO exception on a byte array?
            errorHandler.accept("Error writing " + key, e);
            return null;
        }

        return addCache(key, bos.toByteArray(), inputs);
    }

    private byte[] addCache(String key, byte[] data, List<String> inputs) {
        CacheEntry entry = new CacheEntry(key, data, inputs);
        cache.put(key, entry);
        for (String input : inputs)
            cacheInputs.computeIfAbsent(input, k -> new ArrayList<>()).add(entry);

        return data;
    }

    public void clearMetadata(File file) {
        String key = file.getAbsolutePath();
        List<CacheEntry> entries = cacheInputs.remove(key);
        if (entries != null) {
            for (CacheEntry ent : entries) {
                cache.remove(ent.path);
                ent.inputs.remove(key);
                for (String input : ent.inputs) {
                    List<CacheEntry> child = cacheInputs.get(input);
                    if (child != null) {
                        child.remove(ent);
                        if (child.isEmpty())
                            cacheInputs.remove(input);
                    }
                }
            }
        }
    }

    public int purgeCache() {
        int count = getCacheSize();
        cache.clear();
        cacheInputs.clear();
        return count;
    }

    public int getCacheSize() {
        return cache.size();
    }

    @Override
    public void configure(Reposilite reposilite) {
        @Command(name = "purge", description = "Clear cache")
        final class PurgeCommand implements ReposiliteCommand {
            @Override
            public boolean execute(List<String> output) {
                output.add("Purged " + MetadataService.this.purgeCache() + " elements");
                return true;
            }
        }
        reposilite.getConsole().registerCommand(new PurgeCommand());
    }

    private static class CacheEntry {
        final String path;
        final byte[] data;
        final List<String> inputs;
        private CacheEntry(String path, byte[] data, List<String> inputs)  {
            this.path = path;
            this.data = data;
            this.inputs = inputs;
        }
    }
}
