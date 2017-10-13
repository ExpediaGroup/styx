# Writing end-to-end Scala tests for Styx

## Overview

A typical e2e test consist of three components, 1) test origins, 2) styx server, and 3) a test client.
These parts must be created and wired together in a test setup phase. We have implemented several helpers 
to make it easy to set up these three parts.

The overall structure of a test is the following:

    class FooSpec extends FunSpec 
                  with StyxProxySpec 
                  with ... {
      
      // Create and start an origin server:
      val backend = FakeHttpServer.HttpStartupConfig().start()
      
      // Declare styx configuration:
      styxConfig = StyxConfig(plugins = List("aggregator" -> new AggregationTesterPlugin(2750)))

      override protected def beforeAll(): Unit = {
        // Start styx:
        super.beforeAll()
        
        // Connect styx to backends:
        styxServer.setBackends("/" -> HttpBackend("app1", Origins(backend)))
      }
      
      override protected def afterAll(): Unit = {
        // Stop origins:
        mockServer.stopAsync().awaitTerminated()

        // Stop styx:
        super.afterAll()
      }


      ... test follows
    }

 
## Styx server

Use com.hotels.styx.StyxProxySpec trait to manage styx server lifecycle. Mix it in to the test spec,
and it takes care of starting a styx server in a `beforeAll()` block (hence super.beforeAll() from overridden
version above) and stoping it in `afterAll()`.

The StyxProxySpec trait exposes a variable called `styxConfig` (of type StyxConfig). Should you need
to customise any aspect of styx, just declare a new StyxConf configuration and set it to `styxConfig` variable
in a test case constructor. For example:

    class BarSpec extends FunSpec with StyxProxySpec {        
        val styxConfig = StyxConfig(plugins = List("asyncDelayPlugin" -> new AsyncRequestDelayPlugin()))
        ...
        
Note that while it is possible for you to specify the port numbers for http, https, and admin connectors,
you normally do not want to do this. Leave them with default values (defaults to 0), and StyxProxySpec 
automatically allocates them when starting Styx.

The styx server is accessible via `styxServer` variable. It is just an instance of the normal Java `StyxServer`
class. However StyxProxySpec adds useful features this bare StyxServer via implicit `StyxServerOperations` class
that is automatically in scope. With this you have the following operations added to the `styxServer`:


    def httpsProxyHost: String

    def proxyHost: String

    def adminHost: String

    def secureRouterURL(path: String): String

    def routerURL(path: String): String

    def adminURL(path: String): String

    def secureHttpPort: Int

    def httpPort: Int

    def adminPort: Int

    def metricsSnapshot: CodaHaleMetricsFacade

    def setBackends(backends: (String, StyxBackend)*): Unit
    
   
## Configuring Backends

There are several different options to use for backend origins:

  - Wiremock
  - FakeHttpServer (based on WireMock)
  - HttpServer
  - HttpsOriginServer
  
The recommended origin is the Wire-Mock based FakeHttpServer. It is suitable for most of
styx system tests. It is useful for mocking RESTful applications at application level. For
testing some proxying aspects it is necessary to test at HTTP or even TCP level. For 
these situations we have HttpServer and HttpsOriginServer classes.
 
We look at how to create these different types of origins in the following sections, but
before that let's look at how to configure the backends to Styx.

Say you have an instance of a FakeHttpServer:

    val backend = FakeHttpServer.HttpStartupConfig(appId = "myApp", originId = "myApp-01").start()
    
Now to configure this as a backend for styx:    

    styxServer.setBackends("/" -> HttpBackend("myApp", Origins(backend)))
    
This sets up a mapping from "/" path prefix to the declared HttpBackend. Remember that `styxServer`
was brought into scope from StyxProxySpec. The `HttpBackend` (there is also a `HttpsBackend` for HTTPS backends)
is a Scala case class which declares a configuration for the backend. In this example, the default 
configuration is applied for everything except for application ID and origins list. As a Scala case
class it has an attribute for every BackendService attribute, but they are initialised with sensible
defaults. In order to override, just specify the desired attribute with named arguments:

    import scala.concurrent.duration._    

    styxServer.setBackends("/" -> HttpBackend(
                                    "myApp", 
                                    Origins(backend),
                                    responseTimeout = 5.seconds
                                    ))

Note about `Origins()` declaration. It takes in a list of Origin objects. But how come it then accepted
a FakeHttpServer `backend`? This is because `StyxProxySpec` also brings into a scope an
`com.hotels.styx.support.ImplicitOriginConversions` trait which automatically converts various
origin types, such as FakeHttpServer into an Origin, so you can avoid converting them manually.
    
    
## FakeHttpServer
    
You create a FakeHttpServer by declaring its properties via `FakeHttpServer.HttpStartupConfig` or
`FakeHttpServer.HttpsStartupConfig` case classes. To start the server, you would call a `start()` 
method on a configuration class.

The port numbers are allocated when the server is started. Also a default endpoint is added
to the server for the root `"/"` path.


 
    
## Using Styx clients

There are two Styx clients available for scala tests. One is for testing low-level networking,
 another is for suitable for application level testing.

### Using StyxHttpClient via StyxClientSupplier trait

A trait called `StyxClientSupplier` provides an HTTP client for the scala tests. When mixed in,
it adds these to the test:

 - val client: HttpClient
 - def doHttpRequest(HttpRequest): HttpResponse
 - def doSecureRequest(HttpRequest): HttpResponse
 - def doRequest(HttpRequest): HttpResponse
 
An example of usage:

    import com.hotels.styx.StyxServerSupport.StyxServerOperations
    import com.hotels.styx.HealthCheckSpec
   
    class HealthCheckSpec extends FunSpec 
        with StyxClientSupplier 
        with ... {

      val styxServer = StyxConfig().startSync()
      
      ...

      val (response, body) = decodedRequest(
        get("/")
          .addHeader(HOST, styxServer.proxyHost)
          .build())

      assert(response.status() == INTERNAL_SERVER_ERROR)
      assert(body == "something went wrong")
      

      
