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

import org.apache.http.HttpStatus;
import org.panda_lang.reposilite.Reposilite;
import org.panda_lang.reposilite.ReposiliteContext;
import org.panda_lang.reposilite.ReposiliteContext.View;
import org.panda_lang.reposilite.auth.Permission;
import org.panda_lang.reposilite.auth.Session;
import org.panda_lang.reposilite.error.ErrorDto;
import org.panda_lang.reposilite.error.ResponseUtils;
import org.panda_lang.utilities.commons.function.Result;

import java.io.File;
import java.util.concurrent.CompletableFuture;

final class DeployService {
    private final IRepositoryManager repos;
    private final MetadataService metadataService;

    public DeployService(
            IRepositoryManager repos,
            MetadataService metadataService) {
        this.repos = repos;
        this.metadataService = metadataService;
    }

    public Result<CompletableFuture<Result<FileDetailsDto, ErrorDto>>, ErrorDto> deploy(ReposiliteContext context) {
        String uri = context.filepath();
        if (uri == null) {
            return ResponseUtils.error(HttpStatus.SC_BAD_REQUEST, "Invalid GAV path");
        }

        IRepository repo = null;
        if (!context.repos().isEmpty()) {
            if (context.view() == View.EXPLICIT) {
                repo = context.repos().get(0);
            } else if (context.view() != View.RELEASES && context.view() != View.SNAPSHOTS) {
                return ResponseUtils.error(HttpStatus.SC_METHOD_NOT_ALLOWED, "Deploying to unknown endpoint. Must be a explicit repo, '/releases', or '/snapshots'");
            } else {
                for (IRepository r : context.repos()) {
                    if (r.canContain(uri)) {
                        repo = r;
                        break;
                    }
                }
            }
        }
        if (repo == null) {
            return ResponseUtils.error(HttpStatus.SC_NOT_FOUND, "Can not find repo at: " + context.uri());
        }

        if (repo.isReadOnly()) {
            return ResponseUtils.error(HttpStatus.SC_METHOD_NOT_ALLOWED, "Artifact deployment is disabled");
        }

        if (!repo.canContain(context.filepath())) {
            return ResponseUtils.error(HttpStatus.SC_METHOD_NOT_ALLOWED, "Repository " + repo.getName() + " can not contain: " + context.filepath());
        }

        Result<Session, String> authResult = context.session(repo.getName() + '/' + uri);

        if (authResult.isErr()) {
            return ResponseUtils.error(HttpStatus.SC_UNAUTHORIZED, authResult.getError());
        }

        Session session = authResult.get();

        if (!session.hasPermission(Permission.WRITE) && !session.isManager()) {
            return ResponseUtils.error(HttpStatus.SC_UNAUTHORIZED, "Cannot deploy artifact without write permission");
        }

        if (!repo.getQuota().hasSpace()) {
            return ResponseUtils.error(HttpStatus.SC_INSUFFICIENT_STORAGE, "Out of disk space");
        }

        File file = repo.getFile(context.filepath());
        FileDetailsDto fileDetails = FileDetailsDto.of(file);

        File metadataFile = new File(file.getParentFile(), "maven-metadata.xml");
        metadataService.clearMetadata(metadataFile);

        Reposilite.getLogger().info("DEPLOY " + authResult.get().getAlias() + " successfully deployed " + file + " from " + context.address());

        //TODO: Remove this when we remove metadata service
        if (file.getName().contains("maven-metadata")) {
            return Result.ok(CompletableFuture.completedFuture(Result.ok(fileDetails)));
        }

        //TODO: Design a better API for this, so we don't have to cast to internal types.
        CompletableFuture<Result<FileDetailsDto, ErrorDto>> task = ((RepositoryManager)repos).storeFile(
            uri,
            repo,
            context.filepath(),
            context::input,
            () -> fileDetails,
            exception -> new ErrorDto(HttpStatus.SC_INTERNAL_SERVER_ERROR, "Failed to upload artifact"));

        return Result.ok(task);
    }

}
