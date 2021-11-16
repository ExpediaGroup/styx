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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.hotels.styx.api.HttpHeaderValues.APPLICATION_JSON;
import static com.hotels.styx.api.HttpHeaderValues.HTML;
import static com.hotels.styx.api.HttpHeaderValues.PLAIN_TEXT;

/**
 * A collection of media types associated with file extensions.
 */
final class MediaTypes {
    private MediaTypes() {
    }

    public static final CharSequence MICROSOFT_MS_VIDEO = "video/x-msvideo";
    public static final CharSequence MICROSOFT_ASF_VIDEO = "video/x-ms-asf";

    public static final CharSequence WAV_AUDIO = "audio/x-wav";
    public static final CharSequence ICON = "image/x-icon";

    public static final CharSequence OCTET_STREAM = "application/octet-stream";
    private static final Map<String, CharSequence> MEDIA_TYPES_BY_EXTENSION = new HashMap<>();

    private static void addMediaType(CharSequence mediaType, String... extensions) {
        for (String extension : extensions) {
            MEDIA_TYPES_BY_EXTENSION.put(extension, mediaType);
        }
    }

    static {
        addMediaType(PLAIN_TEXT, "txt");
        addMediaType(HTML, "html", "htm");
        addMediaType("text/css;charset=UTF-8", "css");
        addMediaType(APPLICATION_JSON, "js");

        addMediaType(ICON, "ico");
        addMediaType("image/gif", "gif");
        addMediaType("image/jpeg", "jpg", "jpeg");
        addMediaType("image/png", "png");
        addMediaType("application/vnd.ms-excel", "xls");
        addMediaType("application/pdf", "pdf");

        addMediaType("audio/mpeg", "mp3");
        addMediaType(WAV_AUDIO, "wav");

        addMediaType(MICROSOFT_ASF_VIDEO, "asf");
        addMediaType(MICROSOFT_MS_VIDEO, "avi");
        addMediaType("video/mpeg", "mpg");
    }

    /**
     * Return the mime type of the given file extension,
     * defaults to "application/octet-stream" if the extension is missing or unknown.
     *
     * @param extension the file extension
     * @return the mime type of the given file extension
     */
    public static CharSequence mediaTypeForExtension(String extension) {
        return Optional.ofNullable(MEDIA_TYPES_BY_EXTENSION.get(extension))
                .orElse(OCTET_STREAM);
    }

    /**
     * Return the mime type of the given filename based on the file extension,
     * defaults to "application/octet-stream" if the extension is missing or unknown.
     *
     * @return the mime type of the given filename based on the file extension
     */
    public static CharSequence mediaTypeOf(String filename) {
        int dotPosition = filename.lastIndexOf('.');
        if (dotPosition > 0) {
            return mediaTypeForExtension(filename.substring(dotPosition + 1));
        }
        return OCTET_STREAM;
    }
}
