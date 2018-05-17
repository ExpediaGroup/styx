# Advanced Routing Configuration

For advanced scenarios Styx offers a configurable routing
object model for building HTTP pipelines. These pipelines
can be hierarchical, and they can branch off at various points
when necessary.

The routing object model describes how HTTP traffic flows through
the Styx pipeline. It can be fully manipulated with YAML
configuration.

Styx also implements a domain specific language (DSL) for matching
and dissecting HTTP traffic. This is used together with routing
object model to make routing decisions.

Styx HTTP interceptors can be inserted at various points on the pipeline.
Styx offers some built-in interceptors. They can also be instantiated
from styx interceptor plugins as usual.

## Example

This example configuration routes http and https traffic to separate
backends.

    httpPipeline:
      name: entrypoint
      type: InterceptorPipeline
      config:
        pipeline:
          - redirect-old-domain
          - bad-cookie-fixer
        handler:
          name: protocol-router
          destination: protocolCheck

 ----- 

    routingObjects:
      proxyToSecure:
         type: BackendServiceProxy
         config:
           backendProvider: "https-backends"

      proxyToInsecure:
         type: BackendServiceProxy
         config:
           backendProvider: "http-backends"
           
           
      proxyToEdgeAuthSrv:
         type: BackendServiceProxy
         config:
           ..
           connectionPool:
             ...
           tlsSettings:
             ...
           healthCheck:
             ...

      proxyToErrorLoggingService:
         type: BackendServiceProxy
         config:
           ..
           connectionPool:
             ...
           tlsSettings:
             ...
           healthCheck:
             ...

      pathPrefixForSecureApps:
         type: PathPrefix
         config:

      pathPrefixForInsecureApps:
         type: PathPrefix
         config:

      protocolCheck:
          type: ConditionRouter
          config:
            choice:
              - condition: protocol() == "https"
                destination: pathPrefixForSecureApps
            default:
              destination: proxyToErrorLoggingService

      trySecureFirst:
          type: Duplicate
          config:
            copyTo: errorLogger
            next: protocolCheck 
              


Without going too much into details (specifics will be explained later)
a hierarchical structure should be apparent. The pipeline itself is an
*InterceptorPipeline* object which runs the traffic through a pipeline of
two interceptors, in this case plugins:

 - *redirect-old-domain*
 - *bad-cookie-fixer* before

After this pipeline, the traffic is handled by a *ConditionRouter* which
sends all traffic satisfying the condition *protocol() == "https" to a 
BackendServiceProxy called *secure-backends*, and all the other traffic to
another BackendServiceProxy called *insecure-backends*.

Graphically the object model can be represented as:

    entrypoint
    <InterceptorPipeline>
         |
         | - redirect-old-domain
         |
         | - bad-cookie-fixer
         |
         v
     protocol-router
     <ConditionRouter>
         |       |
         |       +------------------+
         |                          |
         |                          |
         v                          v
      secure-backends           insecure-backends
      <BackendServiceProxy>     <BackendServiceProxy>


## Concepts

The routing object model consist of *Http Handler* and *Interceptor* objects.

The *Http Handler* objects consume and handle HTTP requests in some
non-trivial ways, such as proxying the traffic to backend services, etc.
The *InterceptorPipeline*, *ConditionRouter*, and a *BackendServiceProxy*
in above example are all HTTP handlers. Some handlers like *InterceptorPipeline*
or *ConditionRouter* can pass the traffic for other handlers to consume.

HTTP *Interceptor* objects are intended to perform simple actions
(eg logging) or transforming requests and/or responses along the way
(Rewrite).

List of built-in handlers:

 - BackendServiceProxy. It proxies to configured backends based on the URL path prefix.
 - HttpInterceptorPipeline. It runs the request through the interceptor pipeline before passing on to the next handler.
 - ProxyToBackend. It proxies to an individual configured backend service.
 - StaticResponseHandler. Responds with specified response.

List of built-in interceptors:

 - Rewrite. Rewrites URLs.


## Enabling Advanced Routing

To enable advanced routing, declare `httpPipeline` attribute in the
main styx configuration file.


## Configuration of the Building Blocks

### Routing Config Objects

Routing object model is configured in Yaml with *Routing Config Nodes*.
It is either a `ROUTING-CONFIG-REF` which is a reference to another named
object, or a `ROUTING-CONFIG-DEF` that is block of of configuration.

A `ROUTNG-CONFIG-DEF` has a common format of:

    ROUTING-CONFIG-DEF:
       name: <optional, a descriptive name>
       type: <type>
       config:
           <A type specific configuration block>

The list of types and their configuration layouts are specified below.

A `ROUTING-CONFIG-REF` is just a string that is supposed to reference
to another routing config object, styx service, or plugin.


### HttpInterceptorPipeline

Runs the configured interceptors (or plugins) before passing on to
the next handler in the processing chain.

*Configuration*:

    name: <descriptive name for this object (optional)>
    type: InterceptorPipeline
    config:
        pipeline:
           <ROUTING-CONFIG-NODE-LIST>
        handler:
           <ROUTING-CONFIG-DEFINITION>

*Pipeline*:

Routing config node list can have both routing config references and
definitions mixed together. A routing config reference must always
refer to a valid plugin name that has been declared in the Styx *plugins*
section. A routing config definition can be used to insert styx built-in
interceptors.

*Handler*:

This is a routing config definition block that defines the handler used.


### ConditionRouter

The Condition router subjects the HTTP request to a set of tests, or *conditions*, that determine
which handler to pass the request to next.

*Configuration*:

    name: <descriptive name for this object (optional)>
    type: ConditionRouter
    config:
        routes:
           <CONDITION-DESTINATION-LIST>
        fallback:
           <ROUTING-CONFIG-DEFINITION>

*Routes*:

This block contains a list of destinations that are activated depending on the outcome of the conditions.

    CONDITION-DESTINATION:
       condition: <STRING, DSL predicate>
       destination:
         <ROUTING-CONFIG-DEFINITION>

Conditions are evaluated in the order in which they appear in the routes list.
The request is sent to the first destination that results in a positive
match from the condition.

*Fallback*:

An optional field that specifies a handler which the request is sent
when none of the configured routes match with the request. When fallback
is absent, a *502 Bad Gateway* is returned by default.


### ProxyToBackend

Proxies a request to a backend service.

*Configuration*:

    name: <descriptive name for this object (optional)>
    type: ProxyToBackend
    config:
        backend:
           <STYX-HTTP-BACKEND-DEF>

The Styx HTTP backend definition follows the syntax of backends in the origins file,
 but without the path attribute.

      backend:
        id: "ba"
        connectionPool:
          maxConnectionsPerHost: 45
          maxPendingConnectionsPerHost: 15
        responseTimeoutMillis: 60000
        origins:
        - { id: "ba1", host: "localhost:9094" }


### StaticResponseHandler

Responds with a preconfigured response.

    name: <descriptive name for this object (optional)>
    type: StaticResponseHandler
    config:
        status: <HTTP status code: int>
        content: <a string that gets added to the content>


### BackendServiceProxy

Standard path-prefix based router/proxy to backend services.

    name: <descriptive name for this object (optional)>
    type: BackendServiceProxy
    config:
        backendProvider: <name>

The `backendProvider` attribute must refer to a named backend service
in the `services` section. For example, to proxy between HTTP and HTTPS
origins based on the incoming protocol:

    httpPipeline:
      name: entrypoint
      type: InterceptorPipeline
      config:
        handler:
          name: protocol-router
          type: ConditionRouter
          config:
            routes:
              - condition: protocol() == "https"
                destination:
                  name: secure-backends
                  type: BackendServiceProxy
                  config:
                    backendProvider: "https-backends"
            fallback:
              name: insecure-backends
              type: BackendServiceProxy
              config:
                backendProvider: "http-backends"

And in the services:

    services:
      factories:
        http-backends:
          class: "com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistry$Factory"
          config: {originsFile: "/path/to/http-origins.yml"}
        https-backends:
          class: "com.hotels.styx.proxy.backends.file.FileBackedBackendServicesRegistry$Factory"
          config: {originsFile: "/path/to/https-origins.yml"}



## Routing DSL

The routing DSL supports the following functions:

    method()     - returns the HTTP method name as a string
    path()       - returns the URL path as a string
    userAgent()  - returns the user agent header as a tring
    protocol()   - returns the protocol (http, or https) as a string
    header(NAME) - returns a given header value
    cookie(NAME) - returns a given cookie name


These functions are always used as a part of equivalency tests,
so that the `ConditionRouter` predicate always evaluates to either
true or false.

Both single and double quotes can be used to quote strings. Regular
expressions are given as string, and therefore they also need to
be quoted with either single or double quotes.

Some examples below:

    path() == "/some-path"

Presence of a Host header:

    header("Host")

Regular expression matching:

    header("Host") =~ "^/.*\.co\.uk"

Can be combined with Boolean operators:

    header("Host") == "bbc.co.uk" AND header("Content-Length") == "7"

    header("Host") == "bbc.co.uk" OR header("Content-Length") == "7"

    NOT header("Host")

    header("Host") AND (header("X-Site-Info") =~ "nwa[0-9]" OR header("X-Site-Info") =~ "xda[0-9]")

Cookie values:

    cookie("TheCookie") == "foobar-baz"

User agent:

    userAgent() == "Mozilla Firefox 1.1.2" OR userAgent() =~ "Safari.*"

