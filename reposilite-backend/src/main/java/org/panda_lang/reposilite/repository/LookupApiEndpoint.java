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
import org.panda_lang.reposilite.Reposilite;
import org.panda_lang.reposilite.ReposiliteContext;
import org.panda_lang.reposilite.auth.IAuthManager;
import org.panda_lang.reposilite.auth.IAuthedHandler;
import org.panda_lang.reposilite.auth.Permission;
import org.panda_lang.reposilite.auth.Session;
import org.panda_lang.reposilite.error.ErrorDto;
import org.panda_lang.reposilite.error.ResponseUtils;
import org.panda_lang.reposilite.metadata.MetadataUtils;
import org.panda_lang.reposilite.utils.FilesUtils;
import org.panda_lang.utilities.commons.StringUtils;
import org.panda_lang.utilities.commons.function.Option;
import org.panda_lang.utilities.commons.function.PandaStream;
import org.panda_lang.utilities.commons.function.Result;

import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

final class LookupApiEndpoint implements IAuthedHandler {
    private final IRepositoryManager repos;
    private final IAuthManager auth;

    public LookupApiEndpoint(IRepositoryManager repos, IAuthManager auth) {
        this.repos = repos;
        this.auth = auth;
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
    @Override
    public void handle(Context ctx, ReposiliteContext context) {
        Reposilite.getLogger().info("API " + context.uri() + " from " + context.address());

        if (context.normalized() == null) {
            ResponseUtils.errorResponse(ctx, new ErrorDto(HttpStatus.SC_BAD_REQUEST, "Invalid GAV path"));
            return;
        }

        String uri = context.normalized();

        if ("/".equals(uri) || StringUtils.isEmpty(uri)) {
            Option<Session> session = auth.getSession(context.headers(), null).toOption();
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

        IRepository repo = context.repo();
        if (repo == null) {
            ResponseUtils.errorResponse(ctx, new ErrorDto(HttpStatus.SC_NOT_FOUND, "Can not find repo at: " + uri));
            return;
        }

        if (!repo.canBrowse() || repo.isHidden()) {
            Result<Session, String> auth = this.auth.getSession(context.headers(), uri);
            if (auth.isErr()) {
                ResponseUtils.errorResponse(ctx, HttpStatus.SC_UNAUTHORIZED, auth.getError());
                return;
            }

            if ((!StringUtils.isEmpty(uri) && !"/".equals(uri) && !auth.get().hasPermissionTo('/' + uri))
                || !auth.get().hasAnyPermission(Permission.READ, Permission.WRITE, Permission.MANAGER)) {
                ResponseUtils.errorResponse(ctx, HttpStatus.SC_UNAUTHORIZED, "Unauthorized request");
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

        ctx.json(new FileListDto(PandaStream.of(FilesUtils.listFiles(requestedFile))
                .map(FileDetailsDto::of)
                .transform(stream -> MetadataUtils.toSorted(stream, FileDetailsDto::getName, FileDetailsDto::isDirectory))
                .toList()));
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
