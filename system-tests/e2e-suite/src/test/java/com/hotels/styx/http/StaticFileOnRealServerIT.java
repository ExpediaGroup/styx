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
package com.hotels.styx.http;

import com.google.common.io.Files;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.client.SimpleNettyHttpClient;
import com.hotels.styx.client.connectionpool.CloseAfterUseConnectionDestination;
import com.hotels.styx.server.HttpServer;
import com.hotels.styx.server.handlers.StaticFileHandler;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetSocketAddress;

import static com.hotels.styx.support.api.matchers.HttpResponseBodyMatcher.hasBody;
import static com.hotels.styx.api.support.HostAndPorts.freePort;
import static com.hotels.styx.server.HttpServers.createHttpServer;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static org.hamcrest.MatcherAssert.assertThat;

public class StaticFileOnRealServerIT {
    private final com.hotels.styx.api.HttpClient client = new SimpleNettyHttpClient.Builder()
            .connectionDestinationFactory(new CloseAfterUseConnectionDestination.Factory())
            .build();

    private HttpServer webServer;
    private File dir;
    private String serverEndpoint;

    private static String toHostAndPort(InetSocketAddress address) {
        return address.getHostName() + ":" + address.getPort();
    }

    @BeforeClass
    public void startServer() {
        dir = Files.createTempDir();
        webServer = createHttpServer(freePort(), new StaticFileHandler(dir));
        webServer.startAsync().awaitRunning();
        serverEndpoint = toHostAndPort(webServer.httpAddress());
    }


    @AfterClass
    public void stopServer() {
        dir.delete();
        webServer.stopAsync().awaitTerminated();
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
        HttpResponse response = execute(request);
        assertThat(response, hasBody("Hello World"));
    }

    private HttpResponse execute(HttpRequest request) {
        return client.sendRequest(request).toBlocking().first();
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
