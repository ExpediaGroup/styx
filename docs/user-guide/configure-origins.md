# Configuring Backend Services and Origins

## Configuration file

Backend services and origins known to Styx are configured in their own YAML file, which is referenced by the main 
configuration via the `originsFile` property in the services block.

The value for this `originsFile` property accepts including environment variables (with the ${ENV_VAR} format) and can reference:
 - A path in the filesystem by default.
 - A resource in the classpath by using the prefix *classpath:*.

### Examples:
Using the absolute path of the file:
```yaml
services:
  factories:
    backendServiceRegistry:
      class: "com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistry$Factory"
      config: {originsFile: "${STYX_HOME}/conf/env-development/origins.yaml"}
 ```
Using a file in the classpath:
```yaml
services:
  factories:
    backendServiceRegistry:
      class: "com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistry$Factory"
      config: {originsFile: "classpath:/conf/origins.yaml"}
 ```
    
## Backend services

Each service has the following properties:

*   **id**: a unique name used to identify backend services

*   **path**: the beginning of a (relative) URI used by Styx to determine which backend service to route a request to.

*   **healthCheck**: a group of parameters a relative URI for Styx to contact to determine whether an origin is "alive".

*   **origins**: a list of origins (instances of the backend service)

*   **stickySession**: a group of parameters enabling Styx to pin down an HTTP session to a specific origin.
 Styx achieves this by inserting a session cookie in HTTP responses.

*   **connectionPool**: configuration for the connection pools used to communicate with this backend service.

*   **responseTimeoutMillis**: amount of time, in milliseconds, Styx waits for a response from origin.
Defaults to 60000 milliseconds.

*   **sslSettings**: Enables HTTPS for backend.

## Health check
See [Health Checks](configure-health-checks.md) for details.

The health check block has the following properties:

*   **uri**: a URI that Styx will attempt to connect to in order to make a health check

*   **intervalMillis**: the interval in between health checks in milliseconds

*   **healthyThreshold**: the number of times the health check must pass for an inactive origin to be declared active

*   **unhealthyThreshold**: the number of times the health check must pass for an active origin to be declared inactive

## Sticky Session
See [Session Affinity](configure-session-affinity.md) for details.

The sticky session block has the following properties:

*   **enabled**: Enables (true) or disables (false) sticky sessions. When absent, defaults to false.

*   **timeoutSeconds**: Styx pins an HTTP session to an origin by inserting a special cookie in HTTP responses.
 TimeoutSeconds can be set to adjust the cookie expiry time, and it is the number of seconds since the most recent HTTP request.
  If absent defaults to 43200 seconds, which is 12 hours.

## Connection Pool
See [Connection Pooling](configure-connection-pooling.md) for details.

The connection pool block has the following properties:

*   **maxConnectionsPerHost**: the maximum number of connections that may be established to a single origin

*   **maxPendingConnectionsPerHost**: the maximum number of connections that may be waiting to be established at the same time

*   **connectTimeoutMillis**: the maximum time Styx should wait for a connection to be established

*   **pendingConnectionTimeoutMillis**: the maximum time to wait for a connection from the connection pool

## Rewrites

For the list of rewrites, each rewrite has two properties: **urlPattern** and **replacement**. An URL from the response is matched against configured urlPatterns, in the order they appear in the **rewrites** list. The first matching entry will be used to substitute existing URL with a new one according to the **replacement** template.

*   **urlPattern** - a regular expression, in Java syntax, that specifies the URL to be matched. This typically contains one or more capture groups.

*   **replacement** - specifies a template that describes how the URL is substituted. It may contain any literal text, together with special $1, $2, ... symbols. Any literal text will appear in the new URL as is, and any $1, $2, ... symbol will be substituted with a text captured by corresponding capture group from the **urlPattern**.

If the request URL does not match any of the **urlPattern**s, the request retains its original URL.

Because **urlPattern**s are matched in order they appear in the **rewrites** list, one needs to add the most specific pattern to the top of the list, and the least specific (i.e. most generic) pattern to the bottom of the list.

## SSL Settings
See [Transport Layer Security](configure-tls.md) for details.

The `sslSettings` is an optional configuration block. If present, it enables an HTTPS protocol between Styx and the
 backend application. If absent, insecure HTTP protocol is used. The `sslSettings` has following attributes:

*   **trustAllCerts** - when `false` Styx authenticates the backend origin with configured certificates.
 When `true` Styx doesn't authenticate the remote end.

*   **sslProvider** - Sets the SSL provider implementation. Defaults to `JDK`.

*   **addlCerts** - A list of additional certificates that are used to authenticate remote origins. Each certificate has two attributes:

    *   **alias** - a descriptive name to identify the certificate.

    *   **path** - a path to the certificate file.

*   **trustStorePath** - A path to Java truststore file containing trusted certificates for authenticating origins.

*   **trustStorePassword** - Password for keystore referred by **trustStorePath** attribute.

Styx considers incoming traffic and outgoing traffic separately. It will convert between the HTTP and HTTPS protocols 
when the incoming server side protocol differs from the outgoing origin side protocol.

Limitations:

*   Styx does not support client side authentication for backend origins. Therefore the backend origins are unable to authenticate styx.

## Origins

For the list of origins, each origin has the following properties:

*   **id**: a unique name used to identify origins.

*   **host**: the hostname and port number that requests will be routed to.

##

## Example

Here is the configuration for an example backend service:
```yaml
    - id: "hwa"
      path: "/"
      healthCheck:
        uri: "/version.txt"
        intervalMillis: 10000
        healthyThreshold: 2
        unhealthyThreshold: 2  
      stickySession:
        enabled: true
        timeoutSeconds: 14321
      connectionPool:
        maxConnectionsPerHost: 300
        maxPendingConnectionsPerHost: 50
        connectTimeoutMillis: 12000
        pendingConnectionTimeoutMillis: 10000    
      rewrites:
      - urlPattern: "/hwa/(.*)/foobar/(.*)"
        replacement: "/$1/barfoo/$2"
      - urlPattern: "/hwa/(.*)"
        replacement: "/$1"
      sslSettings:
        trustAllCerts: false
        sslProvider: JDK
        addlCerts:
          - alias: "my certificate"
            path: /path/to/mycert
          - alias: "alt certificatfe"
            path: /path/to/altcert
        trustStorePath: /path/to/truststore
        trustStorePassword: truststore-123
      origins:
      - id: "hwa1"
        host: "chhlapputle2e63.karmalab.net:7401"
```