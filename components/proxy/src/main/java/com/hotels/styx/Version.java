/*
  Copyright (C) 2013-2018 Expedia Inc.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */
package com.hotels.styx;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Objects;
import java.util.Optional;

import static com.google.common.base.Objects.toStringHelper;
import static java.lang.Integer.parseInt;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Version of the current styx server.
 */
public class Version {
    private static final Logger LOG = getLogger(Version.class);

    private final String releaseTag;

    public static Version newVersion() {
        return new Version();
    }

    public static Version readVersionFrom(String versionPath) {
        try {
            try (Reader reader = new InputStreamReader(Version.class.getResourceAsStream(versionPath))) {
                return new ObjectMapper().readValue(reader, Version.class);
            }
        } catch (Exception e) {
            LOG.warn("error reading  [{}]. {}", versionPath, e);
            return new Version();
        }
    }

    private Version() {
        this.releaseTag = "STYX-dev.0.0";
    }

    @JsonCreator
    public Version(@JsonProperty("release.tag") String releaseTag) {
        this.releaseTag = releaseTag;
    }

    @JsonProperty("release.tag")
    public String releaseTag() {
        return this.releaseTag;
    }

    public String releaseVersion() {
        int firstDot = releaseTag.indexOf('.');

        if (firstDot == -1 || firstDot == releaseTag.length() - 1) {
            return releaseTag;
        }

        return releaseTag.substring(firstDot + 1);
    }

    public Optional<Integer> buildNumber() {
        String releaseVersion = releaseVersion();

        Optional<String> buildNumberAsString = extractFinalInt(releaseVersion);

        return buildNumberAsString.flatMap(this::parseInteger);
    }

    private Optional<String> extractFinalInt(String dotSeparatedIntegers) {
        int lastDot = dotSeparatedIntegers.lastIndexOf('.');

        if (lastDot == -1 || lastDot == dotSeparatedIntegers.length() - 1) {
            return Optional.empty();
        }

        return Optional.of(dotSeparatedIntegers.substring(lastDot + 1));
    }

    private Optional<Integer> parseInteger(String string) {
        try {
            return Optional.of(parseInt(string));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    @Override
    public String toString() {
        return toStringHelper(this)
                .add("releaseTag", releaseTag)
                .toString();
    }

    @Override
    public int hashCode() {
        return Objects.hash(releaseTag);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        Version other = (Version) obj;
        return Objects.equals(this.releaseTag, other.releaseTag);
    }
}
