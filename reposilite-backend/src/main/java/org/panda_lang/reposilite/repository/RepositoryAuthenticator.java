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
import org.panda_lang.reposilite.ReposiliteUtils;
import org.panda_lang.reposilite.auth.Authenticator;
import org.panda_lang.reposilite.auth.Session;
import org.panda_lang.reposilite.error.ErrorDto;
import org.panda_lang.reposilite.error.ResponseUtils;
import org.panda_lang.utilities.commons.StringUtils;
import org.panda_lang.utilities.commons.collection.Pair;
import org.panda_lang.utilities.commons.function.Option;
import org.panda_lang.utilities.commons.function.Result;

import java.util.Map;
import java.util.stream.Collectors;

public final class RepositoryAuthenticator {

    private final boolean rewritePathsEnabled;
    private final Authenticator authenticator;
    private final RepositoryService repositoryService;

    public RepositoryAuthenticator(boolean rewritePathsEnabled, Authenticator authenticator, RepositoryService repositoryService) {
        this.rewritePathsEnabled = rewritePathsEnabled;
        this.authenticator = authenticator;
        this.repositoryService = repositoryService;
    }

    public Result<Pair<String[], Repository>, ErrorDto> authDefaultRepository(Map<String, String> headers, String uri) {
        return ReposiliteUtils.normalizeUri(rewritePathsEnabled, repositoryService, uri)
                .map(normalizedUri -> authRepository(headers, normalizedUri))
                .orElseGet(() -> ResponseUtils.error(HttpStatus.SC_BAD_REQUEST, "Invalid GAV path"));
    }

    public Result<Pair<String[], Repository>, ErrorDto> authRepository(Map<String, String> headers, String normalizedUri) {
        String[] path = StringUtils.split(normalizedUri, "/");
        String repositoryName = path[0];

        if (StringUtils.isEmpty(repositoryName)) {
            return ResponseUtils.error(HttpStatus.SC_NON_AUTHORITATIVE_INFORMATION, "Unsupported request");
        }

        Repository repository = repositoryService.getRepository(repositoryName);

        if (repository == null) {
            return ResponseUtils.error(HttpStatus.SC_NON_AUTHORITATIVE_INFORMATION, "Repository " + repositoryName  + " not found");
        }

        // auth hidden repositories
        if (repository.isHidden()) {
            Result<Session, String> authResult = authenticator.authByUri(headers, normalizedUri);

            if (authResult.isErr()) {
                return ResponseUtils.error(HttpStatus.SC_UNAUTHORIZED, "Unauthorized request");
            }
        }

        return Result.ok(new Pair<>(path, repository));
    }

    FileListDto findAvailableRepositories(Map<String, String> headers) {
        Option<Session> session = authenticator.authByHeader(headers).toOption();

        return new FileListDto(repositoryService.getRepositories().stream()
                .filter(repository -> repository.isPublic() || session.map(value -> value.getRepositoryNames().contains(repository.getName())).orElseGet(false))
                .map(Repository::getFile)
                .map(FileDetailsDto::of)
                .collect(Collectors.toList()));
    }

    public Authenticator getAuthenticator() {
        return this.authenticator;
    }

}
