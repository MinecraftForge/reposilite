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
import org.apache.http.HttpStatus;
import org.panda_lang.reposilite.Reposilite;
import org.panda_lang.reposilite.ReposiliteContext;
import org.panda_lang.reposilite.auth.IAuthManager;
import org.panda_lang.reposilite.auth.Permission;
import org.panda_lang.reposilite.auth.Session;
import org.panda_lang.reposilite.error.ErrorDto;
import org.panda_lang.reposilite.error.ResponseUtils;
import org.panda_lang.reposilite.metadata.MetadataService;
import org.panda_lang.reposilite.metadata.MetadataUtils;
import org.panda_lang.utilities.commons.function.Result;

import java.io.File;

final class LookupService {
    private final MetadataService metadataService;
    private final IRepositoryManager repos;
    private final IAuthManager auth;
    private final ProxyService proxy;

    LookupService(
            MetadataService metadataService,
            IRepositoryManager repos,
            IAuthManager auth,
            ProxyService proxy) {
        this.metadataService = metadataService;
        this.repos = repos;
        this.auth = auth;
        this.proxy = proxy;
    }

    Result<LookupResponse, ErrorDto> findFile(ReposiliteContext context) {
        String uri = context.uri();

        if ("/".equals(uri))
            return ResponseUtils.error(HttpStatus.SC_NON_AUTHORITATIVE_INFORMATION, "Unsupported request");

        IRepository repo = context.repo();
        if (repo == null)
            return ResponseUtils.error(HttpStatus.SC_NOT_FOUND, "Can not find repo at: " + context.normalized());

        if (repo.isHidden()) {
            Result<Session, String> auth = this.auth.getSession(context.headers(), uri);
            if (auth.isErr() || !auth.get().hasPermissionTo('/' + uri) || !auth.get().hasAnyPermission(Permission.READ, Permission.WRITE, Permission.MANAGER))
                return ResponseUtils.error(HttpStatus.SC_UNAUTHORIZED, "Unauthorized request");
        }

        String[] requestPath = context.filepath().split("/");

        // discard invalid requests (less than 'group/(artifact OR metadata)')
        /*
         * TODO: Don't support meta for non-artifacts
         * https://maven.apache.org/ref/3.3.3/maven-repository-metadata/
         * This specifies three locations for metadata:
         *   1) Unversioned artifact: /group/artifact/maven-metadata.xml
         *     This list all versions for this artifact
         *   2) Snapshot artifact: /group/artifact/version-SNAPSHOT/maven-metadata.xml
         *     Lists all versions of the snapshot,
         *   3) Maven Plugin Group: /goup/maven-metadata.xml
         *     This I dont think we can generate... So request from the proxy, or 404
         */
        if (requestPath.length < 2) {
            return ResponseUtils.error(HttpStatus.SC_NON_AUTHORITATIVE_INFORMATION, "Missing artifact identifier");
        }

        String requestedFileName = requestPath[requestPath.length - 1];

        /*
        Optional<Artifact> testArtifact = repository.find(requestPath);
        // This doesn't work for requesting /latest in proxied files, but I don't give a shit...
        if (!testArtifact.isPresent() && this.proxiedStorageRepo != null && !this.proxiedStorageRepo.isEmpty()) {
            Repository proxy = this.repositoryService.getRepository(this.proxiedStorageRepo);
            if (proxy != null) {
                testArtifact = proxy.find(requestPath);
                if (testArtifact.isPresent()) {
                    repository = proxy;
                }
            }
        }
        */

        if (requestPath.length > 3 && requestedFileName.equals("maven-metadata.xml")) {
            Result<String, String> meta = metadataService.generateMetadata(repo, requestPath);
            if (meta.isErr())
                return findProxy(context);
            else
                return Result.ok(new LookupResponse("text/xml", meta.get()));
        }

        // resolve requests for latest version of artifact
        if (requestedFileName.equalsIgnoreCase("latest")) {
            File requestDirectory = repo.getFile(requestPath).getParentFile();
            File[] versions = MetadataUtils.toSortedVersions(requestDirectory);
            File version = versions == null || versions.length == 0 ? null : versions[0];

            if (version == null) {
                return ResponseUtils.error(HttpStatus.SC_NOT_FOUND, "Latest version not found");
            }

            return Result.ok(new LookupResponse("text/plain", version.getName()));
        }

        /* Why the fuck does he do this? Maven clients should request the metadata and then request the specific version they want. This just interfears with that.
        // resolve snapshot requests
        if (requestedFileName.contains("-SNAPSHOT")) {
            repo.resolveSnapshot(repo, requestPath);
            // update requested file name in case of snapshot request
            requestedFileName = requestPath[requestPath.length - 1];
        }
        */

        File file = repo.getFile(requestPath);

        if (!file.exists()) {
            if ((requestPath.length == 3 && !"maven-metadata.xml".equals(requestPath[2])) && requestPath.length < 4)
                return ResponseUtils.error(HttpStatus.SC_NOT_FOUND, "Invalid artifact path");
            else
                return findProxy(context);
        }

        if (file.isDirectory()) {
            return ResponseUtils.error(HttpStatus.SC_NON_AUTHORITATIVE_INFORMATION, "Directory access");
        }

        FileDetailsDto fileDetails = FileDetailsDto.of(file);

        if (!context.method().equals("HEAD")) {
            context.result(outputStream -> FileUtils.copyFile(file, outputStream));
        }

        Reposilite.getLogger().debug("RESOLVED " + file.getPath() + "; mime: " + fileDetails.getContentType() + "; size: " + file.length());
        return Result.ok(new LookupResponse(fileDetails));
    }

    private Result<LookupResponse, ErrorDto> findProxy(ReposiliteContext context) {

        /*
        private void handleProxied(Context ctx, ReposiliteContext context, Result<CompletableFuture<Result<LookupResponse, ErrorDto>>, ErrorDto> response) {
            response
                .map(task -> task.thenAccept(result -> handleResult(ctx, context, result)))
                .peek(ctx::result)
                .onError(error -> handleError(ctx, error));
        }
        */
        return proxy.findProxied(context);
    }

}
