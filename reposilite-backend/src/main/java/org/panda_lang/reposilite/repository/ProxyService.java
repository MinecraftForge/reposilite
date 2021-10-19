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

package org.panda_lang.reposilite.repository;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpStatus;
import org.panda_lang.reposilite.Reposilite;
import org.panda_lang.reposilite.ReposiliteContext;
import org.panda_lang.reposilite.ReposiliteException;
import org.panda_lang.reposilite.error.ErrorDto;
import org.panda_lang.reposilite.error.ResponseUtils;
import org.panda_lang.utilities.commons.function.Result;

import java.io.File;
import java.net.SocketTimeoutException;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;

final class ProxyService {
    private final int proxyConnectTimeout;
    private final int proxyReadTimeout;
    private final ExecutorService ioService;
    private final IRepositoryManager repos;
    private final BiConsumer<String, Exception> errorHandler;
    private final HttpRequestFactory httpRequestFactory;

    public ProxyService(
            IRepositoryManager repos,
            int proxyConnectTimeout,
            int proxyReadTimeout,
            ExecutorService ioService,
            BiConsumer<String, Exception> errorHandler) {
        this.repos = repos;
        this.proxyConnectTimeout = proxyConnectTimeout;
        this.proxyReadTimeout = proxyReadTimeout;
        this.ioService = ioService;
        this.errorHandler = errorHandler;
        this.httpRequestFactory = new NetHttpTransport().createRequestFactory();
    }

    Result<LookupResponse, ErrorDto> findProxied(ReposiliteContext context, IRepository repo, String[] pathParts) {
        Collection<String> proxies = repo.getProxies();

        // /groupId/artifactId/<content>
        if (pathParts.length < 3)
            return Result.error(new ErrorDto(HttpStatus.SC_NOT_FOUND, "Invalid proxied request"));

        String path = context.filepath();

        Future<Result<LookupResponse, ErrorDto>> future = ioService.submit(() -> {
            for (String proxied : proxies) { // TODO: Rewrite all this
                try {
                    // TODO: Check for HEAD request, so that a HEAD to us doesn't result in a full GET to them
                    HttpRequest remoteRequest = httpRequestFactory.buildGetRequest(new GenericUrl(proxied + path));
                    remoteRequest.setThrowExceptionOnExecuteError(false);
                    remoteRequest.setConnectTimeout(proxyConnectTimeout * 1000);
                    remoteRequest.setReadTimeout(proxyReadTimeout * 1000);
                    HttpResponse remoteResponse = remoteRequest.execute();

                    if (!remoteResponse.isSuccessStatusCode()) {
                        continue;
                    }

                    HttpHeaders headers = remoteResponse.getHeaders();

                    //TODO: Detect 302 redirects to directory listing
                    if ("text/html".equals(headers.getContentType())) {
                        continue;
                    }

                    long contentLength = headers.getContentLength() == null ? 0 : headers.getContentLength();

                    FileDetailsDto fileDetails = new FileDetailsDto(FileDetailsDto.FILE, pathParts[pathParts.length - 1], "", remoteResponse.getContentType(), contentLength);
                    LookupResponse response = new LookupResponse(fileDetails);

                    if (context.method().equals("HEAD")) {
                        return Result.ok(response);
                    }

                    /*
                    if (!storeProxied) {
                        context.result(outputStream -> IOUtils.copyLarge(remoteResponse.getContent(), outputStream));
                        return proxiedTask.complete(Result.ok(response));
                    }
                    */

                    return store(context, repo, path, remoteResponse);
                }
                catch (Exception exception) {
                    String message = "Proxied repository " + proxied + " is unavailable due to: " + exception.getMessage();
                    Reposilite.getLogger().error(message);

                    if (!(exception instanceof SocketTimeoutException)) {
                        errorHandler.accept(path, new ReposiliteException(message, exception));
                    }
                }
            }

            return Result.error(new ErrorDto(HttpStatus.SC_NOT_FOUND, "Artifact not found in local and remote repository"));
        });

        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            errorHandler.accept(path, e);
            return ResponseUtils.error(HttpStatus.SC_NOT_FOUND, "Error while resolving proxied artifact");
        }
    }

    private Result<LookupResponse, ErrorDto> store(ReposiliteContext context, IRepository repo, String uri, HttpResponse remoteResponse) {
        IQuota quota = repo.getQuota();

        if (!quota.hasSpace()) {
            Reposilite.getLogger().warn("Out of disk space - Cannot store proxied artifact " + uri);
            return ResponseUtils.error(HttpStatus.SC_NOT_FOUND, "Artifact not found in local and remote repository");
        }

        //TODO: Design a better API for this, so we don't have to cast to internal types.
        CompletableFuture<Result<LookupResponse, ErrorDto>> future = ((RepositoryManager)repos).storeFile(
            uri,
            repo,
            context.filepath(),
            remoteResponse::getContent,
            () -> {
                File file = repo.getFile(context.filepath());
                Reposilite.getLogger().info("Stored proxied " + context.filepath() + " in " + repo + " from " + remoteResponse.getRequest().getUrl());
                context.result(outputStream -> FileUtils.copyFile(file, outputStream));
                return new LookupResponse(FileDetailsDto.of(file));
            },
            exception -> new ErrorDto(HttpStatus.SC_UNPROCESSABLE_ENTITY, "Cannot process artifact")
        );

        try {
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            errorHandler.accept(uri, e);
            return ResponseUtils.error(HttpStatus.SC_NOT_FOUND, "Error while resolving proxied artifact");
        }
    }

}
