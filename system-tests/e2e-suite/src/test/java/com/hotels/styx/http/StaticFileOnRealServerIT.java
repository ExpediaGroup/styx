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
package com.hotels.styx.http;

import com.google.common.io.Files;
import com.google.common.util.concurrent.Service;
import com.hotels.styx.InetServer;
import com.hotels.styx.StyxServers;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.client.HttpClient;
import com.hotels.styx.client.StyxHttpClient;
import com.hotels.styx.server.handlers.StaticFileHandler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetSocketAddress;

import static com.hotels.styx.api.HttpMethod.GET;
import static com.hotels.styx.common.StyxFutures.await;
import static com.hotels.styx.server.HttpServers.createHttpServer;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@TestInstance(PER_CLASS)
public class StaticFileOnRealServerIT {
    private final HttpClient client = new StyxHttpClient.Builder().build();

    private InetServer webServer;
    private Service guavaService;
    private File dir;
    private String serverEndpoint;

    private static String toHostAndPort(InetSocketAddress address) {
        return address.getHostName() + ":" + address.getPort();
    }

    @BeforeAll
    public void startServer() {
        dir = Files.createTempDir();
        webServer = createHttpServer(0, new StaticFileHandler(dir));
        guavaService = StyxServers.toGuavaService(webServer);
        guavaService.startAsync().awaitRunning();
        serverEndpoint = toHostAndPort(webServer.inetAddress());
    }


    @AfterAll
    public void stopServer() {
        dir.delete();
        guavaService.stopAsync().awaitTerminated();
    }

    @Test
    public void shouldWorkInRealServer() throws Exception {
        writeFile("index.html", "Hello World");
        writeFile("foo.js", "some js");
        mkdir("some/dir");
        writeFile("some/dir/content1.txt", "some txt");

        HttpRequest request = new HttpRequest.Builder(GET, "/index.html")
                .header("Host", serverEndpoint)
                .build();

        HttpResponse response = await(client.sendRequest(request));
        assertThat(response.bodyAs(UTF_8), is("Hello World"));
    }

    private void mkdir(String path) {
        new File(dir, path).mkdirs();
    }

    /**
     * Write text file to FileSystem.
     */
    private void writeFile(String path, String contents) throws IOException {
        File file = new File(dir, path);

        try (FileWriter writer = new FileWriter(file)) {
            writer.write(contents);
        }
    }

}
