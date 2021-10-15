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

import org.panda_lang.reposilite.auth.IAuthManager;
import org.panda_lang.reposilite.config.Configuration;
import org.panda_lang.reposilite.config.ConfigurationLoader;
import org.panda_lang.reposilite.console.Console;
import org.panda_lang.reposilite.console.ConsoleConfiguration;
import org.panda_lang.reposilite.error.FailureService;
import org.panda_lang.reposilite.repository.IRepository;
import org.panda_lang.reposilite.repository.IRepositoryManager;
import org.panda_lang.reposilite.stats.StatsConfiguration;
import org.panda_lang.reposilite.stats.StatsService;
import org.panda_lang.reposilite.utils.RunUtils;
import org.panda_lang.reposilite.utils.TimeUtils;
import org.panda_lang.utilities.commons.ValidationUtils;
import org.panda_lang.utilities.commons.function.ThrowingRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

public final class Reposilite {

    private static final Logger LOGGER = LoggerFactory.getLogger("Reposilite");

    private final AtomicBoolean alive;
    private final ExecutorService ioService;
    private final ScheduledExecutorService retryService;
    private final File configurationFile;
    private final File workingDirectory;
    private final boolean testEnvEnabled;

    private final Configuration config;
    private final IRepositoryManager repoManager;
    private final IAuthManager authManager;

    private final ReposiliteExecutor executor;
    private final FailureService failureService;
    private final StatsService statsService;
    private final ReposiliteHttpServer reactiveHttpServer;
    private final Console console;
    private final Thread shutdownHook;
    private long uptime;

    Reposilite(String configurationFile, String workingDirectory, boolean testEnv) {
        ValidationUtils.notNull(configurationFile, "Configuration file cannot be null. To use default configuration file, provide empty string");
        ValidationUtils.notNull(workingDirectory, "Working directory cannot be null. To use default working directory, provide empty string");

        this.alive = new AtomicBoolean(false);
        this.ioService = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 1, TimeUnit.SECONDS, new SynchronousQueue<>());
        this.retryService = Executors.newSingleThreadScheduledExecutor();
        this.configurationFile = new File(configurationFile);
        this.workingDirectory = new File(workingDirectory);
        this.testEnvEnabled = testEnv;

        this.config = ConfigurationLoader.tryLoad(configurationFile, workingDirectory);
        this.failureService = new FailureService();
        this.executor = new ReposiliteExecutor(testEnvEnabled, failureService);

        this.statsService = new StatsService(workingDirectory, failureService, ioService, retryService);

        this.repoManager = buildRepoManager(config, new File(this.workingDirectory, "repositories"), this.ioService, this.retryService, this.failureService::throwException);
        this.authManager = buildAuthManager(config, this.workingDirectory, this.repoManager);

        this.reactiveHttpServer = new ReposiliteHttpServer(this);
        this.console = new Console(System.in, failureService);
        this.shutdownHook = new Thread(RunUtils.ofChecked(failureService, this::shutdown));
    }

    private static IRepositoryManager buildRepoManager(Configuration config, File dir, ExecutorService exec, ScheduledExecutorService sched, BiConsumer<String, Exception> error) {
        IRepositoryManager.Builder builder = IRepositoryManager.builder()
            .dir(dir)
            .quota(config.diskQuota)
            .executor(exec)
            .scheduled(sched)
            .error(error)
            ;

        config.repositories.forEach((name, repc) -> {
            IRepository.Builder repo = builder.repo(name)
                .hidden(repc.hidden)
                .readOnly(!repc.allowUploads)
                .browseable(repc.browseable)
                .delegate(repc.delegate)
                .quota(repc.diskQuota);

            if (repc.prefixes != null)
                repc.prefixes.forEach(repo::prefix);
            if (repc.proxies != null)
                repc.proxies.forEach(repo::proxy);
        });

        return builder.build();
    }

    private static IAuthManager buildAuthManager(Configuration config, File dir, IRepositoryManager repo) {
        return IAuthManager.builder()
            .dir(dir)
            .repo(repo)
            .build();
    }

    public void launch() throws Exception {
        load();
        start();
    }

    @SuppressWarnings("deprecation")
    public void load() throws Exception {
        getLogger().info("--- Environment");

        if (isTestEnvEnabled()) {
            getLogger().info("Test environment enabled");
        }

        getLogger().info("Platform: " + System.getProperty("java.version") + " (" + System.getProperty("os.name") + ")");
        getLogger().info("Configuration: " + configurationFile.getAbsolutePath());
        getLogger().info("Working directory: " + workingDirectory.getAbsolutePath());
        getLogger().info("");

        this.alive.set(true);
        Runtime.getRuntime().addShutdownHook(this.shutdownHook);

        getLogger().info("");
        this.repoManager.load();
        this.authManager.load();
        getLogger().info("");

        getLogger().info("--- Loading domain configurations");
        this.authManager.getCommands().configure(this);
        this.repoManager.getCommands().configure(this);
        new ConsoleConfiguration().configure(this);
        new StatsConfiguration().configure(this);
    }

    public void start() throws Exception {
        getLogger().info("Binding server at " + config.hostname + "::" + config.port);

        CountDownLatch latch = new CountDownLatch(1);
        this.uptime = System.currentTimeMillis();

        reactiveHttpServer.start(config, () -> {
            getLogger().info("Done (" + TimeUtils.format(TimeUtils.getUptime(uptime)) + "s)!");

            schedule(() -> {
                console.defaultExecute("help");

                getLogger().info("Collecting status metrics...");
                console.defaultExecute("status");

                // disable console daemon in tests due to issues with coverage and interrupt method call
                // https://github.com/jacoco/jacoco/issues/1066
                if (!isTestEnvEnabled()) {
                    console.hook();
                }
            });

            latch.countDown();
        });

        latch.await();
        executor.await(() -> getLogger().info("Bye! Uptime: " + TimeUtils.format(TimeUtils.getUptime(uptime) / 60) + "min"));
    }

    public synchronized void forceShutdown() throws Exception {
        Runtime.getRuntime().removeShutdownHook(shutdownHook);
        shutdown();
    }

    public synchronized void shutdown() throws Exception {
        if (!alive.get()) {
            return;
        }

        this.alive.set(false);
        getLogger().info("Shutting down " + config.hostname  + "::" + config.port + " ...");

        reactiveHttpServer.stop();
        statsService.saveStats();
        ioService.shutdownNow();
        retryService.shutdownNow();
        console.stop();
        executor.stop();
    }

    public void schedule(ThrowingRunnable<?> runnable) {
        executor.schedule(runnable);
    }

    public boolean isTestEnvEnabled() {
        return testEnvEnabled;
    }

    public long getUptime() {
        return System.currentTimeMillis() - uptime;
    }

    public ReposiliteHttpServer getHttpServer() {
        return reactiveHttpServer;
    }

    public StatsService getStatsService() {
        return statsService;
    }

    public Configuration getConfiguration() {
        return config;
    }

    public FailureService getFailureService() {
        return failureService;
    }

    public Console getConsole() {
        return console;
    }

    public ReposiliteExecutor getExecutor() {
        return executor;
    }

    public ScheduledExecutorService getRetryService() {
        return retryService;
    }

    public ExecutorService getIoService() {
        return ioService;
    }

    public File getWorkingDirectory() {
        return workingDirectory;
    }

    public IAuthManager getAuth() {
        return this.authManager;
    }

    public IRepositoryManager getRepos() {
        return this.repoManager;
    }

    public static Logger getLogger() {
        return LOGGER;
    }
}
