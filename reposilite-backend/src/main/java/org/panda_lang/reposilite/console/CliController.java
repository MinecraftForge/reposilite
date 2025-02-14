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

package org.panda_lang.reposilite.console;

import io.javalin.websocket.WsConfig;
import org.panda_lang.reposilite.Reposilite;
import org.panda_lang.reposilite.ReposiliteExecutor;
import org.panda_lang.reposilite.auth.IAuthManager;
import org.panda_lang.reposilite.auth.Session;
import org.panda_lang.reposilite.log.ReposiliteWriter;
import org.panda_lang.utilities.commons.StringUtils;
import org.panda_lang.utilities.commons.function.Result;

import java.util.function.Consumer;

public final class CliController implements Consumer<WsConfig> {

    private static final String AUTHORIZATION_PREFIX = "Authorization:";

    private final String forwardedIpHeader;
    private final ReposiliteExecutor reposiliteExecutor;
    private final IAuthManager auth;
    private final Console console;

    public CliController(
            String forwardedIpHeader,
            ReposiliteExecutor reposiliteExecutor,
            IAuthManager auth,
            Console console) {
        this.forwardedIpHeader = forwardedIpHeader;
        this.reposiliteExecutor = reposiliteExecutor;
        this.auth = auth;
        this.console = console;
    }

    @Override
    public void accept(WsConfig wsConfig) {
        wsConfig.onConnect(connectContext -> wsConfig.onMessage(authContext -> {
            String realIp = authContext.header(this.forwardedIpHeader);
            String address = StringUtils.isEmpty(realIp) ? authContext.session.getRemoteAddress().toString() : realIp;

            String authMessage = authContext.message();
            if (!authMessage.startsWith(AUTHORIZATION_PREFIX)) {
                Reposilite.getLogger().info("CLI | Unauthorized CLI access request from " + address + " (missing credentials)");
                connectContext.send("Unauthorized connection request");
                connectContext.session.disconnect();
                return;
            }

            String credentials = StringUtils.replaceFirst(authMessage, AUTHORIZATION_PREFIX, "");
            Result<Session, String> auth = this.auth.getSession(credentials);

            if (!auth.isOk() || !auth.get().isManager()) {
                Reposilite.getLogger().info("CLI | Unauthorized CLI access request from " + address);
                connectContext.send("Unauthorized connection request");
                connectContext.session.disconnect();
                return;
            }

            String username = auth.get().getAlias() + "@" + address;

            wsConfig.onClose(closeContext -> {
                Reposilite.getLogger().info("CLI | " + username + " closed connection");
                ReposiliteWriter.getConsumers().remove(closeContext);
            });

            ReposiliteWriter.getConsumers().put(connectContext, connectContext::send);
            Reposilite.getLogger().info("CLI | " + username + " accessed remote console");

            wsConfig.onMessage(messageContext -> {
                Reposilite.getLogger().info("CLI | " + username + "> " + messageContext.message());
                reposiliteExecutor.schedule(() -> console.defaultExecute(messageContext.message()));
            });

            for (String message : ReposiliteWriter.getCache()) {
                connectContext.send(message);
            }
        }));
    }
}
