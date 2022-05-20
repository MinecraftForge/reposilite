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
import org.panda_lang.reposilite.ReposiliteContext;
import org.panda_lang.reposilite.ReposiliteContext.View;
import org.panda_lang.reposilite.auth.Permission;
import org.panda_lang.reposilite.auth.Session;
import org.panda_lang.reposilite.error.ErrorDto;
import org.panda_lang.reposilite.error.ResponseUtils;
import org.panda_lang.utilities.commons.function.Result;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.apache.http.HttpStatus.*;

final class LookupService {
    private final MetadataService metadataService;
    private final IRepositoryManager repos;
    private final ProxyService proxy;

    LookupService(
            MetadataService metadataService,
            IRepositoryManager repos,
            ProxyService proxy) {
        this.metadataService = metadataService;
        this.repos = repos;
        this.proxy = proxy;
    }

    Result<LookupResponse, ErrorDto> findFile(ReposiliteContext context) {
        String filepath = context.filepath();
        if (filepath.isEmpty())
            return ResponseUtils.error(SC_NON_AUTHORITATIVE_INFORMATION, "Unsupported request");

        if (filepath.charAt(filepath.length() - 1) != '/') {
            for (IRepository repo : context.repos()) {
                if (repo.isDirectory(context.filepath()))
                    return ResponseUtils.error(SC_MOVED_TEMPORARILY, context.uri() + '/');
            }
        }

        String[] parts = filepath.split("/");
        boolean isMeta = "maven-metadata.xml".equals(parts[parts.length - 1]);

        if (context.view() == View.EXPLICIT || context.repos().size() == 1) {
            IRepository repo = context.repos().isEmpty() ? null : context.repos().get(0);
            if (repo == null)
                return ResponseUtils.error(SC_NOT_FOUND, "Can not find repo at: " + context.uri());

            if (!repo.canContain(context.filepath()))
                return ResponseUtils.error(SC_NOT_FOUND, "Impossible artifact");

            return findFile(context, parts, isMeta, true, null, context.repos(), 0, repo);
        }

        List<IRepository> filtered = null;
        for (IRepository repo : context.repos()) {
            if (!repo.canContain(context.filepath()))
                continue;

            if (filtered == null)
                filtered = new ArrayList<>();
            filtered.add(repo);
        }

        if (filtered.isEmpty())
            return ResponseUtils.error(SC_NOT_FOUND, "Can not find repo at: " + context.uri());

        // Filter out repos the user can't see
        List<IRepository> visibleRepos = new ArrayList<>();
        for (IRepository repo : filtered) {
            if (!repo.isHidden()) {
                visibleRepos.add(repo);
            } else {
                Result<Session, String> auth = context.session('/' + repo.getName() + '/' + context.filepath());
                if (auth.isOk() && auth.get().hasAnyPermission(Permission.READ, Permission.WRITE, Permission.MANAGER)) {
                    visibleRepos.add(repo);
                }
            }
        }

        if (visibleRepos.isEmpty()) {
            // The user can't see any of repos that may contain the file, so tell them the file doesn't exist
            return ResponseUtils.error(SC_NOT_FOUND, "File not found");
        }

        // TODO: File hashes
        if (filtered.size() > 1 && isMeta) {
            byte[] meta = metadataService.mergeMetadata(context.sanitized(), context.filepath(), visibleRepos);
            if (meta != null)
                return Result.ok(new LookupResponse("text/xml", meta));
        }

        return findFile(context, parts, isMeta, false, visibleRepos.size() > 1 ? new HashSet<>() : null, visibleRepos, 0, null);
    }

    private Result<LookupResponse, ErrorDto> findFile(ReposiliteContext context, String[] parts, boolean isMeta, boolean isExplicit, Set<String> visited, List<IRepository> repos, int index,
            IRepository repo) {
        if (repo == null)
            repo = repos.get(index);

        if (repo.isHidden()) {
            Result<Session, String> auth = context.session('/' + repo.getName() + '/' + context.filepath());
            if (auth.isErr() || !auth.get().hasAnyPermission(Permission.READ, Permission.WRITE, Permission.MANAGER)) {
                if (!isExplicit)
                    return ResponseUtils.error(SC_NOT_FOUND, "File not found");
                return ResponseUtils.error(SC_UNAUTHORIZED, "Unauthorized request");
            }
        }

        File file = repo.getFile(context.filepath());

        // TODO: Hash file extensions
        if (!file.exists()) {
            if (isMeta) {
                if (parts.length == 1) // Must at least have a group in order to potentially exist
                    return ResponseUtils.error(SC_NOT_FOUND, "Missing group identifier");
                return findProxy(context, parts, isMeta, isExplicit, visited, repos, index, repo);
            }

            // Must at least have 4 segments: group/artifact/version/file
            if (parts.length < 4)
                return ResponseUtils.error(SC_NOT_FOUND, "Invalid artifact path");
            return findProxy(context, parts, isMeta, isExplicit, visited, repos, index, repo);
        }

        if (file.isDirectory())
            return ResponseUtils.error(SC_OK, "Directory access"); // TODO: Better way to say 'serve the frontend'

        FileDetailsDto fileDetails = FileDetailsDto.of(file);

        if (!context.method().equals("HEAD"))
            context.result(outputStream -> FileUtils.copyFile(file, outputStream));

        Reposilite.getLogger().debug("RESOLVED " + file.getPath() + "; mime: " + fileDetails.getContentType() + "; size: " + file.length());
        return Result.ok(new LookupResponse(fileDetails));
    }

    private Result<LookupResponse, ErrorDto> findProxy(ReposiliteContext context, String[] parts, boolean isMeta, boolean isExplicit, Set<String> visited, List<IRepository> repos, int index, IRepository repo) {
        if (repo.getDelegate() != null) {
            IRepository delegate = this.repos.getRepo(repo.getDelegate());
            if (delegate != null) {
                if  (visited == null || visited.add(delegate.getName()))
                    return findFile(context, parts, isMeta, isExplicit, visited, repos, index, delegate);
            }
        }

        if (repo.getProxies().isEmpty()) {
            if (visited != null) {
                while (index < repos.size() - 1) {
                    IRepository next = repos.get(++index);
                    if (visited.add(next.getName()))
                        return findFile(context, parts, isMeta, isExplicit, visited, repos, index, next);
                }
            }
            return ResponseUtils.error(SC_NOT_FOUND, "File not found");
        }

        return proxy.findProxied(context, repo, parts);
    }
}
