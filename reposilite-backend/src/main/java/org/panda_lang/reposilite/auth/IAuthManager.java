package org.panda_lang.reposilite.auth;

import java.io.File;
import java.util.Map;

import org.panda_lang.reposilite.IJavalinComponent;
import org.panda_lang.reposilite.ReposiliteConfiguration;
import org.panda_lang.reposilite.repository.IRepositoryManager;
import org.panda_lang.utilities.commons.function.Result;

public interface IAuthManager extends IJavalinComponent {
    public static Builder builder() {
        return new AuthManager.Builder();
    }

    // Loads user information
    void load();
    // Forces any pending data to be saved, typically in prep for shut down
    void save();

    @Deprecated //TODO: Move commands to their own package
    ReposiliteConfiguration getCommands();

    @Deprecated //TODO: Properly encapsulate/limit the entry points?, Only used by CliController
    Result<Session, String> getSession(String creds);
    @Deprecated //TODO: Properly encapsulate/limit the entry points?
    Result<Session, String> getSession(Map<String, String> headers, String url);

    // Create a random password using a SecureRandom generator.
    String createRandomPassword();

    //TODO: Redesign the entire auth system to allow users with multiple paths, not single tokens
    Token createToken(String path, String alias, String permissions, String password);
    Token deleteToken(String name);

    Token getToken(String name);

    public static interface Builder {
        IAuthManager build();
        Builder dir(File value);
        Builder repo(IRepositoryManager value);
    }
}
