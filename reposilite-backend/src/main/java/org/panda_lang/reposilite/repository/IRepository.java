package org.panda_lang.reposilite.repository;

import java.io.File;
import java.util.Collection;

public interface IRepository {
    public static Builder builder(String name) {
        return new Repository.Builder(name);
    }

    String getName();
    boolean isReadOnly();
    boolean isHidden();
    boolean canBrowse();
    String getDelegate();
    Collection<String> getProxies();
    boolean canContain(String path);
    boolean isDirectory(String path);

    /* Loads information from disc, or wherever the repos are stored.
     * Calculates quotas and stuff like that
     */
    void load();
    IQuota getQuota();

    //TODO: Make this not use files, but something that allows us to swap out storage backends
    File getFile(View view, String... elements);
    boolean contains(View view, String path);

    public interface Builder {
        Builder prefix(String... values);
        default Builder hidden() { return hidden(true); }
        Builder hidden(boolean value);
        default Builder readOnly() { return readOnly(true); }
        Builder readOnly(boolean value);
        Builder browseable(boolean value); //I need a better name...
        Builder quota(String value);
        Builder proxy(String... values);
        Builder delegate(String value);
        Builder dir(File value);
        Builder baseDir(File value);
        IRepository build();
    }

    public enum View {
        // A view of releases only, may be multiple repos
        RELEASES,
        // A view of snapshots only, may be multiple repos
        SNAPSHOTS,
        // A view of everything, most likely multiple repos
        ALL
    }
}
