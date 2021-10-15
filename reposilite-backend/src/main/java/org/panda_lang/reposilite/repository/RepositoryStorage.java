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
import org.panda_lang.reposilite.Reposilite;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

final class RepositoryStorage {
    private static final long RETRY_WRITE_TIME = 2000L;

    private final IRepositoryManager manager;
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduled;

    RepositoryStorage(IRepositoryManager manager, ExecutorService executor, ScheduledExecutorService scheduled) {
        this.manager = manager;
        this.executor = executor;
        this.scheduled = scheduled;
    }

    void load() {
        Reposilite.getLogger().info("--- Loading repositories");

        for (IRepository repo : manager.getRepos()) {
            repo.load();
            Reposilite.getLogger().info("+ " + repo.getName() + (repo.isHidden() ? " (hidden)" : "") + " " + repo.getQuota());
        }

        Reposilite.getLogger().info(manager.getRepos().size() + " repositories have been found " + manager.getQuota());
    }

    CompletableFuture<File> storeFile(InputStream source, IRepository repo, String path) throws Exception {
        return storeFile(new CompletableFuture<>(), source, repo, path);
    }

    private CompletableFuture<File> storeFile(CompletableFuture<File> task, InputStream source, IRepository repo, String path) throws IOException {
        File targetFile = repo.getFile(path);

        if (targetFile.isDirectory()) {
            throw new IOException("Cannot lock directory");
        }

        File lockedFile = new File(targetFile.getAbsolutePath() + ".lock");

        if (lockedFile.exists()) {
            scheduled.schedule(() -> {
                executor.submit(() -> {
                    storeFile(task, source, repo, path);
                    return null;
                });
            }, RETRY_WRITE_TIME, TimeUnit.MILLISECONDS);

            return task;
        }

        FileUtils.forceMkdirParent(targetFile);

        if (targetFile.exists()) {
            Files.move(targetFile.toPath(), lockedFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }

        Files.copy(source, lockedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        //TODO: This needs to subtract the length of the overwritten file. So that repeated deploys don't eat the quota
        ((DiskQuota)repo.getQuota()).allocate(lockedFile.length());
        Files.move(lockedFile.toPath(), targetFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);

        task.complete(targetFile);
        return task;
    }
}
