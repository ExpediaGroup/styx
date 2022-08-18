/*
  Copyright (C) 2013-2022 Expedia Inc.

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

import com.hotels.styx.api.extension.service.RewriteConfig;
import com.hotels.styx.proxy.ProxyServerConfig;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static com.hotels.styx.support.ResourcePaths.fixturesHome;
import static com.hotels.styx.support.matchers.IsOptional.isValue;
import static java.lang.Runtime.getRuntime;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class StyxConfigTest {

    private static final int HALF_OF_AVAILABLE_PROCESSORS = getRuntime().availableProcessors() / 2;

    private static final String REWRITES_FILE = fixturesHome() + "conf/rewrites.yml";

    final String yaml = "" +
            "proxy:\n" +
            "  connectors:\n" +
            "    http:\n" +
            "      port: 80\n" +
            "  maxHeaderSize: 8193\n" +
            "  maxChunkSize: 8193\n" +
            "metrics:\n" +
            "  reporting:\n" +
            "   prefix: \"STYXHPT\"\n";

    final StyxConfig styxConfig = StyxConfig.fromYaml(yaml, false);
    final ProxyServerConfig serverConfig = styxConfig.proxyServerConfig();

    @Test
    public void initializesFromConfigurationSource() {
        assertThat(serverConfig.httpConnectorConfig().get().port(), is(80));
        assertThat(serverConfig.maxHeaderSize(), is(8193));
        assertThat(serverConfig.maxChunkSize(), is(8193));
        assertThat(styxConfig.get("metrics.reporting.prefix", String.class).get(), is("STYXHPT"));
    }

    @Test
    public void readsListsOfHeadersAndCookiesToHide() {
        String yaml =
                "request-logging:\n" +
                "  hideHeaders:\n" +
                "    - header1\n" +
                "    - header2\n" +
                "  hideCookies:\n" +
                "    - cookie1\n" +
                "    - cookie2\n";

        StyxConfig styxConfig = StyxConfig.fromYaml(yaml, false);
        List<String> headersToHide = styxConfig.get("request-logging.hideHeaders", List.class).orElse(Collections.emptyList());
        List<String> cookiesToHide = styxConfig.get("request-logging.hideCookies", List.class).orElse(Collections.emptyList());

        assertThat(headersToHide.get(0), is("header1"));
        assertThat(headersToHide.get(1), is("header2"));
        assertThat(cookiesToHide.get(0), is("cookie1"));
        assertThat(cookiesToHide.get(1), is("cookie2"));
    }

    @Test
    public void readsBossThreadsCountFromConfigurationSource() {
        String yaml = "" +
                "proxy:\n" +
                "  bossThreadsCount: 32\n";

        StyxConfig styxConfig = StyxConfig.fromYaml(yaml, false);
        assertThat(styxConfig.proxyServerConfig().bossThreadsCount(), is(32));
    }

    @Test
    public void readsTheDefaultValueForBossThreadsCountIfNotConfigured() {
        assertThat(styxConfig.proxyServerConfig().bossThreadsCount(), is(HALF_OF_AVAILABLE_PROCESSORS));
    }

    @Test
    public void readsWorkerThreadsCountFromConfigurationSource() {
        String yaml = "" +
                "proxy:\n" +
                "  workerThreadsCount: 32\n";

        StyxConfig styxConfig = StyxConfig.fromYaml(yaml, false);
        assertThat(styxConfig.proxyServerConfig().workerThreadsCount(), is(32));
    }

    @Test
    public void readsTheDefaultValueForWorkerThreadsCountIfNotConfigured() {
        assertThat(serverConfig.workerThreadsCount(), is(HALF_OF_AVAILABLE_PROCESSORS));
    }

    @Test
    public void readsTheDefaultValueForWorkerThreadsCountSetToZero() {
        String yaml = "" +
                "proxy:\n" +
                "  workerThreadsCount: 0\n";

        StyxConfig styxConfig = StyxConfig.fromYaml(yaml, false);
        assertThat(styxConfig.proxyServerConfig().workerThreadsCount(), is(HALF_OF_AVAILABLE_PROCESSORS));
    }

    @Test
    public void readsClientWorkerThreadsCountFromConfigurationSource() {
        String yaml = "" +
                "proxy:\n" +
                "  clientWorkerThreadsCount: 32\n";

        StyxConfig styxConfig = StyxConfig.fromYaml(yaml, false);
        assertThat(styxConfig.proxyServerConfig().clientWorkerThreadsCount(), is(32));
    }

    @Test
    public void readsTheDefaultValueForClientWorkerThreadsCountIfNotConfigured() {
        assertThat(styxConfig.proxyServerConfig().clientWorkerThreadsCount(), is(HALF_OF_AVAILABLE_PROCESSORS));
    }

    @Test
    public void readsTheDefaultValueForClientWorkerThreadsCountSetToZero() {
        String yaml = "" +
                "proxy:\n" +
                "  clientWorkerThreadsCount: 0\n";

        StyxConfig styxConfig = StyxConfig.fromYaml(yaml, false);
        assertThat(styxConfig.proxyServerConfig().clientWorkerThreadsCount(), is(HALF_OF_AVAILABLE_PROCESSORS));
    }

    @Test
    public void readsReadTimeoutValue() {
        String yaml = "" +
                "proxy:\n" +
                "  requestTimeoutMillis: 10000\n";

        StyxConfig styxConfig = StyxConfig.fromYaml(yaml, false);

        assertThat(styxConfig.get("proxy.requestTimeoutMillis", Integer.class), isValue(10000));
        assertThat(styxConfig.proxyServerConfig().requestTimeoutMillis(), is(10000));
    }

    @Test
    public void readsListOfRewriteGroups() {
        String yaml = "" +
            "rewrites:\n" +
            "  someGroup:\n" +
            "    - urlPattern: \"/foo/(.*)\"\n" +
            "      replacement: \"/bar/$1\"\n" +
            "    - urlPattern: \"/ping/(.*)\"\n" +
            "      replacement: \"/pong/$1\"\n" +
            "  anotherGroup:\n" +
            "    - urlPattern: \"/hey/(.*)\"\n" +
            "      replacement: \"/hi/$1\"\n";

        StyxConfig styxConfig = StyxConfig.fromYaml(yaml, false);

        assertThat(styxConfig.get("rewrites.someGroup[0].urlPattern", String.class), isValue("/foo/(.*)"));
        assertThat(styxConfig.get("rewrites.someGroup[0].replacement", String.class), isValue("/bar/$1"));
        assertThat(styxConfig.get("rewrites.someGroup[1].urlPattern", String.class), isValue("/ping/(.*)"));
        assertThat(styxConfig.get("rewrites.someGroup[1].replacement", String.class), isValue("/pong/$1"));
        assertThat(styxConfig.get("rewrites.anotherGroup[0].urlPattern", String.class), isValue("/hey/(.*)"));
        assertThat(styxConfig.get("rewrites.anotherGroup[0].replacement", String.class), isValue("/hi/$1"));
        assertThat(styxConfig.rewriteGroupsConfig().get("someGroup"), 
            is(List.of(
                new RewriteConfig("/foo/(.*)", "/bar/$1"),
                new RewriteConfig("/ping/(.*)", "/pong/$1")
            ))
        );
        assertThat(styxConfig.rewriteGroupsConfig().get("anotherGroup"),
            is(List.of(
                new RewriteConfig("/hey/(.*)", "/hi/$1")
            ))
        );
    }

    @Test
    public void readsRewriteFile() {
        String yaml = "" +
            "rewrites:\n" +
            "  configFile: \"" + REWRITES_FILE + "\"\n";

        StyxConfig styxConfig = StyxConfig.fromYaml(yaml, false);

        assertThat(styxConfig.get("rewrites.configFile", String.class), isValue(REWRITES_FILE));
        assertThat(styxConfig.rewriteGroupsConfig().get("someGroup"),
            is(List.of(
                new RewriteConfig("/foo/(.*)", "/bar/$1"),
                new RewriteConfig("/ping/(.*)", "/pong/$1")
            ))
        );
        assertThat(styxConfig.rewriteGroupsConfig().get("anotherGroup"),
            is(List.of(
                new RewriteConfig("/hey/(.*)", "/hi/$1")
            ))
        );
    }
}
