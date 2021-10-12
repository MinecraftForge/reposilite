package org.panda_lang.reposilite.repository;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.panda_lang.reposilite.ReposiliteConfiguration;
import org.panda_lang.reposilite.metadata.MetadataService;
import org.panda_lang.utilities.commons.function.Result;
import org.panda_lang.utilities.commons.function.ThrowingRunnable;
import org.panda_lang.utilities.commons.function.ThrowingSupplier;

import io.javalin.http.Handler;

class RepositoryManager implements IRepositoryManager {
    private final DiskQuota quota;
    private final File root;
    private final ExecutorService executor;
    private final ScheduledExecutorService scheduled;
    private final BiConsumer<String, Exception> errorHandler;
    private final Map<String, IRepository> repos;
    private final Collection<IRepository> repoView;
    private final RepositoryStorage storage;
    private final DeployService deployService;
    private final MetadataService metadataService;

    private RepositoryManager(DiskQuota quota, File root, ExecutorService executor, ScheduledExecutorService scheduled, BiConsumer<String, Exception> errorHandler, Map<String, IRepository> repos) {
        this.quota = quota;
        this.root = root;
        this.executor = executor;
        this.scheduled = scheduled;
        this.errorHandler = errorHandler;
        this.repos = repos;
        this.repoView = Collections.unmodifiableCollection(this.repos.values());
        this.storage = new RepositoryStorage(this, this.executor, this.scheduled);
        this.metadataService = new MetadataService(this.errorHandler);
        this.deployService = new DeployService(this, this.metadataService);
    }

    @Override
    public void load() {
        this.storage.load();
    }

    @Override
    public IRepository getPrimaryRepository() {
        return this.repos.values().iterator().next();
    }

    @Override
    public IRepository getRepo(String name) {
        return this.repos.get(name);
    }

    @Override
    public Collection<? extends IRepository> getRepos() {
        return this.repoView;
    }

    @Override
    public IQuota getQuota() {
        return this.quota;
    }

    @Override
    public ReposiliteConfiguration getCommands() {
        return this.metadataService;
    }

    @Override
    public void register(IJavalinContext jctx) {
        if (jctx.apiEnabled()) {
            Handler lookupApiEndpoint = jctx.authedToHandler(new LookupApiEndpoint(this, jctx.auth()));
            jctx.javalin()
                .get("/api", lookupApiEndpoint) // TODO: Kill this... We need to re-org the API to sane expandable formats.
                .get("/api/*", lookupApiEndpoint);
        }

        Handler deployEndpoint = jctx.authedToHandler(new DeployEndpoint(this.deployService));

        ProxyService proxyService = new ProxyService(this, jctx.config().proxyConnectTimeout, jctx.config().proxyReadTimeout, this.executor, this.errorHandler);
        LookupService lookupService = new LookupService(metadataService, this, jctx.auth(), proxyService);
        Handler lookupController = jctx.authedToHandler(new LookupEndpoint(this, jctx.frontend(), lookupService, errorHandler));

        jctx.javalin()
            .get("/*", lookupController)
            .head("/*", lookupController)
            .put("/*", deployEndpoint)
            .post("/*", deployEndpoint);
    }


    <R, E, T extends Exception> CompletableFuture<Result<R, E>> storeFile(
            String id,
            IRepository repo,
            String path,
            ThrowingSupplier<InputStream, IOException> source,
            ThrowingSupplier<R, T> onSuccess,
            Function<Exception, E> onError) {

        CompletableFuture<Result<R, E>> task = new CompletableFuture<>();

        tryExecute(id, task, onError, () -> {
            this.storage.storeFile(source.get(), repo, path).thenAccept(file -> {
                tryExecute(id, task, onError, () -> {
                    task.complete(Result.ok(onSuccess.get()));
                });
            });
        });

        return task;
    }

    private <R, E> void tryExecute(String id, CompletableFuture<Result<R, E>> task, Function<Exception, E> onError, ThrowingRunnable<? extends Exception> runnable) {
        try {
            runnable.run();
        } catch (Exception exception) {
            errorHandler.accept(id, exception);
            task.complete(Result.error(onError.apply(exception)));
        }
    }

    static class Builder implements IRepositoryManager.Builder {
        private String quota = null;
        private File dir = new File("./repositories");
        private Supplier<ExecutorService> executor = () -> new ThreadPoolExecutor(0, Integer.MAX_VALUE, 1, TimeUnit.SECONDS, new SynchronousQueue<>());
        private Supplier<ScheduledExecutorService> scheduled = Executors::newSingleThreadScheduledExecutor;
        private BiConsumer<String, Exception> error = (a, b) -> {};
        private List<Supplier<IRepository>> repos = new ArrayList<>();
        private DiskQuota quotaObj = null;

        @Override
        public IRepositoryManager build() {
            this.quotaObj = this.quota == null ?
                DiskQuota.unlimited() :
                this.quota.charAt(this.quota.length() - 1) == '%' ?
                    DiskQuota.ofPercentage(dir, this.quota) :
                    DiskQuota.of(this.quota);

            Map<String, IRepository> repoMap = repos.stream().map(Supplier::get).collect(Collectors.toMap(
                IRepository::getName,
                Function.identity(),
                (a,b) -> {
                    throw new IllegalStateException("Could not add multiple repos with the same name: " + a + " " + b);
                },
                LinkedHashMap::new)
            );

            return new RepositoryManager(
                this.quotaObj,
                dir,
                executor.get(),
                scheduled.get(),
                error,
                repoMap
            );
        }

        @Override
        public Builder quota(String value) {
            this.quota = value;
            return this;
        }

        @Override
        public Builder dir(File value) {
            this.dir = value;
            return this;
        }

        @Override
        public Builder executor(ExecutorService value) {
            this.executor = () -> value;
            return this;
        }

        @Override
        public Builder scheduled(ScheduledExecutorService value) {
            this.scheduled = () -> value;
            return this;
        }

        @Override
        public Builder error(BiConsumer<String, Exception> value) {
            this.error = value;
            return this;
        }

        public Builder repo(IRepository value) {
            this.repos.add(() -> value);
            return this;
        }

        @Override
        public IRepository.Builder repo(String name) {
            class Wrapper extends Repository.Builder {
                Wrapper(String name) {
                    super(name);
                    this.directory = () -> new File(Builder.this.dir, name);
                    Builder.this.repos.add(this::build);
                }

                @Override
                protected DiskQuota getQuota() {
                    DiskQuota parent = RepositoryManager.Builder.this.quotaObj;
                    return this.quota == null ?
                        DiskQuota.unlimited(parent) :
                        this.quota.charAt(this.quota.length() - 1) == '%' ?
                            DiskQuota.ofPercentage(parent, this.quota) :
                            DiskQuota.of(parent, this.quota);
                }
            }
            return new Wrapper(name);
        }

        @Override
        public Builder repo(String name, Consumer<IRepository.Builder> config) {
            config.accept(repo(name));
            return this;
        }
    }
}
