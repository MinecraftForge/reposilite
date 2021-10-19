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

package org.panda_lang.reposilite.config;

import net.dzikoysk.cdn.entity.Description;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

public final class Configuration {

    @Description("# ~~~~~~~~~~~~~~~~~~~~~~ #")
    @Description("#       Reposilite       #")
    @Description("# ~~~~~~~~~~~~~~~~~~~~~~ #")

    // Bind properties
    @Description("")
    @Description("# Hostname")
    public String hostname = "0.0.0.0";
    @Description("# Port to bind")
    public Integer port = 80;
    @Description("# Custom base path")
    public String basePath = "/";
    @Description("# Any kind of proxy services change real ip.")
    @Description("# The origin ip should be available in one of the headers.")
    @Description("# Nginx: X-Forwarded-For")
    @Description("# Cloudflare: CF-Connecting-IP")
    @Description("# Popular: X-Real-IP")
    public String forwardedIp = "X-Forwarded-For";
    @Description("# Debug")
    public Boolean debugEnabled = false;

    // SSL
    @Description("")
    @Description("# Support encrypted connections")
    public Boolean sslEnabled = false;
    @Description("# SSL port to bind")
    public Integer sslPort = 443;
    @Description("# Key store file to use.")
    @Description("# You can specify absolute path to the given file or use ${WORKING_DIRECTORY} variable.")
    public String keyStorePath = "${WORKING_DIRECTORY}/keystore.jks";
    @Description("# Key store password to use")
    public String keyStorePassword = "";
    @Description("# Redirect http traffic to https")
    public Boolean enforceSsl = false;

    // Repository properties
    @Description("")
    @Description("# Control the maximum amount of data assigned to Reposilite instance")
    @Description("# Supported formats: 90%, 500MB, 10GB")
    public String diskQuota = "10GB";

    @Description("# How long Reposilite can wait for establishing the connection with a remote host. (In seconds)")
    public Integer proxyConnectTimeout = 3;
    @Description("# How long Reposilite can read data from remote proxy. (In seconds)")
    @Description("# Increasing this value may be required in case of proxying slow remote repositories.")
    public Integer proxyReadTimeout = 15;

    // Frontend properties
    @Description("")
    @Description("# Title displayed by frontend")
    public String title = "#onlypanda";
    @Description("# Description displayed by frontend")
    public String description = "Public Maven repository hosted through the Reposilite";
    @Description("# Accent color used by frontend")
    public String accentColor = "#2fd4aa";

    // API Properties
    @Description("")
    @Description("# API Enabled")
    public boolean apiEnabled = true;

    @SuppressWarnings("serial")
    @Description({
    "",
    "# Repositories, in the order in which they will be searched.",
    "# Repository names must mete the following criteria:",
    "#   No '-': This denotes a suffix for filtering.",
    "#   Lowercase: To prevent ambiguity in requests.",
    "#   Neither `releases` or `snapshots`: Reserved for multi-repo views.",
    "# Names may have a `-releases` or `-snapshots` suffix, if they do they will be",
    "# considered for the special multi-repo views.",
    "#",
    "# repositories {",
    "#   main {",
    "#     # Prefix of paths that this repo will allow.",
    "#     # May be empty to allow all paths",
    "#     prefixes: []",
    "#     # Wither or not this repo requires authentication to read.",
    "#     hidden: false",
    "#     # Weither this repo can be browsed by non-autheticated users.",
    "#     beowseable: true",
    "#     # Allow users with write access to upload.",
    "#     allowUploads: true",
    "#     # Control the maximum amount of data assigned to this repository.",
    "#     # May be null/empty to use the global quota.",
    "#     diskQuota: \"\"",
    "#     # List of remote repositories to proxy",
    "#     # Files not found locally will be attempted to be found",
    "#     # in the following reposiories. And cached locally.",
    "#     proxies: []",
    "#     # Repository name to delegate requests to, if this one does not ",
    "#     # have the requested file. This can not be used with 'proxies'.",
    "#     # This is mainly intended to allow proxied files to be stored in",
    "#     # a seperate repo for organiztion.",
    "#     delegate: \"\"",
    "#   }",
    "# }"
    })
    public LinkedHashMap<String, Repository> repositories = new LinkedHashMap<String, Repository>() {{
        put("main-releases", new Repository());
        put("main-snapshots", new Repository());
    }};

    public static class Repository {
        public List<String> prefixes = Collections.emptyList();
        public Boolean hidden = false;
        public Boolean browseable = true;
        public Boolean allowUploads = true;
        public String diskQuota = "";
        public List<String> proxies = Collections.emptyList();
        public String delegate = "";
    }
}
