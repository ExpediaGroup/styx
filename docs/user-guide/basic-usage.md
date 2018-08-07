# Starting Styx

## System Requirements

Running Styx requires Java 1.8. It can be run with 1.8.0_45 or later versions. Earlier maintenance
releases or Java 1.9 may work, but are not guaranteed to.

## Downloading Styx from release

In order to download Styx from release, go to [Styx releases](https://github.com/HotelsDotCom/styx/releases) and download the zip file for the version you want, making sure you choose the file corresponding to whichever operating system you are using.
Alternatively, you can build styx from source code.

## Building Styx from source code 
 
 In order to build styx from source code, Java 8 (1.8.0_45 or later) is required. 
 The build system requires Apache Maven. The Styx CI pipeline uses Maven version 3.2.1 
 for the automated continuous integration builds. On Mac OSX, a version installed 
 by HomeBrew is satisfactory.
 
 The instructions are:
 1. Download the code from github
 2. Generate the artifact using maven.
```
git clone https://github.com/HotelsDotCom/styx.git
cd styx && mvn package
```

## Step 1: Installing Styx:

Styx comes pre-packaged in a zip file. Extract the zip file to a directory of choice:

    $ unzip styx-<VERSION>-<OS>-<PLATFORM>.zip 

This creates a subdirectory called `styx-<VERSION>` that contains Styx binaries and a set 
 of configuration examples. Example configuration files can be found in `styx-<VERSION>/conf`
 subdirectory. There are some files worth noticing:
 
* `default.yml`   - A Styx server configuration file. It specifies the Styx server
                      port numbers and other application configuration.
                      
* `conf/origins` subdirectory containing examples of Styx origins configuration 
 files. A styx origins configuration file specifies the backend services for Styx.
 Especially, have a look at `origins-configuration-documentation.yml`. It explains 
 all aspects of origins configuration settings (you can see more details in [Backend services and origins](configure-origins.md)).

                       
* `logback.xml`      - Logging configuration file. There are more examples in the `conf/logback`
                      subdirectory.
                      
* `styx-env.sh`     - JVM settings file. See "Configuring JVM Settings" section below.



## Step 2: Start Styx:

To start Styx, run the startup script from *bin/* directory, passing in the
configuration file as an argument:

    $ ./bin/startup conf/env-development/styx-config.yml

This starts Styx according to the configuration specified in *staging.yml* file.

You also can specify the configuration via the `STYX_CONFIG` environment
variable. For example:

    STYX_CONFIG=conf/env-development/styx-config.yml ./bin/startup

If the configuration file is not specified as a command line argument or as an environment 
variable, Styx by default attempts to load its configuration from *$STYX_HOME/conf/default.yaml*.

Once Styx has started up, it displays the Styx logo banner, followed by information
about the port numbers it is listening on.

    INFO  2017-06-27 08:20:48 [c.h.s.s.n.NettyServer] [main] - starting services
    INFO  2017-06-27 08:20:48 [c.h.s.s.n.NettyServer] [Proxy-Boss-0-Thread] - server connector class com.hotels.styx.proxy.ProxyConnectorFactory$ProxyConnector bound successfully on port 8080
    INFO  2017-06-27 08:20:48 [c.h.s.s.n.NettyServer] [Proxy-Boss-0-Thread] - server connector class com.hotels.styx.proxy.ProxyConnectorFactory$ProxyConnector bound successfully on port 8443
    INFO  2017-06-27 08:20:48 [c.h.s.s.n.NettyServer] [main] - starting services
    INFO  2017-06-27 08:20:48 [c.h.s.s.n.NettyServer] [Admin-Boss-0-Thread] - server connector class com.hotels.styx.server.netty.WebServerConnectorFactory$WebServerConnector bound successfully on port 9000
    INFO  2017-06-27 08:20:48 [c.h.s.StyxServer] [main] - Started styx server in 63ms

This tells you that the application has successfully started, and that proxy server is listening ports 8080 for HTTP, 
8443 for HTTPS, and that admin interface has started on port 9000 (HTTPS).
    
    
# Configuring JVM Settings

There is a file *conf/styx-env.sh* that is sourced in from *bin/startup* when the startup 
script runs. Use this file as a "startup hook" to initialise Styx before it gets started. 
You can configure JVM settings, system properties, and any other installation that may be 
necessary before startup.

The styx startup script accepts an `-e` or `--env` command line option for specifying an alternative
Styx environment file:

    $ ./bin/startup -e conf/env-development/styx-env.sh

The custom environment file can also be specified via the *STYX_ENV_FILE* environment variable.
     

# Configuring Logging

Styx uses [Logback style](https://logback.qos.ch/manual/index.html) configuration files for its logger 
configuration.

By default, the logback file is loaded from *$STYX_HOME/conf/logback.xml*. You can specify an alternative
logging configuration file using `-l` or `--logback` command line options:

    $ ./bin/startup --logback conf/env-development/logback.xml
    
You can also specify this via environment: *STYX_LOG_CONFIG*.


# Startup Script Reference

## Command Line Options

Usage: *startup [options] [CONFIG-FILE]*

*CONFIG-FILE* is a path to the Styx configuration file.
 
Command line options are:
        
    -l <FILE>
    --logback <FILE>
  
        Reads the logging configuration from FILE.
  
    -e <FILE>
    --env <FILE>
  
        Reads the environment settings from FILE.
  
    -h 
    --help
  
        Display this help message.



## Environment Variables

    STYX_CONFIG=<FILE>
     
        Specifies the Styx configuration file.
        
    STYX_LOG_CONFIG=<FILE>
    
        Specifies Styx Logback configuration file.=

    STYX_ENV_FILE=<FILE>
    
        Reads the environment settings from FILE.
        
    STYX_LOG_OUTPUT=<DIR>
    
        Specifies an output directory for Styx log messages.
        
    APP_<NAME>=<VALUE>
    
        Any environment variable starting with APP_ prefix is interpreted
        as a system property for Styx application. The startup script removes
        the APP_ prefix and passes the remaining <NAME>=<VALUE> tuple as a system
        property for Styx.
        
    JVM_<SETTING>=<VALUE>
    
        Any environment variable starting with <JVM_> declares a command line
        option for the underlying Java Virtual Machine. 
        The startup throws away the JVM_<SETTING> part, and passes the
        <VALUE>, verbatim, as a command line option for the JVM.
        
        

