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

import org.apache.commons.io.FileUtils;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.panda_lang.reposilite.Reposilite;

final class Repository implements IRepository {
    private final String name;
    private final File root;
    private final boolean hidden;
    private final boolean readOnly;
    private final boolean browseable;
    private final DiskQuota quota;
    private final List<String> proxies;
    private final List<String> prefixes;

    private Repository(String name, File root, List<String> prefixes, boolean hidden, boolean readOnly, boolean browseable, DiskQuota quota, List<String> proxies) {
        this.name = name;
        this.root = root;
        this.prefixes = prefixes == null || prefixes.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(prefixes);
        this.hidden = hidden;
        this.readOnly = readOnly;
        this.browseable = browseable;
        this.quota = quota;
        this.proxies = proxies == null || proxies.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(proxies);
    }

    @Override
    public void load() {
        if (this.root.mkdirs()) {
            Reposilite.getLogger().info("+ Repository '" + getName() + "' has been created");
        }
        // Calculate how much we've used.
        // TODO: Move to a thread as this can take time and we don't want to stall the server while iterating
        this.quota.allocate(FileUtils.sizeOfDirectory(this.root));
    }

    @Override
    public boolean contains(String... path) {
        File targetFile = getFile(path);

        // TODO: Checks if length < 3 because it was checking for artifact info group:name:version
        // But that was dirty and dumb, we can do better
        if (!targetFile.exists() || targetFile.isDirectory() || path.length < 3)
            return false;

        return true;
    }

    @Override
    public File getFile(String... path) {
        return new File(this.root, Arrays.stream(path).collect(Collectors.joining(File.separator)));
    }

    /*
    public String getUri() {
        return "/" + getName();
    }
    */


    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isReadOnly() {
        return this.readOnly;
    }

    @Override
    public boolean isHidden() {
        return this.hidden;
    }

    @Override
    public boolean canBrowse() {
        return this.browseable;
    }

    @Override
    public IQuota getQuota() {
        return this.quota;
    }

    @Override
    public Collection<String> getProxies() {
        return this.proxies;
    }

    static class Builder implements IRepository.Builder {
        private final String name;
        private List<String> prefixes = new ArrayList<>();
        private boolean hidden = false;
        private boolean readOnly = false;
        private boolean browseable = true;
        protected String quota = null;
        private List<String> proxies = new ArrayList<>();
        protected Supplier<File> directory;

        Builder(String name) {
            if (name.indexOf('/') != -1 || name.indexOf('\\') != -1 || name.contains(".."))
                throw new IllegalArgumentException("Invalid repository name: " + name);
            this.name = name;
            this.directory = () -> new File("./repositories/" + name);
        }

        protected DiskQuota getQuota() {
            return this.quota == null ?
                DiskQuota.unlimited() :
                this.quota.charAt(this.quota.length() - 1) == '%' ?
                    DiskQuota.ofPercentage(directory.get(), this.quota) :
                    DiskQuota.of(this.quota);
        }

        @Override
        public IRepository build() {
            return new Repository(
                this.name,
                this.directory.get(),
                this.prefixes,
                this.hidden,
                this.readOnly,
                this.browseable,
                getQuota(),
                this.proxies
            );
        }

        @Override
        public Builder prefix(String... values) {
            for (String value : values)
                this.prefixes.add(value);
            return this;
        }

        @Override
        public Builder hidden(boolean value) {
            this.hidden = value;
            return this;
        }

        @Override
        public Builder readOnly(boolean value) {
            this.readOnly = value;
            return this;
        }

        @Override
        public Builder browseable(boolean value) {
            this.browseable = value;
            return this;
        }

        @Override
        public Builder quota(String value) {
            this.quota = value == null || value.isEmpty() ? null : value;
            return this;
        }

        @Override
        public Builder proxy(String... values) {
            for (String value : values)
                this.proxies.add(value);
            return this;
        }

        @Override
        public Builder dir(File value) {
            this.directory = () -> value;
            return this;
        }

        @Override
        public Builder baseDir(File value) {
            this.directory = () -> new File(value, this.name);
            return this;
        }
    }

}
