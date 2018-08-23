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
package com.hotels.styx.support.origins;


import com.hotels.styx.StartupConfig;
import com.hotels.styx.StyxConfig;
import com.hotels.styx.api.Resource;
import com.hotels.styx.api.extension.Origin;
import com.hotels.styx.api.configuration.Configuration;
import com.hotels.styx.applications.BackendServices;
import com.hotels.styx.infrastructure.configuration.ConfigurationParser;
import com.hotels.styx.infrastructure.configuration.yaml.YamlConfiguration;
import com.hotels.styx.server.HttpConnectorConfig;
import com.hotels.styx.server.HttpServer;
import com.hotels.styx.server.ServerEventLoopFactory;
import com.hotels.styx.server.StandardHttpRouter;
import com.hotels.styx.server.netty.NettyServerBuilder;
import com.hotels.styx.server.netty.WebServerConnectorFactory;
import com.hotels.styx.server.netty.eventloop.PlatformAwareServerEventLoopFactory;
import org.slf4j.Logger;

import java.util.List;
import java.util.Properties;

import static com.hotels.styx.StartupConfig.newStartupConfigBuilder;
import static com.hotels.styx.applications.yaml.YamlApplicationsProvider.loadApplicationsFrom;
import static com.hotels.styx.infrastructure.configuration.ConfigurationSource.configSource;
import static com.hotels.styx.infrastructure.configuration.yaml.YamlConfigurationFormat.YAML;
import static com.hotels.styx.server.netty.eventloop.ServerEventLoopFactories.memoize;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
import static org.slf4j.LoggerFactory.getLogger;

public class StyxOriginsStarterApp {
    private static final Logger LOG = getLogger(StyxOriginsStarterApp.class);

    private static final ServerEventLoopFactory serverEventLoopFactory = memoize(new PlatformAwareServerEventLoopFactory("Origin-Starter", 0, 0));

    private final List<HttpServer> originsServers;

    public static StyxOriginsStarterApp originsStarterApp(String applicationsConfigLocation) {
        return new StyxOriginsStarterApp(loadApplicationsFrom(applicationsConfigLocation));
    }

    public StyxOriginsStarterApp(BackendServices backendServices) {
        this.originsServers = stream(backendServices.spliterator(), false)
                .flatMap(application -> application.origins().stream())
                .map(StyxOriginsStarterApp::createHttpServer)
                .collect(toList());
    }

    private static HttpServer createHttpServer(Origin origin) {
        LOG.info("creating server for {}", origin.host());

        return new NettyServerBuilder()
                .name(origin.hostAsString())
                .setServerEventLoopFactory(serverEventLoopFactory)
                .setHttpConnector(new WebServerConnectorFactory().create(new HttpConnectorConfig(origin.host().getPort())))
                .httpHandler(new StandardHttpRouter().add("/*", new AppHandler(origin)))
                .build();
    }

    public static void main(String[] args) {
        // This is a bit quick & dirty, but it's only for the dummy origins process, so it won't affect Styx itself.
        System.setProperty("STYX_HOME", args[0]);

        StartupConfig startupConfig = newStartupConfigBuilder()
                .styxHome(args[0])
                .configFileLocation(args[1])
                .build();

        if (args.length < 4) {
            Configuration configurationFromFile = config(startupConfig.configFileLocation(), System.getProperties());

            StyxConfig config = new StyxConfig(startupConfig, configurationFromFile);

            String originsFile = config.applicationsConfigurationPath().orElseThrow(() ->
                    new IllegalStateException("Cannot start origins: No origins file specified"));

            originsStarterApp(originsFile).run();
        } else {
            originsStarterApp(args[3]).run();
        }
    }

    private static Configuration config(Resource resource, Properties properties) {
        return new ConfigurationParser.Builder<YamlConfiguration>()
                .overrides(properties)
                .format(YAML)
                .build()
                .parse(configSource(resource));
    }

    public void run() {
        originsServers.forEach(server -> server.startAsync().awaitRunning());
    }

    public void stop() {
        originsServers.forEach(server -> server.stopAsync().awaitTerminated());
    }
}
