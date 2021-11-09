/*
  Copyright (C) 2013-2021 Expedia Inc.

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
package com.hotels.styx.server.handlers;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static com.hotels.styx.api.HttpHeaderValues.APPLICATION_JSON;
import static com.hotels.styx.api.HttpHeaderValues.HTML;
import static com.hotels.styx.api.HttpHeaderValues.PLAIN_TEXT;
import static com.hotels.styx.server.handlers.MediaTypes.ICON;
import static com.hotels.styx.server.handlers.MediaTypes.MICROSOFT_ASF_VIDEO;
import static com.hotels.styx.server.handlers.MediaTypes.MICROSOFT_MS_VIDEO;
import static com.hotels.styx.server.handlers.MediaTypes.OCTET_STREAM;
import static com.hotels.styx.server.handlers.MediaTypes.WAV_AUDIO;
import static com.hotels.styx.server.handlers.MediaTypes.mediaTypeForExtension;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class MediaTypesTest {
    @ParameterizedTest
    @MethodSource("mediaTypesByFileExtensions")
    public void returnsMediaTypesByFileExtensions(String extension, CharSequence mediaType) {
        assertThat(mediaTypeForExtension(extension), is(mediaType));
    }

    private static Stream<Arguments> mediaTypesByFileExtensions() {
        return Stream.of(
                Arguments.of("htm", HTML),
                Arguments.of("txt", PLAIN_TEXT),
                Arguments.of("unknown", OCTET_STREAM),
                Arguments.of("css", "text/css;charset=UTF-8"),
                Arguments.of("js", APPLICATION_JSON),
                Arguments.of("ico", ICON),
                Arguments.of("gif", "image/gif"),
                Arguments.of("jpg", "image/jpeg"),
                Arguments.of("jpeg", "image/jpeg"),
                Arguments.of("png", "image/png"),
                Arguments.of("xls", "application/vnd.ms-excel"),
                Arguments.of("pdf", "application/pdf"),

                Arguments.of("mp3", "audio/mpeg"),
                Arguments.of("wav", WAV_AUDIO),

                Arguments.of("asf", MICROSOFT_ASF_VIDEO),
                Arguments.of("avi", MICROSOFT_MS_VIDEO),
                Arguments.of("mpg", "video/mpeg")
        );
    }
}