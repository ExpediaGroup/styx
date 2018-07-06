# Metrics Reference

## Server Side Metrics

### HTTP level metrics (`requests` scope)

**requests.cancelled.`<cause>`**

* Counter
* Requests cancelled due to an error.


**requests.outstanding**

* Counter
* Number of requests currently being served (in flight).

**requests.response.sent**

* Counter
* Total number of responses sent downstream

**requests.response.status.`<code>`**

* Counter
* Total number or responses for each status code class (1xx, 2xx, ...)
* Total number of responses for each error status code (code >= 400)
* Total number of unrecognised status codes (`<code>` is `unrecognised`)
* This metric combines statuses from origins with statuses from Styx-generated responses.

**requests.received**

* Counter
* Total number of requests received

**requests.error-rate.500**

* Meter
* The rate of 500 Internal Server Error
* This metric combines statuses from origins with statuses from Styx-generated responses.

**requests.latency**

* Timer
* Request latency, measured on Styx server interface.
* Measured as a time to last byte written back to downstream.
* Timer starts when request arrives, timer stops when the response
  from origin is fully written to the socket.


### TCP connection level metrics (`connections` scope)

**connections.eventloop.`<thread>`.registered-channel-count**

* Counter
* Number of TCP connections registered against the Styx server IO thread, where
`<thread>` is the IO thread name.

**connections.total-connections**

* Counter
* Total number of TCP connections active on Styx server side.
* Does not count client side TCP connections.


**connections.eventloop.`<thread>`.channels**

* Histogram
* Measures the distribution of number of channels for a named IO thread.
  There is a counter for each thread.


**connections.bytes-received**

* Counter
* Total number of bytes received.

**connections.bytes-sent**

* Counter
* Total number of bytes sent.


### Styx Server metrics (`styx` scope)

**styx.exception.`<cause>`**

* Count
* Number of exceptions, for each `<cause>` exception name.

**styx.server.http.requests**

* Count
* Number of requests received from http connector (port).

**styx.server.http.responses.`<code>`**

* Count
* Number of responses sent out via http connector.

**styx.server.https.requests**

* Count
* Number of requests received from https connector (port).

**styx.server.https.responses.`<code>`**

* Count
* Number of responses sent out via https connector.

**styx.version.buildnumber**

* Gauge
* Styx version number.


### Open SSL metrics

TBD:

    connections.openssl.session.accept
    connections.openssl.session.acceptGood
    connections.openssl.session.acceptRenegotiate
    connections.openssl.session.cacheFull
    connections.openssl.session.cbHits
    connections.openssl.session.misses
    connections.openssl.session.number



## Client Side Metrics

### Per Back-End Request Metrics

**origins.`<backend>`.requests.cancelled**
**origins.`<backend>`.`<origin>`.requests.cancelled**

* Count
* Number of requests cancelled due to an error.

**origins.`<backend>`.requests.success-rate**
**origins.`<backend>`.`<origin>`.requests.success-rate**

* Meter
* Rate of successful requests to the origin.
* A request is considered a success when it returns a non-5xx class status code.


**origins.`<backend>`.requests.error-rate**
**origins.`<backend>`.`<origin>`.requests.error-rate**

* Meter
* Number of failed requests to the origin.
* A request is considered a failure when origin responds with a 5xx class status code.

**origins.`<backend>`.requests.response.status.`<code>`**
**origins.`<backend>`.`<origin>`.requests.response.status.`<code>`**

* Meter
* Number of responses from origin with a status codee of `<code>`.
* Unrecognised status codes are collapsed to a value of -1. A status
  code is unrecognised when `code < 100` or `code >= 600`.


**origins.`<backend>`.requests.response.status.5xx**
**origins.`<backend>`.`<origin>`.requests.response.status.5xx**

* Meter
* A rate of 5xx responses from an origin.

**origins.`<backend>`.requests.latency**
**origins.`<backend>`.`<origin>`.requests.latency**

* Timer
* A latency distribution of requests to origin.
* Measured as time to last byte.
* Timer started when request is sent, and stopped when the last content
  byte is received.

**origins.`<backend>`.`<origin>`.status**

* Gauge
* Current [origin state](configure-health-checks.md), as follows:
   * 1 - ACTIVE,
   * 0 - INACTIVE
   * -1 - DISABLED


### Connection pool metrics

**origins.`<backend>`.`<origin>`.connectionspool.available-connections**

* Gauge
* Number of primed TCP connections readily available in the pool.

**origins.`<backend>`.`<origin>`.connectionspool.busy-connections**

* Gauge
* Number of connections borrowed at the moment.

**origins.`<backend>`.`<origin>`.connectionspool.connection-attempts**

* Gauge
* Number of TCP connection establishment attempts.

**origins.`<backend>`.`<origin>`.connectionspool.connection-failures**

* Gauge
* Number of failed TCP connection attempts.

**origins.`<backend>`.`<origin>`.connectionspool.connections-closed**

* Gauge
* Number of TCP connection closures.
* Counts the connections closed by *Styx*, not an origin.

**origins.`<backend>`.`<origin>`.connectionspool.connections-terminated**

* Gauge
* Number of times TCP connection has terminated, either because it was
  closed by styx, or by an origin, or otherwise disconnected.

**origins.`<backend>`.`<origin>`.connectionspool.pending-connections**

* Gauge
* Size of the [pending connections queue](configure-connection-pooling.md) at the moment.
