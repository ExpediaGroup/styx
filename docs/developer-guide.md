
# Developer Manual

## 1.1 Introduction

In this guide we will walk through how to build applications on top of Styx.


## 1.2 System Requirements

Building Styx requires Java 23.

The build system requires Apache Maven. The Styx team uses Maven version 3
for automated continuous integration builds. On Mac OSX, a version installed
by HomeBrew is satisfactory.


## 1.3 Building Styx

To clone and build styx afresh:

    $ git clone https://github.com/HotelsDotCom/styx.git
    $ cd styx
    $ make release-no-tests

This produces a file `distribution/target/styx.zip`.

If `styx-test/` already exists, remove it first to avoid conflicts on dependencies:

    $ rm -rf styx-test

Let's unzip this file:

    $ unzip distribution/target/styx-<VERSION>.zip -d styx-test


## 1.4 Running Styx

Now, start the styx server:

    $ APP_originsFile=`pwd`/styx-test/styx/conf/origins.yml ./styx-test/styx/bin/startup
    Running Styx startup...
    ...
    /plenty of output/
    ...

Note `APP_originsFile` specifies a Styx origins file path. It substitutes a placeholder
in the default server configuration file `styx-test/styx/conf/default.yml`. Normally
you would just specify the path in a server configuration itself, either as an absolute
path, or a path relative to the Styx server working directory. But for now we'll just pass
it in to start the server from your project working directory.

Finally you will see:

    INFO  2017-10-13 14:56:24 [c.h.s.s.n.NettyServer] [Admin-Boss-0-Thread] - server connector class com.hotels.styx.server.netty.WebServerConnectorFactory$WebServerConnector bound successfully on port 9000
    INFO  2017-10-13 14:56:24 [c.h.s.StyxServer] [main] - Started styx server in 68ms


Once Styx server has started, the admin dashboard is available at http://localhost:9000/.
You are presented with the Styx admin root interface which shows various
configurations, health check endpoints and metrics data. Click on *Dashboard*
link to open up the internal Styx dashboard. You will see some example
origins configured. Note that none of them are active since those servers
have not been started.

To stop Styx server, kill it by hitting `Ctrl-C` in the terminal window.


## 1.5 Building and Starting Styx with Test Origins

Let's point Styx to some stub services. Start Styx as follows:

    $ make start-with-origins

This will rebuild the Styx project, and start it with some test origins. An *Origin* in
Styx jargon refers to an instance of a *Backend Service*. A Backend Service is a
collection of servers. An origin is just one of those servers.

Now take some time to explore the admin interface again. Via the admin interface, you can
explore:

 - The Dashboard
 - Server settings
 - Settings for Backend Services
 - Backend service statuses
 - Styx and Origins Metrics

Styx logs are located in `styx-test/logs/styx.log`. You can see them using:

    $ cat styx-test/logs/styx.log

And see the startup logs using:

    $ cat styx-test/logs/startup.log

## On Developing Plugins

 - [Styx API Overview](./developer-guide/api-overview.md) - Styx programming API overview.

 - [Plugins](./developer-guide/plugins.md) - How to develop plugins.

 - [Plugins Scenarios](./developer-guide/plugins-scenarios.md) - How to implement different traffic handling scenarios in plugins.

