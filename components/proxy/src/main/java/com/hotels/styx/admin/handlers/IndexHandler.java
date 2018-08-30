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
package com.hotels.styx.admin.handlers;

import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.common.http.handler.BaseHttpHandler;

import static com.google.common.net.HttpHeaders.CONTENT_LANGUAGE;
import static com.google.common.net.MediaType.HTML_UTF_8;
import static com.hotels.styx.api.FullHttpResponse.response;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_TYPE;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;

/**
 * Generates an index page for the registered handlers.
 */
public class IndexHandler extends BaseHttpHandler {
    private static final String INDEX_LISTING_FORMAT_STRING =
            "<html><body><ol style='list-style-type: none; padding-left: 0px; margin-left: 0px;'>%s</ol></body></html>";

    private final String html;

    /**
     * Constructs an instance with given links.
     *
     * @param links links to display
     */
    public IndexHandler(Iterable<Link> links) {
        this.html = generateHtml(links);
    }

    private static String generateHtml(Iterable<Link> links) {
        return buildIndexContent(
                stream(links.spliterator(), false)
                        .map(Link::toString)
                        .collect(toList()));
    }

    @Override
    protected HttpResponse doHandle(HttpRequest request) {
        return response(OK)
                .addHeader(CONTENT_TYPE, HTML_UTF_8.toString())
                .header(CONTENT_LANGUAGE, "en")
                .body(html, UTF_8)
                .build()
                .toStreamingResponse();
    }

    private static String buildIndexContent(Iterable<String> links) {
        StringBuilder builder = new StringBuilder();
        for (String link : links) {
            builder.append("<li>")
                    .append(link)
                    .append("</li>");
        }
        return format(INDEX_LISTING_FORMAT_STRING, builder);
    }

    /**
     * A link to be displayed in the IndexHandler.
     */
    public static final class Link implements Comparable<Link> {
        private final String label;
        private final String path;

        private Link(String label, String path) {
            this.label = requireNonNull(label);
            this.path = requireNonNull(path);
        }

        /**
         * Create a link.
         *
         * @param label the label to display as the link
         * @param path  the path that the link should lead to
         * @return a new link
         */
        public static Link link(String label, String path) {
            return new Link(label, path);
        }

        @Override
        public int compareTo(Link o) {
            int comparison = label.compareTo(o.label);

            if (comparison == 0) {
                return path.compareTo(o.path);
            }

            return comparison;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Link link = (Link) o;

            if (!label.equals(link.label)) {
                return false;
            }
            if (!path.equals(link.path)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = label.hashCode();
            result = 31 * result + path.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return format("<a href='%s'>%s</a>", path, label);
        }
    }
}
