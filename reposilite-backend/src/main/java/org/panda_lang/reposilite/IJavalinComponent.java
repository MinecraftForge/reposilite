package org.panda_lang.reposilite;

import org.panda_lang.reposilite.auth.IAuthManager;
import org.panda_lang.reposilite.auth.IAuthedHandler;
import org.panda_lang.reposilite.config.Configuration;
import org.panda_lang.reposilite.repository.IRepositoryManager;
import org.panda_lang.reposilite.resource.FrontendProvider;

import io.javalin.Javalin;
import io.javalin.http.Handler;

public interface IJavalinComponent {
    void register(IJavalinContext ctx);

    // Not a fan of this, but it allows us to extend the context without breaking API for people who don't care.
    public interface IJavalinContext {
        Javalin javalin();
        boolean apiEnabled();
        Handler authedToHandler(IAuthedHandler child);
        IAuthManager auth();
        IRepositoryManager repos();
        FrontendProvider frontend();
        @Deprecated Configuration config();
    }
}
