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
import org.panda_lang.reposilite.auth.Session;
import org.panda_lang.reposilite.repository.IRepository;
import org.panda_lang.reposilite.repository.IRepositoryManager;
import org.panda_lang.utilities.commons.StringUtils;
import org.panda_lang.utilities.commons.function.Option;
import org.panda_lang.utilities.commons.function.Result;
import org.panda_lang.utilities.commons.function.ThrowingConsumer;
import org.panda_lang.utilities.commons.function.ThrowingSupplier;

import io.javalin.http.Context;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

public final class ReposiliteContext {
    public static ReposiliteContext create(IAuthManager auth, IRepositoryManager repos, String ipHeader, Context context) {
        Map<String, String> headers = context.headerMap(); // this can only be called once with a valid result for some reason, so cache it here
        String realIp = context.header(ipHeader);
        String address = StringUtils.isEmpty(realIp) ? context.req.getRemoteAddr() : realIp;
        Result<Session, String> session = auth.getSession(headers);

        String uri = context.req.getRequestURI();
        if (uri.startsWith("/api/"))
            uri = uri.substring(5);
        else if (uri.equals("/api"))
            uri = "";
        String normalized = ReposiliteUtils.normalizeUri(repos, uri).getOrNull();

        //TODO: check auth and only list repos we have access to
        IRepository repo = null;
        String filepath = null;
        if (normalized != null) {
            int idx = normalized.indexOf('/');
            if (idx != -1) {
                repo = repos.getRepo(normalized.substring(0, idx));
                filepath = normalized.substring(idx + 1);
            }
        }

        return new ReposiliteContext(
            context.req.getRequestURI(),
            normalized,
            filepath,
            context.method(),
            address,
            headers,
            context.req::getInputStream,
            repo,
            auth,
            session
        );
    }

    private final String uri;
    private final String normalized;
    private final String filepath;
    private final String method;
    private final String address;
    private final Map<String, String> header;
    private final ThrowingSupplier<InputStream, IOException> input;
    private final IRepository repo;
    private final IAuthManager auth;
    private final Result<Session, String> session;
    private ThrowingConsumer<OutputStream, IOException> result;

    private ReposiliteContext(
            String uri,
            String normalized,
            String filepath,
            String method,
            String address,
            Map<String, String> header,
            ThrowingSupplier<InputStream, IOException> input,
            IRepository repo,
            IAuthManager auth,
            Result<Session, String> session) {

        this.uri = uri;
        this.normalized = normalized;
        this.filepath = filepath;
        this.method = method;
        this.address = address;
        this.header = header;
        this.input = input;
        this.repo = repo;
        this.auth = auth;
        this.session = session;
    }

    public void result(ThrowingConsumer<OutputStream, IOException> result) {
        this.result = result;
    }

    public Option<ThrowingConsumer<OutputStream, IOException>> result() {
        return Option.of(result);
    }

    public InputStream input() throws IOException {
        return input.get();
    }

    public Map<String, String> headers() {
        return header;
    }

    public String address() {
        return address;
    }

    public String method() {
        return method;
    }

    public String uri() {
        return uri;
    }

    public String normalized() {
        return normalized;
    }

    public String filepath() {
        return filepath;
    }

    public IRepository repo() {
        return repo;
    }

    public IAuthManager auth() {
        return auth;
    }

    public Result<Session, String> session() {
        return this.session;
    }

    public Result<Session, String> session(String url) {
        return session.flatMap(s -> s.hasPermissionTo(url) ? Result.ok(s) : Result.error("Unauthorized access attempt"));
    }
}
