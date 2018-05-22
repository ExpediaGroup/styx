
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

 
The API offers an easy conversion between the two.

Call `.toFullRequest(Int maxContentBytes)` (or `.toFullResponse(Int maxContentBytes)`) 
on the streaming message object to aggregate it to a `FullHttpRequest` (or `FullHttpResponse`).
The full type signature of `.toFullNNN` is:

```java
   public StyxObservable<FullHttpRequest> toFullRequest(int maxContentBytes);
```
  
This instructs Styx Core to start aggregating up to `maxContentBytes`. The operation is 
asynchronous as indicated by `StyxObservable<FullHttpResponse>` return type. Once the 
content is fully aggregated a `FullHttpResponse` object is emitted via the observable.

To convert a full message to streaming, you would call `toStreamingRequest()` 
(or `toStreamingResponse`). Because content aggregation is not involved, the API is
simpler:

```java
    public HttpRequest toStreamingRequest()
``` 
 
 
### Http Interceptor Interface

### Http Handler Interface

### Styx Observable

   