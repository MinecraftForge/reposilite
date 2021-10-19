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

import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.OpenApi;
import io.javalin.plugin.openapi.annotations.OpenApiContent;
import io.javalin.plugin.openapi.annotations.OpenApiParam;
import io.javalin.plugin.openapi.annotations.OpenApiResponse;
import org.apache.http.HttpStatus;
import org.panda_lang.reposilite.ReposiliteContext;
import org.panda_lang.reposilite.ReposiliteContext.View;
import org.panda_lang.reposilite.auth.IAuthedHandler;
import org.panda_lang.reposilite.auth.Permission;
import org.panda_lang.reposilite.auth.Session;
import org.panda_lang.reposilite.error.ErrorDto;
import org.panda_lang.reposilite.error.ResponseUtils;
import org.panda_lang.reposilite.metadata.MetadataUtils;
import org.panda_lang.reposilite.utils.FilesUtils;
import org.panda_lang.utilities.commons.StringUtils;
import org.panda_lang.utilities.commons.function.Option;
import org.panda_lang.utilities.commons.function.Result;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

final class LookupApiEndpoint implements IAuthedHandler {
    private final IRepositoryManager repos;

    public LookupApiEndpoint(IRepositoryManager repos) {
        this.repos = repos;
    }

    @OpenApi(
        operationId = "repositoryApi",
        summary = "Browse the contents of repositories using API",
        description = "Get details about the requested file as JSON response",
        tags = { "Repository" },
        pathParams = {
            @OpenApiParam(name = "*", description = "Artifact path qualifier", required = true, allowEmptyValue = true),
        },
        responses = {
            @OpenApiResponse(
                status = "200",
                description = "Returns document (different for directory and file) that describes requested resource",
                content = {
                    @OpenApiContent(from = FileDetailsDto.class),
                    @OpenApiContent(from = FileListDto.class)
                }
            ),
            @OpenApiResponse(
                status = "401",
                description = "Returns 401 in case of unauthorized attempt of access to private repository",
                content = @OpenApiContent(from = ErrorDto.class)
            ),
            @OpenApiResponse(
                status = "404",
                description = "Returns 404 (for Maven) and frontend (for user) as a response if requested artifact is not in the repository"
            ),
        }
    )
    // TODO: Return error/file details instead of FileList if requesting path not ending in / ?
    @Override
    public void handle(Context ctx, ReposiliteContext context) {
        //Reposilite.getLogger().info("API " + context.uri() + " from " + context.address());

        String filepath = context.filepath();
        if (filepath == null) {
            ResponseUtils.errorResponse(ctx, new ErrorDto(HttpStatus.SC_BAD_REQUEST, "Invalid GAV path"));
            return;
        }

        if ("/".equals(context.sanitized()) || StringUtils.isEmpty(context.sanitized())) {
            Option<Session> session = context.session().toOption();
            List<IRepository> viewable = repos.getRepos().stream()
                .filter(repo -> (repo.canBrowse() && !repo.isHidden()) || session.map(value -> value.getRepositories().contains(repo)).orElseGet(false))
                .collect(Collectors.toList());

            // There are repos, but none that can be anonymously browsed, return unauthorized
            if (!session.isPresent() && viewable.isEmpty() && !repos.getRepos().isEmpty()) {
                ResponseUtils.errorResponse(ctx, new ErrorDto(HttpStatus.SC_UNAUTHORIZED, "Unauthorized request"));
                return;
            }

            // Even if its empty (the logged in user has no view rights) send the result
            ctx.json(new FileListDto(viewable.stream()
                .map(IRepository::getFile)
                .map(FileDetailsDto::of)
                .collect(Collectors.toList()))
            );
            return;
        }

        if (context.view() != View.EXPLICIT && context.view() != View.ALL) {
            ResponseUtils.errorResponse(ctx, new ErrorDto(HttpStatus.SC_NOT_IMPLEMENTED, "Can not browse API in merged views. Must specify a repository"));
            return;
        }

        IRepository repo = context.view() == View.ALL || context.repos().isEmpty() ? null : context.repos().get(0);
        if (repo == null) {
            ResponseUtils.errorResponse(ctx, new ErrorDto(HttpStatus.SC_NOT_FOUND, "Can not find repo at: " + context.sanitized()));
            return;
        }

        if (!repo.canBrowse() || repo.isHidden()) {
            Result<Session, String> auth = context.session('/' + repo.getName() + '/' + filepath);
            if (auth.isErr() || !auth.get().hasAnyPermission(Permission.READ, Permission.WRITE, Permission.MANAGER)) {
                ResponseUtils.errorResponse(ctx, HttpStatus.SC_UNAUTHORIZED, auth.isErr() ? auth.getError() : "Unauthorized request");
                return;
            }
        }

        File requestedFile = repo.getFile(context.filepath());
        Optional<FileDetailsDto> latest = findLatest(requestedFile);

        if (latest.isPresent()) {
            ctx.json(latest.get());
            return;
        }

        if (!requestedFile.exists()) {
            ResponseUtils.errorResponse(ctx, HttpStatus.SC_NOT_FOUND, "File not found");
            return;
        }

        if (requestedFile.isFile()) {
            ctx.json(FileDetailsDto.of(requestedFile));
            return;
        }

        ctx.json(new FileListDto(Arrays.stream(FilesUtils.listFiles(requestedFile))
            .sorted((a,b) -> {
                if (a.isDirectory() != b.isDirectory())
                    return a.isDirectory() ? -1 : 1;
                return a.getName().compareTo(b.getName());
            })
            .map(FileDetailsDto::of)
            .collect(Collectors.toList())
        ));
    }


    //TODO: Deprecate this API?
    private Optional<FileDetailsDto> findLatest(File requestedFile) {
        if (requestedFile.getName().equals("latest")) {
            File parent = requestedFile.getParentFile();

            if (parent != null && parent.exists()) {
                File[] files = MetadataUtils.toSortedVersions(parent);
                if (files.length > 0)
                    return Optional.of(FileDetailsDto.of(files[0]));
            }
        }

        return Optional.empty();
    }
}
