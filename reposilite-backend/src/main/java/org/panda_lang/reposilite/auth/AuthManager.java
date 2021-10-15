package org.panda_lang.reposilite.auth;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import org.apache.http.HttpStatus;
import org.panda_lang.reposilite.ReposiliteConfiguration;
import org.panda_lang.reposilite.console.Console;
import org.panda_lang.reposilite.error.ErrorDto;
import org.panda_lang.reposilite.repository.IRepositoryManager;
import org.panda_lang.utilities.commons.function.Result;

class AuthManager implements IAuthManager {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final File workDir;
    private final IRepositoryManager repos;
    private final Authenticator auth;

    private AuthManager(File workDir, IRepositoryManager repos) {
        this.workDir = workDir;
        this.repos = repos;
        this.auth = new Authenticator(workDir, repos);
    }

    @Override
    public void register(IJavalinContext jctx) {
        if (jctx.apiEnabled()) {
            jctx.javalin().get("/api/auth", ctx -> {
                getSession(ctx.headerMap())
                .map(session -> new AuthDto(session.getToken().getPath(), session.getToken().getPermissions(), session.getRepositoryNames()))
                .mapErr(error -> new ErrorDto(HttpStatus.SC_UNAUTHORIZED, error))
                .peek(ctx::json)
                .onError(error -> ctx.status(error.getStatus()).json(error));
            });
        }
        jctx.javalin().after("/*", ctx -> {
            if (ctx.status() == HttpStatus.SC_UNAUTHORIZED) {
                ctx.header("www-authenticate", "Basic realm=\"Reposilite\", charset=\"UTF-8\"");
            }
        });
    }

    @Override
    public void load() {
        try {
            this.auth.getTokenService().loadTokens();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void save() {
        try {
            this.auth.getTokenService().saveTokens();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ReposiliteConfiguration getCommands() {
        return reposilite -> {
            TokenService tokenService = this.auth.getTokenService();
            Console console = reposilite.getConsole();
            console.registerCommand(new ChAliasCommand(tokenService));
            console.registerCommand(new ChmodCommand(tokenService));
            console.registerCommand(new KeygenCommand(tokenService, this));
            console.registerCommand(new RevokeCommand(tokenService));
            console.registerCommand(new TokensCommand(tokenService));
        };
    }

    @Override
    public String createRandomPassword() {
        byte[] randomBytes = new byte[48];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getEncoder().encodeToString(randomBytes);
    }

    @Override
    public Result<Session, String> getSession(Map<String, String> header) {
        return this.auth.getSession(header);
    }

    @Override
    public Result<Session, String> getSession(String credentials) {
        return this.auth.getSession(credentials);
    }

    @Override
    public Token createToken(String path, String alias, String permissions, String password) {
        return this.auth.getTokenService().createToken(path, alias, permissions, password);
    }

    @Override
    public Token deleteToken(String name) {
        return this.auth.getTokenService().deleteToken(name);
    }

    @Override
    public Token getToken(String name) {
        return this.auth.getTokenService().getToken(name).getOrNull();
    }

    static class Builder implements IAuthManager.Builder {
        private File dir = new File(".");
        private IRepositoryManager repos = null;

        @Override
        public IAuthManager build() {
            if (repos == null)
                throw new IllegalStateException("Can not build a AuthManager without a RepositoryManager");
            return new AuthManager(dir, repos);
        }

        @Override
        public IAuthManager.Builder dir(File value) {
            this.dir = value;
            return this;
        }

        @Override
        public IAuthManager.Builder repo(IRepositoryManager value) {
            this.repos = value;
            return this;
        }
    }
}
