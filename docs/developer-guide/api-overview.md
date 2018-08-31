
# Styx API Overview

Styx provides an Application Programming Interface (API) for implementing custom extensions.
As of writing the extensions can be (1) HTTP intercepting plugins, or (2) backend service 
providers. Some other extension points will be opened in future.

Besides Styx extensions, the API can be used as a library in other software projects. The
`styx-server` module makes it really easy to start Styx based HTTP(s) servers with just a 
few lines of code. The `styx-client` can be used as a reactive HTTP client.

The `styx-api-testsupport` provides helpers for unit testing Styx extensions. 

The relevant modules are available in Maven Central:

  * Styx API: https://mvnrepository.com/artifact/com.hotels.styx/styx-api
  * Styx API Test Support: https://mvnrepository.com/artifact/com.hotels.styx/styx-api-testsupport
  * Styx Server: https://mvnrepository.com/artifact/com.hotels.styx/styx-server
  * Styx Client: https://mvnrepository.com/artifact/com.hotels.styx/styx-client
  
## Main Components

The main API components are:

   * HTTP Message Abstractions: HttpRequest, HttpResponse, FullHttpRequest, FullHttpResponse
   * HttpInterceptor
   * HttpHandler
   * StyxObservable
   
### HTTP Message Abstractions

Styx HTTP message abstractions are the cornerstone of the API. They come in two flavours, 
supporting (1) streaming HTTP messages, and (2) "full" HTTP messages.

To achieve high performance with reduced memory footprint, Styx treats the proxied 
HTTP messages as byte streams. It decodes HTTP message headers into a HTTP message object,
but the message body flows through as a stream of content chunks. This way Styx is
able to deal with arbitrarily large content streams.

Streaming HTTP messages, represented by `HttpRequest` and `HttpResponse` classes,
are the keys to the Styx core. The main Styx extension points, `HttpInterceptor` 
and `HttpHandler` interfaces (described later) interact only on the streaming requests. 
Hardly surprising  given that internally Styx core sees everything as a HTTP stream. 

However often times it is much more convenient to deal with HTTP messages when both
the headers and content come together in one atomic unit. The API therefore provides
`FullHttpRequest` and `FullHttpResponse` classes. They are immutable, fully re-usable
HTTP message abstractions that are really convenient in unit testing, admin interfaces,
and in 3rd party software built on Styx libraries.

Few words about message interoperability. The streaming and full variants are not 
interface compatible as per
[Liskov substitution principle](https://en.wikipedia.org/wiki/Liskov_substitution_principle) 
and therefore they form two separate class hierarchies. The nature of the content,
streaming vs full, is reflected in the type signatures of the content accessors:

* Streaming HTTP messages: 

```java
    public class HttpRequest { 
        ...
        
        public StyxObservable<ByteBuf> body() { .. }
    }
```

* Full HTTP messages:
   
```java
    public class FullHttpRequest { 
        ...       
        public byte[] body() { .. }
        
        public String bodyAs(Charset charset) { .. }        
    }
```

### Conversion Between HTTP Message Types
 
It is easy to convert between the streaming and full HTTP messages.  

Streaming `HttpRequest` has a `toFullRequest` method (`toFullResponse` for `HttpResponse`).
It aggregates a HTTP message body stream into one continuous byte array and creates a 
corresponding `FullHttpRequest` (`FullHttpResponse`). Its type signature is:

```java
   public StyxObservable<FullHttpRequest> toFullRequest(int maxContentBytes);
```

Note that the HTTP content streams represents live network traffic streaming
through Styx core. The content stream is not memoized nor stored. 
When the content is gone, it is gone for good. 
Therefore a `HttpRequest` (`HttpResponse`) can be aggregated to a full 
message strictly once only.

The type signature illustrates other facts about the content stream:

* Return type of `StyxObservable` shows that HTTP content stream is 
  aggregated asynchronously. Obviously because the rest of the content 
  stream are yet to be received from the network.

* The `maxContentBytes` sets an upper limit for the message 
  body, protecting Styx from exhausting memory in face of very long message
  bodies, or perhaps from denial of service attacks. If the limit is reached,
  the conversion fails with `ContentOverflowException`. 

Converting a full message to a stream is much easier. The `FullHttpRequest` 
(`FullHttpResponse`) has a method called `toStreamingRequest` (or `toStreamingResponse`): 
```java
    public HttpRequest toStreamingRequest()
``` 

The method signature reveals that `HttpResponse` is available immediately.
Because the content is permanently stored in full, you can clone a
full message object into as many streaming objects as necessary. For example:

```java
    public class PingHandler extends BaseHttpHandler {
        private static final FullHttpResponse PONG = response(OK)
                .disableCaching()
                .contentType(PLAIN_TEXT_UTF_8)
                .body("pong", UTF_8)
                .build();
        
        @Override
        protected HttpResponse doHandle(HttpRequest request) {
            return PONG.toStreamingResponse();
        }
    }   
```
 
### Http Interceptor Interface

A HTTP interceptor is an object that transforms, responds, or runs side-effecting actions 
for HTTP traffic passing through. 

Styx arranges interceptors in a linear chain, forming a core of its proxying pipeline.
All recieved traffic goes through the interceptor chain which then acts on the traffic
accordingly.  
  
Styx has some internal Styx interceptors. But normally it is the custom plugins that
add value for Styx deployments. An extension point for a custom plugin is the 
`HttpInterceptor` interface. It has only one method:

```java
    public interface HttpInterceptor {
       ...
       StyxObservable<HttpResponse> intercept(HttpRequest request, Chain chain);
    }
```

A Styx plugin is an implementation of this method. 

It is the `intercept` method which transforms or acts on a received request, 
and its corresponding response. As an event based system, all implementations 
must be strictly non-blocking. Blocking the thread would stall Styx event processing 
loop. So take care to stick with asynchronous implementation.

The received request is passed in as its first argument. The second argument, `Chain`, is
a handle to the remaining tail of the `HttpInterceptor` chain. The most important
function of the chain is the `proceed` method. It passes the request to the
next interceptor in the chain. It returns a response observable, in which you
bind any response transformations. For example:


```java
    @Override
    public StyxObservable<HttpResponse> intercept(HttpRequest request, Chain chain) {
        HttpRequest newRequest = request.newBuilder()
                .header(VIA, viaHeader(request))
                .build();

        return chain.proceed(newRequest)
                .map(response -> response.newBuilder()
                        .header(VIA, viaHeader(response))
                        .build());
    }
```

The chain also contains a request context which can be obtained with a 
call to `chain.context()`. It is a set of key-value properties associated 
with the request. Plugins may store information in the context.
Styx core also add snippets of information like sender IP address, and so on. 

### Http Handler Interface

Http Handler interface forms a basis for Styx admin interfaces and routing objects. 
As opposed to `HttpInterceptor`s, which just pass the messages down the pipeline,
the `HttpHandler` is meant to *consume* the HTTP request. It is a similarly simple
interface:

```java
    public interface HttpHandler {
        StyxObservable<HttpResponse> handle(HttpRequest request, HttpInterceptor.Context context);
    }
```

It asynchronously processes the request, and returns the response within a context of 
`StyxObservable`. It is given the `HttpInterceptor.Context` as a second argument, so that it is able
to access the request context properties.

Notice the absence of `Chain`. Therefore it is not able to proceed the message any further.

As with `HttpInterceptor` implementations, the `handle` method must never block. Blocking the
thread will block the Styx event loop.  


### Styx Observable

Conceptually similar to Futures, `StyxObservable` is a data type that 
facilitates asynchronous event handling, modelled after Rx 
[observables](http://reactivex.io/documentation/observable.html).

However `rx.Observable` is a very generic reactive stream abstraction. 
The `StyxObservable` is an observable that has been adapted 
for the specific Styx use case, which is of processing live network data 
streams. 

Another key difference between the two observables is that `StyxObservable`
does not have a `subscribe` method. More precisely it has been hidden 
to prevent 3rd party extensions from subscribing to live data streams. 
This is a privileged operation that for the reliable operation is exclusive 
for Styx core only.  

Styx `HttpInterceptor` and `HttpHandler` objects merely build a pipeline of 
`StyxObservable` operators modelling a data path for HTTP response processing. 
The interceptors operate in a  "sand-boxed" environment and Styx core 
triggers the subscription when it sees fit.  
