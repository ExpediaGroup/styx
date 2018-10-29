# Scenarios

## Transforming Request and Response headers

### Synchronously transforming requests
 
Transforming a request object synchronously is trivial. By "synchronously" we mean in the 
same thread, in non-blocking fashion. 

Just call `request.newBuilder` to create a new `HttpRequest.Builder` object. 
It has already been initialised with the copy of the original `request`. Modify 
the builder as desired, construct a new version, and pass it to the `chain.proceed()`, 
as the example demonstrates:

```java
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.plugins.spi.Plugin;

import static com.hotels.styx.api.HttpHeaderNames.USER_AGENT;

public class SyncRequestPlugin implements Plugin {
    @Override
    public Eventual<LiveHttpResponse> intercept(LiveHttpRequest request, Chain chain) {
        return chain.proceed(
                request.newBuilder()
                    .header(USER_AGENT, "Styx/1.0 just testing plugins")
                    .build()
        );
    }
}
```
    
### Synchronously transforming response

This example demonstrates how to synchronously transform a HTTP response. We will
call `Eventual.map` to add an "X-Foo" header to the response.

```java
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.plugins.spi.Plugin;

public class SyncResponsePlugin implements Plugin {
    @Override
    public Eventual<LiveHttpResponse> intercept(LiveHttpRequest request, HttpInterceptor.Chain chain) {
        return chain.proceed(request)
                .map(response -> response.newBuilder()
                        .header("X-Foo", "bar")
                        .build()
                );
    }
}
```
	
### Asynchronously transform request object

Sometimes it is necessary to transform the request asynchronously. For example, you may need to 
look up external key value stores to parameterise the request transformation. The example below
shows how to modify a request URL path based on an asynchronous lookup to an external database.
 	
```java
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.Url;
import com.hotels.styx.api.plugins.spi.Plugin;

import java.util.concurrent.CompletableFuture;

public class AsyncRequestInterceptor implements Plugin {

    @Override
    public Eventual<LiveHttpResponse> intercept(LiveHttpRequest request, HttpInterceptor.Chain chain) {
        
        return asyncUrlReplacement(request.url())                                       // (1)
                .map(newUrl -> request.newBuilder()                                     // (4)
                        .url(newUrl)
                        .build())
                .flatMap(chain::proceed);                                               // (5)
    }

    private static Eventual<Url> asyncUrlReplacement(Url url) {
        return Eventual.from(pathReplacementService(url.path()))                  // (3)
                .map(newPath -> new Url.Builder(url)
                        .path(newPath)
                        .build());
    }

    private static CompletableFuture<String> pathReplacementService(String url) {
        // Pretend to make a call here:
        return CompletableFuture.completedFuture("/replacement/path");                  // (2)
    }
}
```

Step 1. We call the `asyncUrlReplacement`, which returns an `Eventual<Url>`.
The `asyncUrlReplacement` wraps a call to the remote service and converts 
the outcome into an `Eventual`.

Step 2. A call to `pathReplacementService` makes a non-blocking call to the remote key/value store.
Well, at least we pretend to call the key value store, but in this example we'll just return a 
completed future of a constant value.   

Step 3. `CompletableFuture` is converted to an `Eventual`, so that other asynchronous
operations like `chain.proceed` can be bound to it later on.

Step 4. The eventual outcome from the `asyncUrlReplacement` yields a new, modified URL instance.
We'll transform the `request` by substituting the URL path with the new one. 
This is a quick synchronous operation so we'll do it in a `map` operator. 

Step 5. Finally, we will bind the outcome from `chain.proceed` into the response `Eventual`.
Remember that `chain.proceed` returns an `Eventual<LiveHttpResponse>`. It is, therefore,
interface compatible and can be `flatMap`'d to the response `Eventual`. The resulting
response `Eventual` chain is returned from the `intercept`.


### Asynchronously transform response object

This example demonstrates asynchronous response processing. Here we pretend that `callTo3rdParty` method
makes a non-blocking request to retrieve string that is inserted into a response header. 

A `callTo3rdParty` returns a `CompletableFuture` which asynchronously produces a string value. We 
will call this function when the HTTP response is received. 

```java
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.plugins.spi.Plugin;

import java.util.concurrent.CompletableFuture;

public class AsyncResponseInterceptor implements Plugin {
    private static final String X_MY_HEADER = "X-My-Header";

    @Override
    public Eventual<LiveHttpResponse> intercept(LiveHttpRequest request, HttpInterceptor.Chain chain) {
        return chain.proceed(request)                                                                            // (1)
                .flatMap(response ->                                                                             // (3)
                        Eventual.from(thirdPartyHeaderService(response.header(X_MY_HEADER).orElse("default")))   // (2)
                                .map(value ->                                                                    // (4)
                                        response.newBuilder()
                                                .header(X_MY_HEADER, value)
                                                .build())
                );
    }

    private static CompletableFuture<String> thirdPartyHeaderService(String myHeader) {
        // Pretend to make a call here:
        return CompletableFuture.completedFuture("value");
    }
}
```

Step 1. We start by calling `chain.proceed(request)` to obtain a response `Eventual`. 

Step 2. A `callTo3rdParty` returns a `CompletableFuture`. It is converted to an `Eventual` with
`Eventual.from` and bound to the response `Eventual`.

Step 3. The `flatMap` operator binds `callToThirdParty` into the response `Eventual`.

Step 4. We will transform the HTTP response by inserting an outcome of `callToThirdParty`, or `value`, 
into the response headers.	


## HTTP Content Transformations	

Styx exposes HTTP messages to interceptors as streaming `LiveHttpRequest` and `LiveHttpResponse` messages.
In this form, the interceptors can process the content in a streaming fashion. That is, they they
can look into, and modify the content as it streams through.

Alternatively, live messages can be aggregated to `HttpRequest` or `HttpResponse` 
messages. The full HTTP message body is then available for the interceptor to use. Note that content
aggregation is always an asynchronous operation. This is because the streaming HTTP message is 
exposing the content, in byte buffers, as it arrives from the network, and Styx must wait until 
all content has been received. 


### Aggregating Content into Full Messages

```java
import com.hotels.styx.api.LiveHttpRequest;
import com.hotels.styx.api.LiveHttpResponse;
import com.hotels.styx.api.Eventual;
import com.hotels.styx.api.plugins.spi.Plugin;

public class RequestAggregationPlugin implements Plugin {
    private static final int MAX_CONTENT_LENGTH = 100000;

    @Override
    public Eventual<LiveHttpResponse> intercept(LiveHttpRequest request, Chain chain) {
        return request.toFullRequest(MAX_CONTENT_LENGTH)
                .map(fullRequest -> fullRequest.newBuilder()
                        .body(new byte[0], true)
                        .build())
                .flatMap(fullRequest -> chain.proceed(fullRequest.toStreamingRequest()));
    }
}
```

`maxContentBytes` is a safety valve that prevents Styx from accumulating too much content
and running out of memory. Styx only accumulates up to `maxContentBytes` of content. 
`toFullRequest` fails when the content stream exceeds this amount, and  Styx emits a 
`ContentOverflowException`,  


### Transformations on Streaming HTTP response content

Streaming HTTP body content can be transformed both synchronously and asynchronously.
However there are some pitfalls you need to know:

 - **Reference counting:** Styx exposes the content stream as a `ByteStream` of reference counted
  `Buffer` objects. 
  
 - **Continuity (or discontinuity) of Styx content `ByteStream`:** each content transformation with 
  `map` or `flatMap` is a composition of another `ByteStream`. So is each content transformation 
  linked to some source `ByteStream`, an ultimate source being the Styx server core.
  It is the consumer's responsibility to ensure this link never gets broken. That is, you 
  are not allowed to just replace the content stream with another one, unless it composes
  to the previous content source.


### Synchronously transforming streaming request content

In this rather contrived example we will transform HTTP response content to 
upper case letters. 

```java
public class MyPlugin extends Plugin {
    @Override
    public Eventual<LiveHttpResponse> intercept(LiveHttpRequest request, Chain chain) {
        return chain.proceed(
                request.newBuilder()
                        .body(byteStream ->                                                              // 1
                                byteStream.map(buf -> {                                                  // 2
                                    String upperCase = new String(buf.content(), UTF_8).toUpperCase();   // 3
                                    return new Buffer(upperCase, UTF_8);
                                }))
                        .build()
        );
    }
}    
```

A call to `request.newBuilder` opens up a new request builder that allows the request 
to be transformed.

The body transformation involves two lambda methods:

  1. `.body(Function<ByteStream, ByteStream>)` accepts a lambda that modifies the request
     byte stream. Here we provide a lambda that accepts a `ByteStream` and returns another
     by applying a synchronous `map` operator on the stream.
     
  2. The `ByteStream` is modified by applying a `map` operator that synchronously modifies
     each `Buffer` that streams through. The `map` involves another lambda that accepts a
     `Buffer` and returns a modified buffer.
  
  3. In this example, the content of the buffer is interpreted as `String` and 
     converted to upper case. 
  

### Synchronously transforming streaming response content

This example demonstrates how HTTP response content can be transformed synchronously.

```java
public class AsyncResponseContentStreamTransformation implements Plugin {
    @Override
    public Eventual<LiveHttpResponse> intercept(LiveHttpRequest request, Chain chain) {
        return chain.proceed(request)
            .map(response ->                                                                        // 1
                response.newBuilder()                         
                    .body(byteStream ->                                                             // 2
                            byteStream.map(buf -> {                                                 // 3
                                String upperCase = new String(buf.content(), UTF_8).toUpperCase();  // 4
                                return new Buffer(upperCase, UTF_8);
                            }))
                    .build());
    }
}
```

This is quite similar to the request transformation, but it involves an additional lambda
expression to capture the HTTP response from its `Eventual` envelope:

  1. `chain.proceed` returns an `Eventual` HTTP response. We apply an `Eventual.map` operator
     to tap into this response. The `map` operator accepts a lambda expression that handles the
     response when it is available.
     
After that, we will transform the response just like we did in the previous example for 
response content transformations.



### Asynchronously transforming streaming request content

Not supported at the moment. Please raise a feature request and the Styx team can implement this.





### Asynchronously transforming streaming response content

Not supported at the moment. Please raise a feature request and the Styx team can implement this.
