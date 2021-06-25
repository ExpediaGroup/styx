# Metrics Reference

:warning: **Metrics have changed!** Migration notes are [here](metrics-migration.md)

## Metric Categories

Styx collects performance metrics from the following functional areas:

 - Server Side Metrics
   - HTTP level metrics (`proxy.request.*`)
   - TCP connection level metrics (`proxy.connection.*`)
   - OpenSSL metrics  (`proxy.request.openssl.*`)
   - Server metrics (`styx.*`):

 - Origin Metrics
   - Request metrics aggregable to back-end service or origin
   - Connection pool metrics
   - Health-check metrics

 - JVM Metrics

 - Operating System Metrics

The server side metrics are collected from the styx ingress interface. 
The origin metrics are collected on the application side where
the requests are forwarded to the backend services.


## Server Side Metrics

The server side metrics scopes are illustrated in a diagram below:

![Styx Server Metrics](../assets/styx-server-metrics.png "Styx server metrics")

### HTTP level metrics (`proxy.request.*`)

####proxy.request.cancelled.`<cause>`

* Requests cancelled due to an error.


####proxy.request.outstanding

* Number of requests currently being served (in flight).

####proxy.response.sent

* Total number of responses sent downstream

####proxy.response.status
`statusClass=(unrecognized/1xx/2xx/3xx/4xx/5xx)`<br>
`statusCode=<statuscode>`

* Total number or responses for each status code class (1xx, 2xx, ...)
* Total number of responses for each error status code (code >= 400)
* Total number of unrecognised status codes (`<code>` is `unrecognised`)
* This metric combines statuses from origins with statuses from Styx-generated responses.

####proxy.request.received

* Total number of requests received

####proxy.request.latency

* Request latency, measured on Styx server interface.
* Measured as a time to last byte written back to downstream.
* Timer starts when request arrives, timer stops when the response
  from origin is fully written to the socket.

### TCP connection level metrics (`proxy.connection.*` )

####proxy.connection.registeredChannelCount
`eventloop=<thread>`

* Number of TCP connections registered against the Styx server IO thread, where
`<thread>` is the IO thread name.

####proxy.connection.totalConnections

* Total number of TCP connections active on Styx server side.
* Does not count client side TCP connections.


####proxy.connection.channels
`eventloop=<thread>`

* Measures the distribution of number of channels for a named IO thread.
  There is a counter for each thread.


####proxy.connection.bytesReceived

* Total number of bytes received.

####proxy.connection.bytesSent

* Total number of bytes sent.

####proxy.connection.idleClosed

* Number of server side connections closed due to idleness. 


### Styx Server metrics (`styx.*`)

####styx.exception
`type=<cause>`

* Number of exceptions, for each `<cause>` exception name.

####styx.server.request
`protocol=(http/https)`

* Number of requests received from http or https connector (port).

####styx.server.response
`protocol=(http/https)`<br>
`statusCode=<statuscode>`

* Number of responses sent out via http or https connector.

####styx.version.buildnumber

* Styx version number.


### Open SSL metrics

TBD:

    connections.openssl.session.accept
    connections.openssl.session.acceptGood
    connections.openssl.session.acceptRenegotiate
    connections.openssl.session.cacheFull
    connections.openssl.session.cbHits
    connections.openssl.session.hits
    connections.openssl.session.misses
    connections.openssl.session.number
    connections.openssl.session.timeouts

## Origin Side Metrics

The origin side metrics scopes are illustrated in a diagram below:

![Styx Client Metrics](../assets/styx-origin-metrics.png "Styx client metrics")


### Per Back-End Request Metrics

####request.cancellation
`appId=<appid>`<br>
`originId=<originid>`

* Number of requests cancelled due to an error.

####request.success
`appId=<appid>`<br>
`originId=<originid>`

* Number of successful requests to the origin.
* A request is considered a success when it returns a non-5xx class status code.


####request.error
`appId=<appid>`<br>
`originId=<originid>`

* Number of failed requests to the origin.
* A request is considered a failure when origin responds with a 5xx class status code.

####request.status
`appId=<appid>`<br>
`originId=<originid>`<br>
`statusCode=<statuscode>`<br>
`statusClass=(1xx/2xx/3xx/4xx/5xx)`

* Number of responses from origin with a status codee of `<code>`.
* Unrecognised status codes are collapsed to a value of -1. A status
  code is unrecognised when `code < 100` or `code >= 600`.


####request.latency
`appId=<appid>`<br>
`originId=<originid>`

* A latency distribution of requests to origin.
* Measured as time to last byte.
* Timer started when request is sent, and stopped when the last content
  byte is received.

####request.timeToFirstByte  
`appId=<appid>`<br>
`originId=<originid>`

* A latency distribution of requests to origin.
* Measured as time to first content byte.
* Timer started when request is sent, and stopped when the first content
  byte is received.

####origin.status
`appId=<appid>`<br>
`originId=<originid>`

* Current [origin state](configure-health-checks.md), as follows:
   * 1 - ACTIVE,
   * 0 - INACTIVE
   * -1 - DISABLED

####origin.healthcheck.failure
`appId=<appid>`<br>
`originId=<originid>`

* Number of health check failures


### Connection pool metrics

####connectionpool.availableConnections
`appId=<appid>`<br>
`originId=<originid>`

* Number of primed TCP connections readily available in the pool.

####connectionpool.busyConnections
`appId=<appid>`<br>
`originId=<originid>`

* Number of connections borrowed at the moment.

####connectionpool.connectionAttempts
`appId=<appid>`<br>
`originId=<originid>`

* Number of TCP connection establishment attempts.

####connectionpool.connectionFailures
`appId=<appid>`<br>
`originId=<originid>`

* Number of failed TCP connection attempts.

####connectionpool.connectionsClosed
`appId=<appid>`<br>
`originId=<originid>`

* Number of TCP connection closures.
* Counts the connections closed by *Styx*, not an origin.

####connectionpool.connectionsTerminated
`appId=<appid>`<br>
`originId=<originid>`

* Number of times TCP connection has terminated, either because it was
  closed by styx, or by an origin, or otherwise disconnected.

####connectionpool.pendingConnections
`appId=<appid>`<br>
`originId=<originid>`

* Size of the [pending connections queue](configure-connection-pooling.md) at the moment.

####connectionpool.connectionsInEstablishment
`appId=<appid>`<br>
`originId=<originid>`

* Number of connections performing a TCP handshake or an SSL/TLS handshake procedure.

## Operating System Metrics

Styx also measures metrics from the underlying operating system:

    os.process.cpu.load
    os.process.cpu.time
    os.system.cpu.load
    os.memory.physical.free
    os.memory.physical.total
    os.memory.virtual.committed
    os.swapSpace.free
    os.swapSpace.total

These ones are only available on a Unix-based system:

    os.fileDescriptors.max
    os.fileDescriptors.open

## Plugin Metrics

Custom extension plugins expose their metrics under `styx.plugins.<name>`
hierarchy. The `name` is a plugin name as it is configured in the
`plugins` section. Consider the following:

```
  plugins:
  all:
    guidFixer:
      factory:
        ... factories ...
      config:
        ... config ...
```

All metrics from this plugin would go under `styx.plugins.guidFixer` prefix.

