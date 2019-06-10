## Configuring Styx

Styx supports a YAML file-based configuration. The path to the YAML file can be passed
as a parameter to the startup script (`startup <CONFIG_FILE>`)
or configured via the environment variable `STYX_CONFIG`.

By default, the file is read from `conf/default.yaml`.

Additionally, all these properties can be overwritten
using environment variables with the same name as the property.

### Example styx-config

```yaml
# A string uniquely identifying the host running the application, must be different for all running instances of the application
# the default value is suitable only for non clustered environments
jvmRouteName: "${jvm.route:noJvmRouteSet}"

proxy:
  connectors:
    http:
      # Port for accessing the proxy server over HTTP.
      port: 8080
    https:
      # Port for accessing the proxy server over HTTPS.
      port: 8443
      # Implementation of SSL functionality, can be JDK or OPENSSL.
      sslProvider: OPENSSL
      # Timeout for the cached SSL session objects.
      sessionTimeoutMillis: 300000
      # Size of the cache used for storing SSL session objects.
      sessionCacheSize: 20000
      # The TLS protocol versions to enable.
      protocols:
        - TLSv1.2
      # The cipher suites to enable, in the order of preference.
      cipherSuites:
       - TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384
       - TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256
       - TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
       - TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
  # Boss threads are those establishing new channels.
  #  If set to 0, availableProcessors / 2 threads will be used
  bossThreadsCount: 1
  # styx client worker threads are those performing all the asynchronous I/O operation to the backend origins.
  # If set to 0, availableProcessors / 2 threads will be used
  clientWorkerThreadsCount: 0
  # Worker threads are those performing all the asynchronous I/O operation on the inbound channel.
  # If set to 0, availableProcessors / 2 threads will be used
  workerThreadsCount: 0
  tcpNoDelay: true
  nioReuseAddress: true
  nioKeepAlive: true
  maxInitialLength: 4096
  maxHeaderSize: 65536
  maxChunkSize: 8192
  maxContentLength: 65536
  # Time in milliseconds Styx Proxy Service waits for an incoming request from client
  # before replying with 408 Request Timeout.
  requestTimeoutMillis: 12000
  # A timeout for idle persistent connections, in milliseconds.
  keepAliveTimeoutMillis: 120000
  maxConnectionsCount: 4000


admin:
  connectors:
    http:
      # Server port for Styx admin interface.
      port: 9000
  # Number of threads for handling incoming connections on admin interface. 0 -> availableProcessors / 2 threads will be used.
  bossThreadsCount: 1
  # Number of worker threads for admin interface
  # Worker threads are those performing all the asynchronous I/O operation on the inbound channel.
  # 0 -> availableProcessors / 2 threads will be used
  workerThreadsCount: 2
  tcpNoDelay: true
  nioReuseAddress: true
  nioKeepAlive: true
  maxInitialLength: 4096
  maxHeaderSize: 8192
  maxChunkSize: 8192
  maxContentLength: 65536
  
  # Whether to cache the generated JSON for the /admin/metrics and /admin/jvm pages
  metricsCache:
    enabled: true
    expirationMillis: 10000

# A list of services that Styx should run upon starting. Any service that can be constructed by a factory extending
# com.hotels.styx.api.configuration.ServiceFactory can be included here 
services:
  factories:
    backendServiceRegistry:
      class: "com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistry$Factory"
      config: 
        originsFile: "${STYX_HOME}/conf/env-development/origins.yaml"
    graphite:
      class: "com.hotels.styx.metrics.reporting.graphite.GraphiteReporterServiceFactory"
      config:
        prefix: "my.metrics"
        # Host of the Graphite endpoint.
        host: "destination.host"
        # Port of the Graphite endpoint.
        port: 2003
        # Graphite reporting interval in milliseconds
        intervalMillis: 15000
    jmx:
      class: "com.hotels.styx.metrics.reporting.jmx.JmxReporterServiceFactory"
      config:
        domain: "com.hotels.styx"

retrypolicy:
  policy:
    factory:
      class: "com.hotels.styx.client.retry.RetryPolicyFactory"
      config: {count: 2}

loadBalancing:
  strategy: #Check load balancing documentation for all the possible strategies
    factory: {class: "com.hotels.styx.client.loadbalancing.strategies.PowerOfTwoStrategy$Factory"}

originRestrictionCookie: restrict_origins

request-logging:
  # Enable logging of requests and responses (with requestId to match them up).
  # Logs are produced on server and origin side, so there is an information on 
  # how the server-side (inbound) and origin-side (outbound) request/response look like.
  # In long format log entry contains additionally headers and cookies. 
  inbound:
    enabled: ${REQUEST_LOGGING_INBOUND_ENABLED:false}
    longFormat: ${REQUEST_LOGGING_INBOUND_LONG_FORMAT:false}
  outbound:
    enabled: ${REQUEST_LOGGING_OUTBOUND_ENABLED:false}
    longFormat: ${REQUEST_LOGGING_OUTBOUND_LONG_FORMAT:false}

# Configures the names of the headers that Styx adds to messages it proxies (see headers.md)
# If not configured, defaults will be used.
styxHeaders:
  styxInfo:
    name: "X-Styx-Info"
    format: "{INSTANCE};{REQUEST_ID}"
  originId:
    name: "X-Styx-Origin-Id"
  requestId:
    name: "X-Styx-Request-Id"
    
# Enables request tracking. This is a debugging feature that shows information about
# each proxied request. Accepts a boolean value (true/false).
requestTracking: false

url:
  encoding:
    # Determines which characters should be escaped if found in the URL.
    unwiseCharactersToEncode: "|,;,{,}"

# Configure plugins that Styx will use
plugins:
  # List of plugin IDs from the configured plugins under "all". Only the plugins listed in "active" will be loaded. 
  active: plugin1, plugin2
  all:
    plugin1:
      factory:
        # the name of a class that implements com.hotels.styx.api.plugins.spi.PluginFactory 
        class: "foo.bar.Plugin1Factory"
        # the path (directory or jar) from which the factory class can be loaded
        classPath: "/foo/bar/"
      # configuration for the plugin. the exact structure is up to the plugin creator
      config:
        foo: "bar"
        bar: "foo"
    plugin2:
      factory:
        class: "pack.age.Plugin2Factory"
        classPath: "/foo/bar/"
      config:
        some: "config"
        et: "cetera"
 ```
