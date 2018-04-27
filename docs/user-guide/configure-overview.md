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
      - type: http
        # Server port for Styx proxy.
        port: 8080

      #  0 -> availableProcessors / 2 threads will be used
      bossThreadsCount: 1

      # styx client worker threads are those performing all the asynchronous I/O operation to the backend origins.
      # 0 -> availableProcessors / 2 threads will be used
      clientWorkerThreadsCount: 0

      # Worker threads are those performing all the asynchronous I/O operation on the inbound channel.
      # 0 -> availableProcessors / 2 threads will be used

      workerThreadsCount: 0

      tcpNoDelay: true
      nioReuseAddress: true
      nioKeepAlive: true
      maxInitialLength: 4096
      maxHeaderSize: 8192
      maxChunkSize: 8192
      maxContentLength: 65536


      # A timeout for idle persistent connections, in milliseconds.
      keepAliveTimeoutMillis: 12000

      # Time in milliseconds Styx Proxy Service waits for an incoming request from client
      # before replying with 408 Request Timeout.
      requestTimeoutMillis: 12000


    admin:
      connectors:
      - type: http
        # Server port for Styx admin interface.
        port: 9000

      # Number of threads for handling incoming connections on admin interface. 0 -> availableProcessors / 2 threads will be used.
      bossThreadsCount: 1

      # Number of worker threads for admin interface
      # Worker threads are those performing all the asynchronous I/O operation on the inbound channel.
      # 0 -> availableProcessors / 2 threads will be used
      workerThreadsCount: 1

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

    loadBalancing:
      strategy: #Check load balancing documentation for all the possible strategies
        factory: {class: "com.hotels.styx.client.loadbalancing.strategies.PowerOfTwoStrategy$Factory"}
 


      adaptive:
        # Adaptive loadbalancer warm-up count. The count is the number of requests that has to hit the
        # load balancer before it upswitches from round robin strategy to least response time strategy.
        warmupCount: 100

    metrics:
      graphite:
        enabled: true
        # Host of the Graphite endpoint. Overrides the property from CoreFoundation
        host: "destination.host"

        # Port of the Graphite endpoint. Overrides the property from CoreFoundation
        port: 2003

        # Graphite reporting interval in milliseconds
        intervalMillis: 5000
      jmx:
        # Enable reporting of metrics via JMX. Overrides the property from CoreFoundation
        enabled: true
        
    request-logging:
      # Enabled logging of requests and responses (with requestId to match them up)
      # Logs are produced on server and client side, so there is an information on 
      # how the server-side (inbound) and client-side (outbound) request/response look like.
      # In long format log entry contains additionally headers and cookies. 
      inbound:
        enabled: true
        longFormat: true
      outbound:
        enabled: true
        longFormat: true
      
    # Determines the format of the response info header
    responseInfoHeaderFormat: {INSTANCE};{REQUEST_ID}
    
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
 ```

Without the comments, it looks like this:
```yaml
   jvmRouteName: "${jvm.route:noJvmRouteSet}"
    proxy:
      connectors:
      - type: http
        port: 8080
      bossThreadsCount: 1
      clientWorkerThreadsCount: 0
      workerThreadsCount: 0
      tcpNoDelay: true
      nioReuseAddress: true
      nioKeepAlive: true
      maxInitialLength: 4096
      maxHeaderSize: 8192
      maxChunkSize: 8192
      maxContentLength: 65536
      requestTimeoutMillis: 12000

    admin:
      connectors:
      - type: http
        port: 9000
      bossThreadsCount: 1
      workerThreadsCount: 1
      tcpNoDelay: true
      nioReuseAddress: true
      nioKeepAlive: true
      maxInitialLength: 4096
      maxHeaderSize: 8192
      maxChunkSize: 8192
      maxContentLength: 65536
      metricsCache:
        enabled: true
        expirationMillis: 10000

    loadBalancing:
      strategy: "ADAPTIVE"
      adaptive:
        warmupCount: 100

    metrics:
      graphite:
        enabled: true
        host: "destination.host"
        port: 2003
        intervalMillis: 5000
      jmx:
        enabled: true

    request-logging:
      enabled: true

    styxHeaders:
      styxInfo:
        name: "X-Styx-Info"
        format: "{INSTANCE};{REQUEST_ID}"
      originId:
        name: "X-Styx-Origin-Id"
      requestId:
        name: "X-Styx-Request-Id"
```