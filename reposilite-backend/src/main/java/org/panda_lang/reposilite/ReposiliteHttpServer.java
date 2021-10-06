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

package org.panda_lang.reposilite;

import io.javalin.Javalin;
import io.javalin.core.JavalinConfig;
import io.javalin.core.JavalinServer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.panda_lang.reposilite.config.Configuration;
import org.panda_lang.reposilite.console.CliController;
import org.panda_lang.reposilite.console.RemoteExecutionEndpoint;
import org.panda_lang.reposilite.error.FailureHandler;
import org.panda_lang.reposilite.repository.DeployEndpoint;
import org.panda_lang.reposilite.repository.LookupApiEndpoint;
import org.panda_lang.reposilite.repository.LookupController;
import org.panda_lang.reposilite.resource.FrontendHandler;
import org.panda_lang.utilities.commons.function.Option;

public final class ReposiliteHttpServer {

    private final Reposilite reposilite;
    private Javalin javalin;

    ReposiliteHttpServer(Reposilite reposilite) {
        this.reposilite = reposilite;
    }

    void start(Configuration configuration, Runnable onStart) {
        DeployEndpoint deployEndpoint = new DeployEndpoint(reposilite.getContextFactory(), reposilite.getDeployService());

        LookupController lookupController = new LookupController(
                configuration.proxied.size() > 0,
                reposilite.getContextFactory(),
                reposilite.getFrontendService(),
                reposilite.getLookupService(),
                reposilite.getProxyService(),
                reposilite.getFailureService());

        this.javalin = create(configuration)
                .before(ctx -> reposilite.getStatsService().record(ctx.req.getRequestURI()))
                .get("/js/app.js", new FrontendHandler(reposilite));

        reposilite.getAuthService().register(configuration, this.javalin);

        if (configuration.apiEnabled) {
            LookupApiEndpoint lookupApiEndpoint = new LookupApiEndpoint(
                    configuration.rewritePathsEnabled,
                    configuration.apiRequiresAuth,
                    reposilite.getContextFactory(),
                    reposilite.getRepositoryAuthenticator(),
                    reposilite.getRepositoryService());


            CliController cliController = new CliController(
                    reposilite.getContextFactory(),
                    reposilite.getExecutor(),
                    reposilite.getAuthService(),
                    reposilite.getConsole());

            this.javalin
                .post("/api/execute", new RemoteExecutionEndpoint(reposilite.getAuthService(), reposilite.getContextFactory(), reposilite.getConsole()))
                .ws("/api/cli", cliController)
                .get("/api", lookupApiEndpoint)
                .get("/api/*", lookupApiEndpoint);
        }


        this.javalin
            .get("/*", lookupController)
            .head("/*", lookupController)
            .put("/*", deployEndpoint)
            .post("/*", deployEndpoint)
            .exception(Exception.class, new FailureHandler(reposilite.getFailureService()));

        javalin.start(configuration.hostname, configuration.port);
        onStart.run();
    }

    void stop() {
        getJavalin().peek(Javalin::stop);
    }

    private Javalin create(Configuration configuration) {
        return Javalin.create(config -> configure(configuration, config));
    }

    private void configure(Configuration configuration, JavalinConfig config) {
        Server server = new Server();

        if (configuration.sslEnabled) {
            Reposilite.getLogger().info("Enabling SSL connector at ::" + configuration.sslPort);

            SslContextFactory sslContextFactory = new SslContextFactory.Server();
            sslContextFactory.setKeyStorePath(configuration.keyStorePath.replace("${WORKING_DIRECTORY}", reposilite.getWorkingDirectory().getAbsolutePath()));
            sslContextFactory.setKeyStorePassword(configuration.keyStorePassword);

            ServerConnector sslConnector = new ServerConnector(server, sslContextFactory);
            sslConnector.setPort(configuration.sslPort);
            server.addConnector(sslConnector);

            if (!configuration.enforceSsl) {
                ServerConnector standardConnector = new ServerConnector(server);
                standardConnector.setPort(configuration.port);
                server.addConnector(standardConnector);
            }
        }

        config.enforceSsl = configuration.enforceSsl;
        config.enableCorsForAllOrigins();
        config.showJavalinBanner = false;

        if (configuration.debugEnabled) {
            // config.requestCacheSize = FilesUtils.displaySizeToBytesCount(System.getProperty("reposilite.requestCacheSize", "8MB"));
            // Reposilite.getLogger().debug("requestCacheSize set to " + config.requestCacheSize + " bytes");
            Reposilite.getLogger().info("Debug enabled");
            config.enableDevLogging();
        }

        config.server(() -> server);
    }

    public boolean isAlive() {
        return getJavalin()
                .map(Javalin::server)
                .map(JavalinServer::server)
                .map(Server::isStarted)
                .orElseGet(false);
    }

    public Option<Javalin> getJavalin() {
        return Option.of(javalin);
    }

}