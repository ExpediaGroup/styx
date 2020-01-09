/*
  Copyright (C) 2013-2020 Expedia Inc.

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


import com.hotels.styx.StyxObjectRecord;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.WebServiceHandler;
import com.hotels.styx.api.configuration.ObjectStore;
import com.hotels.styx.api.extension.service.spi.StyxService;

import java.util.stream.Collectors;

import static com.google.common.net.MediaType.HTML_UTF_8;
import static com.hotels.styx.admin.AdminServerBuilder.adminEndpointPath;
import static com.hotels.styx.admin.AdminServerBuilder.adminPath;
import static com.hotels.styx.api.HttpHeaderNames.CONTENT_TYPE;
import static com.hotels.styx.api.HttpResponse.response;
import static com.hotels.styx.api.HttpResponseStatus.OK;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Returns a simple HTML page with a list of Providers, and the set of available admin endpoints for each.
 */
public class ProviderListHandler implements WebServiceHandler {

    private static final String HTML_TEMPLATE = ""
            + "<!DOCTYPE html>\n"
            + "<html>\n"
            + "<head>\n"
            + "<meta charset=\"UTF-8\">\n"
            + "<title>%s</title>\n"
            + "</head>\n"
            + "\n"
            + "<body>\n"
            + "%s\n"
            + "</body>\n"
            + "</html>";

    private static final String TITLE = "List of Providers";

    private final ObjectStore<StyxObjectRecord<StyxService>> providerDb;

    /**
     * Create a new handler linked to a provider object store.
     * @param providerDb the provider store.
     */
    public ProviderListHandler(ObjectStore<StyxObjectRecord<StyxService>> providerDb) {
        this.providerDb = providerDb;
    }

    @Override
    public Eventual<HttpResponse> handle(HttpRequest request, HttpInterceptor.Context context) {
        String providerList = providerDb.entrySet().stream()
                .map(entry -> htmlForProvider(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining());
        String html = String.format(HTML_TEMPLATE, TITLE, h2(TITLE) + providerList);
        return Eventual.of(response(OK)
                .body(html, UTF_8)
                .addHeader(CONTENT_TYPE, HTML_UTF_8.toString())
                .build());
    }

    private static String htmlForProvider(String name, StyxObjectRecord<StyxService> provider) {
        String endpointList = provider.getStyxService()
                .adminInterfaceHandlers(adminPath("providers", name))
                .keySet()
                .stream()
                .map(relativePath -> adminEndpointPath("providers", name, relativePath))
                .map(absolutePath -> li(link(absolutePath, absolutePath)))
                .collect(Collectors.joining());
        return h3(name + " (" + provider.getType() + ")")
                + "<ul>\n"
                + endpointList
                + "</ul>\n";
    }

    private static String h2(String content) {
        return String.format("<h2>%s</h2>\n", content);
    }

    private static String h3(String content) {
        return String.format("<h3>%s</h3>\n", content);
    }

    private static String link(String href, String text) {
        return String.format("<a href=\"%s\">%s</a>", href, text);
    }

    private static String li(String content) {
        return String.format("<li>%s</li>\n", content);
    }
}
