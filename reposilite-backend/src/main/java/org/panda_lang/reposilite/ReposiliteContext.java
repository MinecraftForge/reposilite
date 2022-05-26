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
import org.panda_lang.reposilite.repository.IRepository.View;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ReposiliteContext {
    public static ReposiliteContext create(IAuthManager auth, IRepositoryManager repoManager, String ipHeader, Context context) {
        Map<String, String> headers = context.headerMap(); // this can only be called once with a valid result for some reason, so cache it here
        String realIp = context.header(ipHeader);
        String address = StringUtils.isEmpty(realIp) ? context.req.getRemoteAddr() : realIp;
        Result<Session, String> session = auth.getSession(headers);

        String uri = context.req.getRequestURI();
        if (uri.startsWith("/api/"))
            uri = uri.substring(5);
        else if (uri.equals("/api"))
            uri = "";

        View view = View.ALL;
        String filepath = null;
        String sanitized = sanitize(uri);
        List<IRepository> repos = new ArrayList<>();

        if (sanitized != null) {
            int idx = sanitized.indexOf('/');
            if (idx == -1) {
                filepath = sanitized;
                repos.addAll(repoManager.getRepos());
            } else {
                String name = sanitized.substring(0, idx);
                if ("releases".equals(name)) {
                    view = View.RELEASES;
                    repos.addAll(repoManager.getRepos());
                    filepath = sanitized.substring(idx + 1);
                } else if ("snapshots".equals(name)) {
                    view = View.SNAPSHOTS;
                    repos.addAll(repoManager.getRepos());
                    filepath = sanitized.substring(idx + 1);
                } else {
                    int hidx = name.indexOf('-');
                    if (hidx > 1) {
                        String view_name = name.substring(hidx + 1);
                        if ("releases".equals(view_name)) {
                            view = View.RELEASES;
                            name = name.substring(0, hidx);
                        } else if ("snapshots".equals(view_name)) {
                            view = View.SNAPSHOTS;
                            name = name.substring(0, hidx);
                        }
                    }

                    IRepository repo = repoManager.getRepo(name);
                    if (repo == null) {
                        repos.addAll(repoManager.getRepos());
                        filepath = sanitized;
                    } else {
                        repos.add(repo);
                        filepath = sanitized.substring(idx + 1);
                    }
                }
            }
        }

        return new ReposiliteContext(
            context.req.getRequestURI(),
            sanitized,
            filepath,
            context.method(),
            address,
            headers,
            context.req::getInputStream,
            repos,
            auth,
            session,
            view
        );
    }

    static String sanitize(String url) {
        if (url.isEmpty())
            return url;

        int idx = -1;
        while (++idx < url.length() && url.charAt(idx) == '/'); // TODO: 404 on multiple consecutive slashes.
        if (idx != 0)
            url = url.substring(idx);


        if (url.contains("..") || url.contains("~") || url.contains(":") || url.contains("\\"))
            return null;

        if ("releases".equals(url) || "snapshots".equals(url))
            return url + '/';

        return url;
    }

    /**
     * Process uri applying following changes:
     *
     * <ul>
     *     <li>Remove root slash</li>
     *     <li>Remove illegal path modifiers like .. and ~</li>
     *     <li>Insert repository name if missing</li>
     * </ul>
     *
     * @param rewritePathsEnabled determines if path rewriting is enabled
     * @param uri the uri to process
     * @return the normalized uri
     */
    /*
    static Option<String> normalizeUri(IRepositoryManager repos, String uri) {
        uri = sanitize(uri);
        if (uri == null)
            return Option.none();

        int idx = uri.indexOf('/');
        if (idx == -1)
            return Option.of(uri);

        String first = uri.substring(0, idx);
        if (repos.getRepo(first) != null)
            return Option.of(uri);

        for (IRepository repo : repos.getRepos()) {
            if (repo.canContain(uri))
                return Option.of(repo.getName() + '/' + uri);
        }

        // If all else fails, fallback to the input uri
        return Option.of(uri);
    }
    */

    private final String uri;
    private final String sanitized;
    private final String filepath;
    private final String method;
    private final String address;
    private final Map<String, String> header;
    private final ThrowingSupplier<InputStream, IOException> input;
    private final List<IRepository> repos;
    private final IAuthManager auth;
    private final Result<Session, String> session;
    private final View view;
    private ThrowingConsumer<OutputStream, IOException> result;

    private ReposiliteContext(
            String uri,
            String sanitized,
            String filepath,
            String method,
            String address,
            Map<String, String> header,
            ThrowingSupplier<InputStream, IOException> input,
            List<IRepository> repos,
            IAuthManager auth,
            Result<Session, String> session,
            View view) {

        this.uri = uri;
        this.sanitized = sanitized;
        this.filepath = filepath;
        this.method = method;
        this.address = address;
        this.header = header;
        this.input = input;
        this.repos = repos;
        this.auth = auth;
        this.session = session;
        this.view = view;
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

    public String sanitized() {
        return sanitized;
    }

    public String filepath() {
        return filepath;
    }

    public List<IRepository> repos() {
        return repos;
    }

    public IAuthManager auth() {
        return auth;
    }

    public Result<Session, String> session() {
        return session;
    }

    public Result<Session, String> session(String url) {
        return session.flatMap(s -> s.hasPermissionTo(url) ? Result.ok(s) : Result.error("Unauthorized access attempt"));
    }

    public View view() {
        return view;
    }
}
