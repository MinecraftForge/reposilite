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

package org.panda_lang.reposilite.metadata;

import org.panda_lang.reposilite.utils.FilesUtils;
import org.panda_lang.utilities.commons.StringUtils;
import org.panda_lang.utilities.commons.collection.Pair;
import org.panda_lang.utilities.commons.function.PandaStream;
import org.panda_lang.utilities.commons.text.Joiner;

import java.io.File;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class MetadataUtils {

    public static final String ESCAPE_DOT = "`.`";

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                    .withLocale(Locale.getDefault())
                    .withZone(ZoneOffset.UTC);

    private MetadataUtils() { }

    static File[] toSortedBuilds(File artifactDirectory) {
        return PandaStream.of(FilesUtils.listFiles(artifactDirectory))
                .filter(File::isFile)
                .filter(file -> file.getName().endsWith(".pom"))
                .stream(stream -> toSorted(stream, File::getName, File::isDirectory))
                .toArray(File[]::new);
    }

    static File[] toFiles(File directory) {
        return PandaStream.of(FilesUtils.listFiles(directory))
                .filter(File::isFile)
                .transform(stream -> toSorted(stream, File::getName, File::isDirectory))
                .toArray(File[]::new);
    }

    public static File[] toSortedVersions(File artifactDirectory) {
        return PandaStream.of(FilesUtils.listFiles(artifactDirectory))
                .filter(File::isDirectory)
                .transform(stream -> toSorted(stream, File::getName, File::isDirectory))
                .toArray(File[]::new);
    }

    static String[] toSortedIdentifiers(String artifact, String version, File[] builds) {
        return PandaStream.of(builds)
                .map(build -> toIdentifier(artifact, version, build))
                .filterNot(StringUtils::isEmpty)
                .distinct()
                .transform(stream -> toSorted(stream, Function.identity(), identifier -> true))
                .toArray(String[]::new);
    }

    static File[] toBuildFiles(File artifactDirectory, String identifier) {
        return PandaStream.of(FilesUtils.listFiles(artifactDirectory))
                .filter(file -> file.getName().contains(identifier + ".") || file.getName().contains(identifier + "-"))
                .filterNot(file -> file.getName().endsWith(".md5"))
                .filterNot(file -> file.getName().endsWith(".sha1"))
                .transform(stream -> toSorted(stream, File::getName, File::isDirectory))
                .toArray(File[]::new);
    }

    public static <T> Stream<T> toSorted(Stream<T> stream, Function<T, String> mapper, Predicate<T> isDirectory) {
        return stream
                .map(object -> new Pair<>(object, mapper.apply(object).split("[-.]")))
                .sorted(new MetadataComparator<>(pair -> mapper.apply(pair.getKey()), Pair::getValue, pair -> isDirectory.test(pair.getKey())))
                .map(Pair::getKey);
    }

    static String toIdentifier(String artifact, String version, File build) {
        String identifier = build.getName();
        identifier = StringUtils.replace(identifier, "." + FilesUtils.getExtension(identifier), StringUtils.EMPTY);
        identifier = StringUtils.replace(identifier, artifact + "-", StringUtils.EMPTY);
        identifier = StringUtils.replace(identifier, version + "-", StringUtils.EMPTY);
        return declassifyIdentifier(identifier);
    }

    private static String declassifyIdentifier(String identifier) {
        int occurrences = StringUtils.countOccurrences(identifier, "-");

        // no action required
        if (occurrences == 0) {
            return identifier;
        }

        int occurrence = identifier.indexOf("-");

        // process identifiers without classifier or build number
        if (occurrences == 1) {
            return isBuildNumber(identifier.substring(occurrence + 1)) ? identifier : identifier.substring(0, occurrence);
        }

        // remove classifier
        return isBuildNumber(identifier.substring(occurrence + 1, identifier.indexOf("-", occurrence + 1)))
                ? identifier.substring(0, identifier.indexOf("-", occurrence + 1))
                : identifier.substring(0, occurrence);
    }

    static String toUpdateTime(File file) {
        return TIMESTAMP_FORMATTER.format(Instant.ofEpochMilli(file.lastModified()));
    }

    static String toGroup(String[] elements) {
        return Joiner.on(".")
                .join(Arrays.copyOfRange(elements, 0, elements.length), value -> value.contains(".") ? value.replace(".", ESCAPE_DOT) : value)
                .toString();
    }

    static String toGroup(String[] elements, int toShrink) {
        return toGroup(shrinkGroup(elements, toShrink)).replace(ESCAPE_DOT, ".");
    }

    private static String[] shrinkGroup(String[] elements, int toShrink) {
        return Arrays.copyOfRange(elements, 0, elements.length - toShrink);
    }

    private static boolean isBuildNumber(String content) {
        for (char character : content.toCharArray()) {
            if (!Character.isDigit(character)) {
                return false;
            }
        }

        return true;
    }

}
