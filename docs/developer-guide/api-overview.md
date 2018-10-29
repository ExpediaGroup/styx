
# Styx API Overview

Styx provides an Application Programming Interface (API) for implementing custom extensions.
As of writing, the extensions can be (1) HTTP interceptor plugins, or (2) backend service 
providers. Other extension points will be opened in future.

Besides Styx extensions, Styx modules can be used as libraries in other software projects. 
The `styx-server` module makes it really easy to start Styx-based HTTP(s) servers with just a 
few lines of code. The `styx-client` can be used as a reactive HTTP client.

The `styx-api-testsupport` provides helpers for unit testing Styx extensions. 

The relevant modules are available in Maven Central:

  * Styx API: https://mvnrepository.com/artifact/com.hotels.styx/styx-api
  * Styx API Test Support: https://mvnrepository.com/artifact/com.hotels.styx/styx-api-testsupport
  * Styx Server: https://mvnrepository.com/artifact/com.hotels.styx/styx-server
  * Styx Client: https://mvnrepository.com/artifact/com.hotels.styx/styx-client
  
## Main Components

Important API concepts are:

   * HTTP message abstractions: `HttpRequest`, `HttpResponse`, `LiveHttpRequest`, `LiveHttpResponse`
   * HTTP message consumers: `HttpInterceptor`, `HttpHandler`
   * Asynchronous event support: `Eventual`
   
### HTTP Message Abstractions

Styx provides two kinds of HTTP message abstractions: (1) Streaming HTTP message classes for 
intercepting live traffic and (2) Immutable HTTP message classes that are generally easier 
to work with.

The `LiveHttpRequest` and `LiveHttpResponse` classes have immutable headers, but the message
body is a stream of byte buffer events, represented by a `ByteStream` class. 

Styx proxy uses these live messages internally. It propagates all HTTP body content received 
on the proxy ingress interface as `ByteStream` events. Because all content chunks are processed
immediately, it is able to minimise proxying latency, and because the message body 
is never fully buffered, Styx is able to proxy arbitrarily long and large message bodies
with minimal memory pressure. 
 
These classes are "live" because they stream through the Styx pipeline in "real time". The "live" 
prefix is meant to serve as a mnemonic for the API consumers to treat them appropriately:
  
  * A `ByteStream` can be transformed and aggregated, but it can not be replayed. 

  * When a `ByteStream` has been consumed, it has been consumed for good. 

The main Styx extension points, `HttpInterceptor` and `HttpHandler` interfaces 
(described later) interact only on the live requests. This is because internally Styx
core processes everything as a HTTP stream. 

For most other situations it is more convenient to use "full" HTTP messages
that provide the HTTP headers and the full body in one immutable unit.
For this purpose, Styx provides `HttpRequest` and `HttpResponse` classes. They are immutable, 
fully re-usable HTTP message classes that are more convenient for unit testing, admin interfaces,
and for other applications built on Styx libraries.

A few words about message interoperability: the live and immutable variants are not 
interface compatible as per
[Liskov substitution principle](https://en.wikipedia.org/wiki/Liskov_substitution_principle) 
and therefore they form two separate class hierarchies. This is evident in the
HTTP content accessors:

* `LiveHttpRequest` (and `LiveHttpResponse`): 

```java
    public class LiveHttpRequest { 
        ...
        
        public ByteStream body() { .. }
    }
```

* `HttpRequest` (and `HttpResponse`)
   
```java
    public class HttpRequest { 
        ...       
        public byte[] body() { .. }
        
        public String bodyAs(Charset charset) { .. }        
    }
```

### Conversion Between HTTP Message Types 

Call the `aggregate` method to convert a `LiveHttpRequest`/`LiveHttpResponse`
to an immutable `HttpRequest`/`HttpResponse` object: 

```java
   public Eventual<HttpRequest> aggregate(int maxContentBytes);
```

This aggregates a HTTP message body stream and creates a corresponding immutable 
HTTP object. Note that `aggregate` consumes the underlying live HTTP message `ByteStream`.

The type signature illustrates other facts about the content stream:

* We can see that `aggregate` is asynchronous by its return type, `Eventual`. 
  This is because it needs to wait for all of the byte stream to be fully available.

* The `maxContentBytes` sets an upper limit for the aggregated message 
  body. This acts as a safety valve, protecting Styx from exhausting the heap memory
  when very long message bodies are received. 
  If the limit is reached, the conversion fails with `ContentOverflowException`. 

Call the `stream` method to convert an immutable `HttpRequest` 
/`HttpResponse` to a `LiveHttpRequest`/`LiveHttpResponse`:
 
```java
    public LiveHttpRequest stream()
``` 

The method signature reveals that `LiveHttpResponse` is available immediately.
Because the content is permanently stored in full, you can clone an immutable
message object into as many streaming objects as necessary. For example:

```java
    public class PingHandler extends BaseHttpHandler {
        private static final HttpResponse PONG = response(OK)
                .disableCaching()
                .contentType(PLAIN_TEXT_UTF_8)
                .body("pong", UTF_8)
                .build();
        
        @Override
        protected LiveHttpResponse doHandle(LiveHttpRequest request) {
            return PONG.stream();
        }
    }   
```
 
### Http Interceptor Interface

Styx has an HTTP pipeline that processes all received HTTP messages.
The pipeline is made of a linear chain of HTTP interceptors. An HTTP interceptor 
transforms, responds, or runs side-effecting actions on live HTTP traffic.
  
Styx provides some internal interceptors. You can also implement your own
interceptor plugins. An extension point for a 
custom plugin is the `HttpInterceptor` interface. It has only one method:

```java
    public interface HttpInterceptor {
       ...
       Eventual<LiveHttpResponse> intercept(LiveHttpRequest request, Chain chain);
       ...
    }
```

The interface shows that the `intercept` method receives a live HTTP request, 
and eventually returns a live HTTP response. 

`intercept` may transform or run side effecting actions on request or response objects. 
As an event based system, all implementations must be strictly non-blocking. 
Blocking a thread stalls the Styx event processing loop. So take care to 
stick with asynchronous implementation.

An HTTP request is passed in as its first argument. The second argument, `Chain`, is
a handle to the next `HttpInterceptor` in the pipeline. The `Chain` has a `proceed` method
that passes the request to the next interceptor. It returns an `Eventual<Response>`, in 
which you bind any response transformations. For example:

```java
    @Override
    public Eventual<LiveHttpResponse> intercept(LiveHttpRequest request, Chain chain) {
        LiveHttpRequest newRequest = request.newBuilder()
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

An `HttpHandler` interface is the basis for Styx admin interfaces and routing objects. 
It *consumes* the HTTP requests and asynchronously responds with HTTP responses. 
It has a simple interface:

```java
    public interface HttpHandler {
        Eventual<LiveHttpResponse> handle(LiveHttpRequest request, HttpInterceptor.Context context);
    }
```

It accepts a live HTTP request as its first argument, and eventually responds with a live HTTP reponse. 
The second argument is an `HttpInterceptor.Context`. The request context contains contextual
information such as the sender of the request and whether it was delivered over a secure protocol or not.

As with `HttpInterceptor` implementations, the `handle` method must never block. Blocking the
thread will block the Styx event loop.  


### Eventual

The `Eventual` class is an envelope for a value that will be available some time in the future.
It represents a deferred value that becomes eventually available, and thus enables 
asynchronous operations in Styx. If `Eventual` appears as a method return type then that
method is asynchronous. 

The `Eventual` behaves much like a future (such as the Java 8+ `CompletableFuture`) but it
implements the Reactive Streams `Publisher` interface and is much simpler overall. 
