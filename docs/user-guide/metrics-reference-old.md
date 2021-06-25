# Metrics Reference

:warning: **Metrics have changed!** Migration notes are [here](metrics-migration.md)

## Metric Categories

Styx collects performance metrics from the following functional areas:

 - Server Side Metrics
   - HTTP level metrics (`requests` scope)
   - TCP connection level metrics (`connections` scope)
   - OpenSSL metrics (when `OPENSSL` provider is configured):
   - Server metrics (`styx` scope):

 - Origin Metrics
   - Request metrics aggregated to back-end service
   - Request metrics per origin origin
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

### HTTP level metrics (`requests` scope)

**requests.cancelled.`<cause>`**

* Requests cancelled due to an error.


**requests.outstanding**

* Number of requests currently being served (in flight).

**requests.response.sent**

* Total number of responses sent downstream

**requests.response.status.`<code>`**

* Total number or responses for each status code class (1xx, 2xx, ...)
* Total number of responses for each error status code (code >= 400)
* Total number of unrecognised status codes (`<code>` is `unrecognised`)
* This metric combines statuses from origins with statuses from Styx-generated responses.

**requests.received**

* Total number of requests received

**requests.error-rate.500**

* The rate of 500 Internal Server Error
* This metric combines statuses from origins with statuses from Styx-generated responses.

**requests.latency**

* Request latency, measured on Styx server interface.
* Measured as a time to last byte written back to downstream.
* Timer starts when request arrives, timer stops when the response
  from origin is fully written to the socket.

### TCP connection level metrics (`connections` scope)

**connections.eventloop.`<thread>`.registered-channel-count**

* Number of TCP connections registered against the Styx server IO thread, where
`<thread>` is the IO thread name.

**connections.total-connections**

* Total number of TCP connections active on Styx server side.
* Does not count client side TCP connections.


**connections.eventloop.`<thread>`.channels**

* Measures the distribution of number of channels for a named IO thread.
  There is a counter for each thread.


**connections.bytes-received**

* Total number of bytes received.

**connections.bytes-sent**

* Total number of bytes sent.

**connections.idleClosed**

* Number of server side connections closed due to idleness. 


### Styx Server metrics (`styx` scope)

**styx.exception.`<cause>`**

* Number of exceptions, for each `<cause>` exception name.

**styx.server.http.requests**

* Number of requests received from http connector (port).

**styx.server.http.responses.`<code>`**

* Number of responses sent out via http connector.

**styx.server.https.requests**

* Number of requests received from https connector (port).

**styx.server.https.responses.`<code>`**

* Number of responses sent out via https connector.

**styx.version.buildnumber**

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

**origins.`<backend>`.requests.cancelled**
**origins.`<backend>`.`<origin>`.requests.cancelled**

* Number of requests cancelled due to an error.

**origins.`<backend>`.requests.success-rate**
**origins.`<backend>`.`<origin>`.requests.success-rate**

* Rate of successful requests to the origin.
* A request is considered a success when it returns a non-5xx class status code.


**origins.`<backend>`.requests.error-rate**
**origins.`<backend>`.`<origin>`.requests.error-rate**

* Number of failed requests to the origin.
* A request is considered a failure when origin responds with a 5xx class status code.

**origins.`<backend>`.requests.response.status.`<code>`**
**origins.`<backend>`.`<origin>`.requests.response.status.`<code>`**

* Number of responses from origin with a status codee of `<code>`.
* Unrecognised status codes are collapsed to a value of -1. A status
  code is unrecognised when `code < 100` or `code >= 600`.


**origins.`<backend>`.requests.response.status.5xx**
**origins.`<backend>`.`<origin>`.requests.response.status.5xx**

* A rate of 5xx responses from an origin.

**origins.`<backend>`.requests.latency**
**origins.`<backend>`.`<origin>`.requests.latency**

* A latency distribution of requests to origin.
* Measured as time to last byte.
* Timer started when request is sent, and stopped when the last content
  byte is received.

**origins.`<backend>`.requests.time-to-first-byte**  
**origins.`<backend>`.`<origin>`.requests.time-to-first-byte**

* A latency distribution of requests to origin.
* Measured as time to first content byte.
* Timer started when request is sent, and stopped when the first content
  byte is received.

**origins.`<backend>`.`<origin>`.status**

* Current [origin state](configure-health-checks.md), as follows:
   * 1 - ACTIVE,
   * 0 - INACTIVE
   * -1 - DISABLED

**origins.`<backend>`.healthcheck.failure**

* Number of health check failure rate


### Connection pool metrics

**origins.`<backend>`.`<origin>`.connectionspool.available-connections**

* Number of primed TCP connections readily available in the pool.

**origins.`<backend>`.`<origin>`.connectionspool.busy-connections**

* Number of connections borrowed at the moment.

**origins.`<backend>`.`<origin>`.connectionspool.connection-attempts**

* Number of TCP connection establishment attempts.

**origins.`<backend>`.`<origin>`.connectionspool.connection-failures**

* Number of failed TCP connection attempts.

**origins.`<backend>`.`<origin>`.connectionspool.connections-closed**

* Number of TCP connection closures.
* Counts the connections closed by *Styx*, not an origin.

**origins.`<backend>`.`<origin>`.connectionspool.connections-terminated**

* Number of times TCP connection has terminated, either because it was
  closed by styx, or by an origin, or otherwise disconnected.

**origins.`<backend>`.`<origin>`.connectionspool.pending-connections**

* Size of the [pending connections queue](configure-connection-pooling.md) at the moment.

**origins.`<backend>`.`<origin>`.connectionspool.connections-in-establishment**

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


## Undocumented or unstable metrics


Following metrics are subject to change their names:

     origins.response.status.<code>
