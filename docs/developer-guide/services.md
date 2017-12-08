- Services are configured in `services.factories` config block:

```
    services:
      factories:
        backendServiceRegistry:
          class: "com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistry$Factory"
          config: {originsFile: "${originsFile}"}
        graphite:
          class: "com.hotels.styx.metrics.reporting.graphite.GraphiteReporterServiceFactory"
          config:
            prefix: "STYXPRES.${CONFIG_ENVIRONMENT:}.${jvmRouteName:noJvmRouteSet}"
            host: "data-internal.lab.hcom"
            port: 2003
            intervalMillis: 15000
        jmx:
          class: "com.hotels.styx.metrics.reporting.jmx.JmxReporterServiceFactory"
          config:
            domain: "com.hotels.styx"

```

- The configuration specifies a Factory class for each 3rd party service.
  Styx loads this Factory class, and invokes its `create()` method. This
  instantiates the service which is loaded into styx.

- To implement your own service, you will need to implement a factory
  class which constructs the service. The factory class must implement
  a `ServiceFactory<E>` interface.

