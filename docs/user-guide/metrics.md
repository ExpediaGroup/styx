# Metrics

Styx collects metrics to provide insight into its operational status. They
can be viewed via admin interface at `http://<styx-host>/admin/metrics`.
They can also be exported into a Graphite monitoring backend. Support for
other monitoring back-ends can be implemented via service point interface.

## Viewing Specific Metrics via the Admin Interface

In addition to viewing the entire set of metrics, it is also possible to request specific metrics like this:

`http://<styx-host>/admin/metrics/<metric-name>`

This will return the metric that matches the specified name (if it exists) as well as any metrics
that use the specified name as a prefix.

For example `http://<styx-host>/admin/metrics/requests.response` would return

    requests.response.sent
    requests.response.status.1xx
    requests.response.status.2xx
    ...
    
## Searching Metrics for string

Use a `filter` query parameter to filter for metrics names matching a given string. 
For example `filter=count` only shows metrics whose name contains `count`. The filtering is applied to the results of the metrics query.

Examples where `term` is the string you want to filter for:

`http://<styx-host>/admin/metrics?filter=<term>`

`http://<styx-host>/admin/metrics/<metric-name>?filter=<term>`

    

# Metrics Reporter Configuration

## Graphite Reporter

To enable metrics reporting to Graphite backend, add `graphite` as a
service name in the `services.factories` configuration block.

Here is an example configuration:

    services:
      factories:
        graphite:
          class: "com.hotels.styx.metrics.reporting.graphite.GraphiteReporterServiceFactory"
          config:
            prefix: "styx-lab-02"
            host: "some-ip"
            port: 2003
            intervalMillis: 15000

* `class` - Set to `com.hotels.styx.metrics.reporting.graphite.GraphiteReporterServiceFactory`
  to enable reporting to Graphite backend in particular.

* `config`

  * `prefix` - A prefix string that is prepended to metric names.
    Allows you to identify the particular styx instance when several styx
    instances are reporting to the same Graphite backend.

  * `host` - Graphite server host name.

  * `port` - Graphite server port.

  * `intervalMillis` - A metrics reporting interval, in milliseconds.


# Styx Metrics Reference

A [Styx Metrics Reference](./metrics-reference.md) has a detailed description for each metric.
