package org.panda_lang.reposilite.repository;

import java.io.File;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.panda_lang.reposilite.IJavalinComponent;
import org.panda_lang.reposilite.ReposiliteConfiguration;

public interface IRepositoryManager extends IJavalinComponent {
    public static Builder builder() {
        return new RepositoryManager.Builder();
    }

    /* Loads information from disc, or wherever the repos are stored.
     * Calculates quotas and stuff like that
     */
    void load();

    IRepository getRepo(String name);
    Collection<? extends IRepository> getRepos();
    IQuota getQuota();

    @Deprecated //TODO: Move commands to their own package
    ReposiliteConfiguration getCommands();

    public static interface Builder {
        Builder quota(String value);
        Builder dir(File value);
        Builder executor(ExecutorService value);
        Builder scheduled(ScheduledExecutorService value);
        Builder error(BiConsumer<String, Exception> value);
        Builder repo(IRepository value);
        IRepository.Builder repo(String name);
        Builder repo(String name, Consumer<IRepository.Builder> config);
        IRepositoryManager build();
    }
}
