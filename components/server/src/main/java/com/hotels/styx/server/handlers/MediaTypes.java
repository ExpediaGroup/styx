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
package com.hotels.styx.server.handlers;

import com.google.common.net.MediaType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.google.common.net.MediaType.CSS_UTF_8;
import static com.google.common.net.MediaType.GIF;
import static com.google.common.net.MediaType.HTML_UTF_8;
import static com.google.common.net.MediaType.JAVASCRIPT_UTF_8;
import static com.google.common.net.MediaType.JPEG;
import static com.google.common.net.MediaType.MICROSOFT_EXCEL;
import static com.google.common.net.MediaType.MPEG_AUDIO;
import static com.google.common.net.MediaType.MPEG_VIDEO;
import static com.google.common.net.MediaType.OCTET_STREAM;
import static com.google.common.net.MediaType.PDF;
import static com.google.common.net.MediaType.PLAIN_TEXT_UTF_8;
import static com.google.common.net.MediaType.PNG;
import static com.google.common.net.MediaType.create;

/**
 * A collection of {@link MediaType}s associated with file extensions.
 */
final class MediaTypes {
    private MediaTypes() {
    }

    public static final MediaType MICROSOFT_MS_VIDEO = create("video", "x-msvideo");
    public static final MediaType MICROSOFT_ASF_VIDEO = create("video", "x-ms-asf");

    public static final MediaType WAV_AUDIO = create("audio", "x-wav");
    public static final MediaType ICON = create("image", "x-icon");
    public static final MediaType EVENT_STREAM = create("text", "event-stream");

    private static final Map<String, MediaType> MEDIA_TYPES_BY_EXTENSION = new HashMap<>();

    private static void addMediaType(MediaType mediaType, String... extensions) {
        for (String extension : extensions) {
            MEDIA_TYPES_BY_EXTENSION.put(extension, mediaType);
        }
    }

    static {
        addMediaType(PLAIN_TEXT_UTF_8, "txt");
        addMediaType(HTML_UTF_8, "html", "htm");
        addMediaType(CSS_UTF_8, "css");
        addMediaType(JAVASCRIPT_UTF_8, "js");

        addMediaType(ICON, "ico");
        addMediaType(GIF, "gif");
        addMediaType(JPEG, "jpg", "jpeg");
        addMediaType(PNG, "png");
        addMediaType(MICROSOFT_EXCEL, "xls");
        addMediaType(PDF, "pdf");

        addMediaType(MPEG_AUDIO, "mp3");
        addMediaType(WAV_AUDIO, "wav");

        addMediaType(MICROSOFT_ASF_VIDEO, "asf");
        addMediaType(MICROSOFT_MS_VIDEO, "avi");
        addMediaType(MPEG_VIDEO, "mpg");
    }

    /**
     * Return the mime type of the given file extension,
     * defaults to "application/octet-stream" if the extension is missing or unknown.
     *
     * @param extension the file extension
     * @return the mime type of the given file extension
     */
    public static MediaType mediaTypeForExtension(String extension) {
        return Optional.ofNullable(MEDIA_TYPES_BY_EXTENSION.get(extension))
                .orElse(OCTET_STREAM);
    }

    /**
     * Return the mime type of the given filename based on the file extension,
     * defaults to "application/octet-stream" if the extension is missing or unknown.
     *
     * @return the mime type of the given filename based on the file extension
     */
    public static MediaType mediaTypeOf(String filename) {
        int dotPosition = filename.lastIndexOf('.');
        if (dotPosition > 0) {
            return mediaTypeForExtension(filename.substring(dotPosition + 1));
        }
        return OCTET_STREAM;
    }
}
