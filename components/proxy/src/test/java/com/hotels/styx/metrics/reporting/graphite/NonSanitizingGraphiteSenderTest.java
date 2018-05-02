package com.hotels.styx.metrics.reporting.graphite;

import com.hotels.styx.support.dns.MockNameService;
import com.hotels.styx.support.server.FakeHttpServer;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import sun.net.spi.nameservice.NameService;

import java.net.InetAddress;
import java.security.Security;

import static com.hotels.styx.api.support.HostAndPorts.freePort;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NonSanitizingGraphiteSenderTest {
    private FakeHttpServer server;
    private InetAddress localhost;
    private int port;

    static {
        // Disable DNS cache:
        Security.setProperty("networkaddress.cache.ttl", "0");
        Security.setProperty("networkaddress.cache.negative.ttl", "0");
    }

    @BeforeClass
    public void setUp() throws Exception {
        port = freePort();
        server = new FakeHttpServer(port).start();
        localhost = InetAddress.getByName("localhost");
    }

    @AfterClass
    public void tearDown() {
        server.stop();
        MockNameService.get().unset();
    }

    @Test
    public void resolvesHostnamesAtEachAttempt() throws Exception {
        NonSanitizingGraphiteSender sender = new NonSanitizingGraphiteSender("localhost", port);

        NameService delegate = mock(NameService.class);
        when(delegate.lookupAllHostAddr(anyString()))
                .thenReturn(new InetAddress[]{localhost})
                .thenReturn(new InetAddress[]{localhost});

        MockNameService.get().setDelegate(delegate);

        sender.connect();
        sender.close();
        verify(delegate).lookupAllHostAddr(eq("localhost"));

        sender.connect();
        verify(delegate, times(2)).lookupAllHostAddr(eq("localhost"));
        sender.close();
    }
}