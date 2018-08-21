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
package com.hotels.styx.metrics.reporting.graphite;

import com.hotels.styx.support.dns.LocalNameServiceDescriptor;
import com.hotels.styx.support.server.FakeHttpServer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import sun.net.spi.nameservice.NameService;

import java.net.InetAddress;

import static com.hotels.styx.common.HostAndPorts.freePort;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NonSanitizingGraphiteSenderTest {
    private FakeHttpServer server;
    private InetAddress graphiteServerAddress;
    private int port;

    @BeforeClass
    public void setUp() throws Exception {
        port = freePort();
        server = new FakeHttpServer(port).start();
        graphiteServerAddress = InetAddress.getByName("localhost");
    }

    @AfterClass
    public void tearDown() {
        server.stop();
        LocalNameServiceDescriptor.get().unset();
    }

    @Test
    public void resolvesHostnamesAtEachAttempt() throws Exception {
        NonSanitizingGraphiteSender sender = new NonSanitizingGraphiteSender("localhost", port);

        NameService delegate = mock(NameService.class);
        when(delegate.lookupAllHostAddr(eq("localhost")))
                .thenReturn(new InetAddress[]{graphiteServerAddress})
                .thenReturn(new InetAddress[]{graphiteServerAddress});

        LocalNameServiceDescriptor.get().setDelegate(delegate);

        sender.connect();
        sender.close();
        verify(delegate).lookupAllHostAddr(eq("localhost"));

        sender.connect();
        verify(delegate, times(2)).lookupAllHostAddr(eq("localhost"));
        sender.close();
    }
}