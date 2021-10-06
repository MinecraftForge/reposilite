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

package org.panda_lang.reposilite.auth;

import org.apache.http.HttpStatus;
import org.jetbrains.annotations.Nullable;
import org.panda_lang.reposilite.Reposilite;
import org.panda_lang.reposilite.ReposiliteConfiguration;
import org.panda_lang.reposilite.config.Configuration;
import org.panda_lang.reposilite.console.Console;
import org.panda_lang.reposilite.repository.RepositoryService;
import org.panda_lang.utilities.commons.collection.Pair;
import org.panda_lang.utilities.commons.function.Result;

import io.javalin.Javalin;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public final class AuthService implements ReposiliteConfiguration {
    private final Authenticator auth;
    Authenticator getAuthenticator() { return this.auth; }

    public AuthService(File workingDirectory, RepositoryService repositoryService) {
        this.auth = new Authenticator(workingDirectory, repositoryService);
    }

    public void register(Configuration configuration, Javalin javalin) {
        javalin.after("/*", ctx -> {
            if (ctx.status() == HttpStatus.SC_UNAUTHORIZED) {
                ctx.header("www-authenticate", "Basic realm=\"Reposilite\", charset=\"UTF-8\"");
            }
        });
    }

    @Override
    public void configure(Reposilite reposilite) {
        TokenService tokenService = this.auth.getTokenService();

        try {
            tokenService.loadTokens();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Console console = reposilite.getConsole();
        console.registerCommand(new ChAliasCommand(tokenService));
        console.registerCommand(new ChmodCommand(tokenService));
        console.registerCommand(new KeygenCommand(tokenService));
        console.registerCommand(new RevokeCommand(tokenService));
        console.registerCommand(new TokensCommand(tokenService));
    }

    public Result<Session, String> authByUri(Map<String, String> header, String uri) {
        return this.auth.authByUri(header, uri);
    }

    public Result<Session, String> authByHeader(Map<String, String> header) {
        return this.auth.authByHeader(header);
    }
    public Result<Session, String> authByCredentials(@Nullable String credentials) {
        return this.auth.authByCredentials(credentials);
    }

    public Pair<String, Token> createToken(String path, String alias, String permissions) {
        return this.auth.getTokenService().createToken(path, alias, permissions);
    }

    public Pair<String, Token> createToken(String path, String alias, String permissions, String token) {
        return this.auth.getTokenService().createToken(path, alias, permissions, token);
    }
}
