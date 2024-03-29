# Migration of metrics to Micrometer
Previously Styx has supported metrics using the Dropwizard library, and reporting
them to a Graphite backend. Work is underway to migrate Styx to use the Micrometer library
instead. This enables tags to be used to describe the metrics more flexibly, and to simplify
adding support for different backends. Prometheus will be supported, in addition to keeping
support for Graphite.

Styx will continue to generate a similar set of metrics as it does now, but the metric names will
change to (a) use tags rather than some hierarchical name elements, and (b) accommodate
different naming standards recommended for Micrometer. This page describes those name changes.

[Old metrics reference](metrics-reference-old.md)
[New metrics reference](metrics-reference.md)

## Server-side metrics

### HTTP-level metrics

| Old type | Old name | New type | New Name | Tags | Comments |
| --- | --- | --- | --- | --- | --- |
| Counter | `requests.outstanding` | Gauge | `proxy.request.outstanding` |
| Timer | `requests.latency` | Timer | `proxy.request.latency` |
| Meter | `requests.received` | Counter | `proxy.request.received` |
| Meter | `requests.error-rate.500` | | | | see `proxy.response.status` below |
| Counter | `requests.response.sent` | Counter | `proxy.response.sent` |
| Counter | `requests.response.status.(unrecognized/1xx/2xx/3xx/4xx/5xx)` | Counter | `proxy.response.status` | `statusClass=(unrecognized/1xx/2xx/3xx/4xx/5xx)`<br>`statusCode=<statuscode>` |
| Counter | `requests.response.status.(500/501/502/503/504/521)` | | | | see `proxy.response.status` above |
| Counter | `requests.cancelled.spuriousRequest` | Counter | `proxy.request.cancelled.spuriousRequest` |
| Counter | `requests.cancelled.responseWriteError` | Counter | `proxy.request.cancelled.responseWriteError` |
| Counter | `requests.cancelled.channelInactive` | Counter | `proxy.request.cancelled.channelInactive` |
| Counter | `requests.cancelled.channelExceptionWhileSendingResponse` | Counter | `proxy.request.cancelled.channelExceptionWhileSendingResponse` |
| Counter | `requests.cancelled.channelExceptionWhileWaitingForResponse` | Counter | `proxy.request.cancelled.channelExceptionWhileWaitingForResponse` |
| Counter | `requests.cancelled.responseError` | Counter | `proxy.request.cancelled.responseError` |
| Counter | `requests.cancelled.observableCompletedTooSoon` | Counter | `proxy.request.cancelled.observableCompletedTooSoon` |

### TCP-level metrics

| Old type | Old name | New type | New Name | Tags | Comments |
| --- | --- | --- | --- | --- | --- |
| Counter | `connections.bytes-received` | Counter | `proxy.connection.bytesReceived` |
| Counter | `connections.bytes-sent` | Counter | `proxy.connection.bytesSent` |
| Counter | `connections.total-connections` | Gauge | `proxy.connection.totalConnections` |
| Counter | `connections.eventloop.<eventloop>.registered-channel-count` | Counter | `proxy.connection.registeredChannelCount` | `eventloop=<eventloop>` |
| Histogram | `connections.eventloop.<eventloop>.channels` | Summary | `proxy.connection.channels` | `eventloop=<eventloop>` |
| Histogram | `connections.idleClosed` | Summary | `proxy.connection.idleClosed` |
| Gauge | `connections.openssl.sessions.number` | Gauge | `proxy.connection.openssl.session.number` |
| Gauge | `connections.openssl.sessions.accept` | Gauge | `proxy.connection.openssl.session.accept` |
| Gauge | `connections.openssl.sessions.acceptGood` | Gauge | `proxy.connection.openssl.session.acceptGood` |
| Gauge | `connections.openssl.sessions.acceptRenegotiate` | Gauge | `proxy.connection.openssl.session.acceptRenegotiate` |
| Gauge | `connections.openssl.sessions.hits` | Gauge | `proxy.connection.openssl.session.hits` |
| Gauge | `connections.openssl.sessions.misses` | Gauge | `proxy.connection.openssl.session.misses` |
| Gauge | `connections.openssl.sessions.cbHits` | Gauge | `proxy.connection.openssl.session.cbHits` |
| Gauge | `connections.openssl.sessions.cacheFull` | Gauge | `proxy.connection.openssl.session.cacheFull` |
| Gauge | `connections.openssl.sessions.timeouts` | Gauge | `proxy.connection.openssl.session.timeouts` |

### Styx Server metrics
| Old type | Old name | New type | New Name | Tags | Comments |
| --- | --- | --- | --- | --- | --- |
| Meter | `styx.errors` | Counter | `styx.error` |
| Counter | `styx.response.status.<statuscode>` | Counter | `styx.response` | `statusCode=<statuscode>` |
| Counter | `styx.exception.<type>` | Counter | `styx.exception` | `type=<type>` |
| Meter | `styx.server.(http/https).requests` | Counter | `styx.server.request` | `protocol=(http/https)` |
| Meter | `styx.server.(http/https).responses.<statuscode>` | Counter | `styx.server.response` | `protocol=(http/https)`<br>`statusCode=<statuscode>` |

## Origin metrics

| Old type | Old name | New type | New Name | Tags | Comments |
| --- | --- | --- | --- | --- | --- |
| Meter | `origins.<appid>.[<originid>.]requests.success-rate` | Counter | `request.success` | `appId=<appid>`<br>`originId=<originid>` |
| Meter | `origins.<appid>.[<originid>.]requests.error-rate` | Counter | `request.error` | `appId=<appid>`<br>`originId=<originid>` |
| Timer | `origins.<appid>.[<originid>.]requests.latency` | Timer | `request.latency` | `appId=<appid>`<br>`originId=<originid>` |
| Timer | `origins.<appid>.[<originid>.]requests.time-to-first-byte` | Timer | `request.timeToFirstByte` | `appId=<appid>`<br>`originId=<originid>` |
| Meter | `origins.<appid>.[<originid>.]requests.response.status.<statuscode>` | Counter | `request.status` | `appId=<appid>`<br>`originId=<originid>`<br>`statusCode=<statuscode>`<br>`statusClass=(1xx/2xx/3xx/4xx/5xx)` |
| Meter | `origins.<appid>.[<originid>.]requests.response.status.5xx` | | | | see `request.status` above |
| Counter | `origins.<appid>.[<originid>.]requests.cancelled` | Counter | `request.cancellation` | `appId=<appid>`<br>`originId=<originid>` |
| Gauge | `origins.<appid>.<originid>.status` | Gauge | `origin.status` | `appId=<appid>`<br>`originId=<originid>` |
| Meter | `origins.<appid>.<originid>.healthcheck.failures` | Counter | `origin.healthcheck.failures` | `appId=<appid>`<br>`originId=<originid>` |

## Connection pool metrics
| Old type | Old name | New type | New Name | Tags | Comments |
| --- | --- | --- | --- | --- | --- |
| Gauge | `origins.<appid>.<originid>.connectionspool.busy-connections` | Gauge | `connectionpool.busyConnections` | `appId=<appid>`<br>`originId=<originid>` |
| Gauge | `origins.<appid>.<originid>.connectionspool.pending-connections` | Gauge | `connectionpool.pendingConnections` | `appId=<appid>`<br>`originId=<originid>` |
| Gauge | `origins.<appid>.<originid>.connectionspool.available-connections` | Gauge | `connectionpool.availableConnections` | `appId=<appid>`<br>`originId=<originid>` |
| Gauge | `origins.<appid>.<originid>.connectionspool.connection-attempts` | Gauge | `connectionpool.connectionAttempts` | `appId=<appid>`<br>`originId=<originid>` |
| Gauge | `origins.<appid>.<originid>.connectionspool.connection-failures` | Gauge | `connectionpool.connectionFailures` | `appId=<appid>`<br>`originId=<originid>` |
| Gauge | `origins.<appid>.<originid>.connectionspool.connections-closed` | Gauge | `connectionpool.connectionsClosed` | `appId=<appid>`<br>`originId=<originid>` |
| Gauge | `origins.<appid>.<originid>.connectionspool.connections-terminated` | Gauge | `connectionpool.connectionsTerminated` | `appId=<appid>`<br>`originId=<originid>` |
| Gauge | `origins.<appid>.<originid>.connectionspool.connections-in-establishment` | Gauge | `connectionpool.connectionsInEstablishment` | `appId=<appid>`<br>`originId=<originid>` |

## JVM Metrics

| Old type | Old name | New type | New Name | Tags | Comments |
| --- | --- | --- | --- | --- | --- |
| Gauge | `jvm.bufferpool.(direct/mapped).(count/used/capacity)` | Gauge | `jvm.buffer.count`<br>`jvm.buffer.memory.used`<br>`jvm.buffer.total.capacity` | | `JvmMemoryMetrics()` |
| Gauge | `jvm.memory.*` | Gauge | `jvm.memory.used`<br>`jvm.memory.committed`<br>`jvm.memory.max` | | `JvmMemoryMetrics()` |
| Gauge | `jvm.thread.*` | Gauge | `jvm.threads.peak`<br>`jvm.threads.daemon`<br>`jvm.threads.live` | | `JvmThreadMetrics()` | 
|  |  | Gauge | `jvm.threads.states` | `state=<threadstate>` | `JvmThreadMetrics()` |
| Gauge | `jvm.gc.*` | Gauge | `jvm.gc.*`<br>`jvm.classes.(loaded/unloaded)` | | `JvmGcMetrics()`<br>`ClassLoaderMetrics()` |
| Gauge | `jvm.uptime` | Gauge | `jvm.uptime` |
| Gauge | `jvm.uptime.formatted` | | | | Removed |
| Gauge | `jvm.netty.(pooled-allocator/unpooled-allocator).(usedDirectMemory/usedHeapMemory)` | Gauge | `jvm.netty.(pooledAllocator/unpooledAllocator).(usedDirectMemory/usedHeapMemory)` |

## Plugin metrics

The Styx metrics registry is available to a `PluginFactory` from the `PluginEnvironment` 
object that is passed in the `create()` method. The existing method `metricRegistry()` has been deprecated,
and plugins are expected to use the new `pluginMeterRegistry()` method instead, which returns a
`PluginMeterRegistry` object.

This object wraps a Micrometer `MeterRegistry` object. It provides standard methods to create
new meters in the registry. All meters that are created using the `PluginMeterRegistry` object
will have a standard tag automatically applied to them:

| Tag name | Tag value |
| --- | --- |
| `plugin` | ID of the plugin from Styx config |

A prefix of form `plugins.<plugin-id>` is no longer automatically added to meter names.
You may wish to namespace your plugins meter names by adding a prefix of your own to them.

You do not have to use the `PluginMeterRegistry` object to create all your metrics. You
can instead obtain the Micrometer `MeterRegistry` from it and use that to create some
or all of your metrics. If you do this, the `plugin` tag above will not be automatically
applied. You must add it yourself. You can obtain it from the `PluginMeterRegistry` as well.

There are a number of standard metrics that are created by Styx for each plugin. Their
names have changed as follows:

| Old type | Old name | New type | New Name | Tags | Comments |
| --- | --- | --- | --- | --- | --- |
| Meter | `plugins.<plugin>.response.status.<statuscode>` | Counter | `plugin.response` | `plugin=<plugin>`<br>`statusCode=<statuscode>` |
| Meter | `plugins.<plugin>.exception.<type>` | Counter | `plugin.exception` | `plugin=<plugin>`<br>`type=<type>` |
| Meter | `plugins.<plugin>.errors` | Counter | `plugin.error` | `plugin=<plugin>` |
