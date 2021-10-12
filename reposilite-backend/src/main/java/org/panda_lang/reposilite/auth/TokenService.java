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

import org.panda_lang.reposilite.Reposilite;
import org.panda_lang.reposilite.ReposiliteConstants;
import org.panda_lang.reposilite.utils.FilesUtils;
import org.panda_lang.reposilite.utils.YamlUtils;
import org.panda_lang.utilities.commons.function.Option;
import org.panda_lang.utilities.commons.function.Result;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

final class TokenService {
    public static final BCryptPasswordEncoder B_CRYPT_TOKENS_ENCODER = new BCryptPasswordEncoder();

    private final Map<String, Token> tokens = new HashMap<>();
    private final TokenStorage database;

    TokenService(File dir) {
        this.database = new TokenStorage(this, dir);
    }

    void loadTokens() throws IOException {
        this.database.loadTokens();
    }

    void saveTokens() throws IOException {
        this.database.saveTokens();
    }

    Token createToken(String path, String alias, String permissions, String password) {
        String encoded = B_CRYPT_TOKENS_ENCODER.encode(password);
        return addToken(new Token(path, alias, permissions, encoded));
    }

    public Result<Token, String> updateToken(String alias, Consumer<Token> tokenConsumer) {
        return getToken(alias)
                .map(token -> {
                    tokenConsumer.accept(token);

                    try {
                        saveTokens();
                        return Result.<Token, String> ok(token);
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                        return Result.<Token, String> error(ioException.getMessage());
                    }
                })
                .orElseGet(() -> Result.error("Cannot find token associated with '" + alias + "' alias"));
    }

    Token addToken(Token token) {
        this.tokens.put(token.getAlias(), token);
        return token;
    }

    Token deleteToken(String alias) {
        return tokens.remove(alias);
    }

    Option<Token> getToken(String alias) {
        return Option.of(tokens.get(alias));
    }

    Collection<Token> getTokens() {
        return tokens.values();
    }

    private final class TokenStorage {
        private final TokenService tokenService;
        private final File tokensFile;

        private TokenStorage(TokenService tokenService, File workingDirectory) {
            this.tokenService = tokenService;
            this.tokensFile = new File(workingDirectory, ReposiliteConstants.TOKENS_FILE_NAME);
        }

        private void loadTokens() throws IOException {
            if (!tokensFile.exists()) {
                Reposilite.getLogger().info("Generating tokens data file...");
                FilesUtils.copyResource("/" + ReposiliteConstants.TOKENS_FILE_NAME, tokensFile);
                Reposilite.getLogger().info("Empty tokens file has been generated");
            } else {
                Reposilite.getLogger().info("Using an existing tokens data file");
            }

            TokenCollection tokenCollection = YamlUtils.load(tokensFile, TokenCollection.class);

            for (Token token : tokenCollection.tokens) {
                // Update missing default permissions of old tokens
                // ~ https://github.com/dzikoysk/reposilite/issues/233
                token.setPermissions(Option.of(token.getPermissions()).orElseGet(Permission.toString(Permission.getDefaultPermissions())));
                tokenService.addToken(token);
            }

            Reposilite.getLogger().info("Tokens: " + tokenService.getTokens().size());
        }

        private void saveTokens() throws IOException {
            TokenCollection tokenCollection = new TokenCollection();

            for (Token token : tokenService.getTokens()) {
                tokenCollection.tokens.add(token);
            }

            YamlUtils.save(tokensFile, tokenCollection);
            Reposilite.getLogger().info("Stored tokens: " + tokenCollection.tokens.size());
        }

    }
}
