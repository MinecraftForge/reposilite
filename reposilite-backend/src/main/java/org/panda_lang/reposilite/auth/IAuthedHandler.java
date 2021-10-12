package org.panda_lang.reposilite.auth;

import org.panda_lang.reposilite.ReposiliteContext;

import io.javalin.http.Context;

public interface IAuthedHandler {
    void handle(Context ctx, ReposiliteContext authCtx);

}
