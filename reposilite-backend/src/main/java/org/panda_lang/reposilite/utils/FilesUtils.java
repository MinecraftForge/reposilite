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

package org.panda_lang.reposilite.utils;

import org.apache.commons.io.FileUtils;
import org.panda_lang.reposilite.Reposilite;
import org.panda_lang.utilities.commons.IOUtils;
import org.panda_lang.utilities.commons.StringUtils;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FilesUtils {

    private static final File[] EMPTY = {};

    private final static long KB_FACTOR = 1024;
    private final static long MB_FACTOR = 1024 * KB_FACTOR;
    private final static long GB_FACTOR = 1024 * MB_FACTOR;
    private static final DecimalFormat NUMBER_FORMATTER = new DecimalFormat("#.##");

    private static final String[] READABLE_CONTENT = {
            ".xml",
            ".pom",
            ".txt",
            ".json",
            ".cdn",
            ".yaml",
            ".yml"
    };

    private FilesUtils() {}

    public static void writeFileChecksums(Path path) throws IOException {
        for (HashFunction func : new HashFunction[] { HashFunction.MD5, HashFunction.SHA1}) {
            File file = new File(path.toString() + '.' + func.getExtension());
            String hash = func.hash(path);
            Files.write(file.toPath(), hash.getBytes(StandardCharsets.UTF_8));
        }
    }

    public static long displayToBytes(String display) {
        Pattern pattern = Pattern.compile("([0-9]+)(([KkMmGg])*[Bb])");
        Matcher match = pattern.matcher(display);

        if (!match.matches() || match.groupCount() != 3) {
            return Long.parseLong(display);
        }

        long value = Long.parseLong(match.group(1));

        switch (match.group(2).toUpperCase()) {
            case "GB":
                return value * GB_FACTOR;
            case "MB":
                return value * MB_FACTOR;
            case "KB":
                return value * KB_FACTOR;
            case "B":
                return value;
            default:
                throw new NumberFormatException("Wrong format");
        }
    }

    public static String bytesToDisplay(long value) {
        if (value == Long.MAX_VALUE)
            return "Unlimited";
        if (value >= GB_FACTOR)
            return NUMBER_FORMATTER.format(value / (float)GB_FACTOR) + "GB";
        else if (value >= MB_FACTOR)
            return NUMBER_FORMATTER.format(value / (float)MB_FACTOR) + "MB";
        else if (value >= KB_FACTOR)
            return NUMBER_FORMATTER.format(value / (float)KB_FACTOR) + "KB";
        return Long.toString(value);
    }

    public static boolean isReadable(String name) {
        for (String extension : READABLE_CONTENT) {
            if (name.endsWith(extension)) {
                return true;
            }
        }

        return false;
    }

    public static String getMimeType(String path, String defaultType) {
        return MimeTypes.getMimeType(getExtension(path), defaultType);
    }

    public static void copyResource(String resourcePath, File destination) throws IOException {
        URL inputUrl = Reposilite.class.getResource(resourcePath);
        FileUtils.copyURLToFile(inputUrl, destination);
    }

    public static void close(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                // idc
            }
        }
    }

    public static File[] listFiles(File directory) {
        File[] files = directory.listFiles();
        return files == null ? EMPTY : files;
    }

    public static String getExtension(String name) {
        int occurrence = name.lastIndexOf(".");
        return occurrence == -1 ? StringUtils.EMPTY : name.substring(occurrence + 1);
    }

    public static String getResource(String name, File base) {
        File target = base == null ? null : new File(base, name);
        if (target != null && target.exists()) {
            try (InputStream stream = new FileInputStream(target)){
                return IOUtils.convertStreamToString(stream).orElseThrow(ioException -> {
                    throw new RuntimeException("Cannot load resource " + name, ioException);
                });
            } catch (IOException e) {
                throw new RuntimeException("Cannot load resource " + name, e);
            }
        }
        return IOUtils.convertStreamToString(Reposilite.class.getResourceAsStream(name)).orElseThrow(ioException -> {
            throw new RuntimeException("Cannot load resource " + name, ioException);
        });
    }

    public static String trim(String data, char chr) {
        int start = -1;
        while (++start < data.length() && data.charAt(start) == chr);
        if (start != 0)
            data = data.substring(start);

        int end = data.length();
        while (end > 0 && data.charAt(--end) == chr);
        if (end != data.length())
            data = data.substring(0, end + 1);
        return data;
    }

    public static void validateRepositoryName(String name) {
        if (!name.equals(name.toLowerCase(Locale.ENGLISH)))
            throw new IllegalArgumentException("Invalid repository name: " + name + " (not lowercase)");

        if (name.indexOf('/') != -1 || name.indexOf('\\') != -1 || name.contains(".."))
            throw new IllegalArgumentException("Invalid repository name: " + name + " (directory traversal)");

        if ("releases".equals(name) || "snapshots".equals(name))
            throw new IllegalArgumentException("Invalid repository name: " + name + " (reserved name)");

        int idx  = name.indexOf('-');
        if (idx != -1)
            throw new IllegalArgumentException("Invalid repository name: " + name + " (illegal suffix)");
    }
}
