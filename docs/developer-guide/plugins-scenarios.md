# Scenarios

## Transforming Request and Response headers

### Synchronously transforming requests
 
Transforming a request object synchronously is trivial. By "synchronously" we mean in the 
same thread, in non-blocking fashion. 

Just call `request.newBuilder()` to create a new `HttpRequest.Builder` object. 
It has already been initialised with the copy of the original `request`. Modify 
the builder as desired, construct a new version, and pass it to the `chain.proceed()`, 
as the example demonstrates:

```java
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.StyxObservable;
import com.hotels.styx.api.plugins.spi.Plugin;

import static com.hotels.styx.api.HttpHeaderNames.USER_AGENT;

public class SyncRequestPlugin implements Plugin {
    @Override
    public StyxObservable<HttpResponse> intercept(HttpRequest request, Chain chain) {
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
use a `StyxObservable.map` method to add an "X-Foo" header to the response.

```java
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.StyxObservable;
import com.hotels.styx.api.plugins.spi.Plugin;

public class SyncResponsePlugin implements Plugin {
    @Override
    public StyxObservable<HttpResponse> intercept(HttpRequest request, HttpInterceptor.Chain chain) {
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
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.StyxObservable;
import com.hotels.styx.api.Url;
import com.hotels.styx.api.plugins.spi.Plugin;

import java.util.concurrent.CompletableFuture;

public class AsyncRequestInterceptor implements Plugin {

    @Override
    public StyxObservable<HttpResponse> intercept(HttpRequest request, HttpInterceptor.Chain chain) {
        
        return asyncUrlReplacement(request.url())                                       // (1)
                .map(newUrl -> request.newBuilder()                                     // (4)
                        .url(newUrl)
                        .build())
                .flatMap(chain::proceed);                                               // (5)
    }

    private static StyxObservable<Url> asyncUrlReplacement(Url url) {
        return StyxObservable.from(pathReplacementService(url.path()))                  // (3)
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

Step 1. We call the `asyncUrlReplacement`, which returns a `StyxObservable<Url>`.
The `asyncUrlReplacement` wraps a call to the remote service and converts 
the outcome into a `StyxObservable`, which is the basis for our response observable.

Step 2. A call to `pathReplacementService` makes a non-blocking call to the remote key/value store.
Well, at least we pretend to call the key value store, but in this example we'll just return a 
completed future of a constant value.   

Step 3. `CompletableFuture` is converted to `StyxObservable`, so that other asynchronous
operations like `chain.proceed` can be bound to it later on.

Step 4. The eventual outcome from the `asyncUrlReplacement` yields a new, modified URL instance.
We'll transform the `request` by substituting the URL path with the new one. 
This is a quick synchronous operation so we'll do it in a `map` operator. 

Step 5. Finally, we will bind the outcome from `chain.proceed` into the response observable.
Remember that `chain.proceed` returns an `Observable<HttpResponse>` it is therefore 
interface compatible and can be `flatMap`'d to the response observable. The resulting
response observable chain is returned from the `intercept`.


### Asynchronously transform response object

This example demonstrates asynchronous response processing. Here we pretend that `callTo3rdParty` method
makes a non-blocking request to retrieve string that is inserted into a response header. 

A `callTo3rdParty` returns a `CompletableFuture` which asynchronously produces a string value. We 
will call this function when the HTTP response is received. 

```java
import com.hotels.styx.api.HttpInterceptor;
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.StyxObservable;
import com.hotels.styx.api.plugins.spi.Plugin;

import java.util.concurrent.CompletableFuture;

public class AsyncResponseInterceptor implements Plugin {
    private static final String X_MY_HEADER = "X-My-Header";

    @Override
    public StyxObservable<HttpResponse> intercept(HttpRequest request, HttpInterceptor.Chain chain) {
        return chain.proceed(request)                                                                            // (1)
                .flatMap(response ->                                                                             // (3)
                        StyxObservable
                                .from(thirdPartyHeaderService(response.header(X_MY_HEADER).orElse("default")))            // (1)
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

Step 1. We start by calling `chain.proceed(request)` to obtain a response observable. 

Step 2. A `callTo3rdParty` returns a CompletableFuture. We use `StyxObservable.from` to convert it 
into a `StyxObservable` so that it can be bound to the response observable. 

Step 3. The `flatMap` operator binds `callToThirdParty` into the response observable.

Step 4. We will transform the HTTP response by inserting an outcome of `callToThirdParty`, or `value`, 
into the response headers.	


## HTTP Content Transformations	

Styx exposes HTTP messages to interceptors as streaming `HttpRequest` and `HttpResponse` messages.
In this form, the interceptors can process the content in a streaming fashion. That is, they they
can look into, and modify the content as it streams through.

Alternatively, streaming messages can be aggregated into a `FullHttpRequest` or `FullHttpResponse` 
messages. The full HTTP message body is then available for the interceptor to use. Note that content
aggregation is always an asynchronous operation. This is because the streaming HTTP message is 
exposing the content, in byte buffers, as it arrives from the network, and Styx must wait until 
all content has been received. 


### Aggregating Content into Full Messages

```java
import com.hotels.styx.api.HttpRequest;
import com.hotels.styx.api.HttpResponse;
import com.hotels.styx.api.StyxObservable;
import com.hotels.styx.api.plugins.spi.Plugin;

public class RequestAggregationPlugin implements Plugin {
    private static final int MAX_CONTENT_LENGTH = 100000;

    @Override
    public StyxObservable<HttpResponse> intercept(HttpRequest request, Chain chain) {
        return request.toFullRequest(MAX_CONTENT_LENGTH)
                .map(fullRequest -> fullRequest.newBuilder()
                        .body(new byte[0], true)
                        .build())
                .flatMap(fullRequest -> chain.proceed(fullRequest.toStreamingRequest()));
    }
}
```

`maxContentBytes` is a safety valve that prevents Styx from accumulating too much content
and possibly running out of memory. Styx only accumulates up to `maxContentBytes` of content. 
`toFullRequest` fails when the content stream exceeds this amount, and  Styx emits a 
`ContentOverflowException`,  


### Transformations on Streaming HTTP response content

Streaming HTTP body content can be transformed both synchronously and asynchronously.
However there are some pitfalls you need to know:

 - Reference counting. Styx exposes the content stream as an observable of reference counted
   Netty `ByteBuf` objects. Ensure the reference counts are correctly decremented when
   buffers are transformed. 
   
 - Continuity (or discontinuity) of Styx content observable. Each content tranformation with 
  `map` or `flatMap` is a composition of some source observable. So is each content transformation 
  linked to some source observable, an ultimate source being the Styx server core.
  It is the consumer's responsibility to ensure this link never gets broken. That is, you 
  are not allowed to just substitute the content observable with another one, unless it composes
  to the previous content source.


### Synchronously transforming streaming request content

In this rather contrived example we will transform HTTP response content to 
upper case letters. 

Streaming `HttpRequest` content is a byte buffer stream. The stream can be accessed
with a `request.body()` method and its data type is `StyxObservable<ByteBuf>`.

Because `toUpperCase` is non-blocking transformation we can compose it to the original
byte stream using a `map` operator.

The mapping function decodes the original byte buffer `buf` to an UTF-8 encoded
Java `String`, and copies its upper case representation into a newly created byte buffer.
The old byte buffer `buf` is discarded.

Because `buf` is a Netty reference counted `ByteBuf`, we must take care to decrement its
reference count by calling `buf.release()`. 

```java
public class MyPlugin extends Plugin {
    @Override
    public StyxObservable<HttpResponse> intercept(HttpRequest request, Chain chain) {
        StyxObservable<ByteBuf> toUpperCase = request.body()
                .map(buf -> {
                    buf.release();
                    return copiedBuffer(buf.toString(UTF_8).toUpperCase(), UTF_8);
                });

        return chain.proceed(
                request.newBuilder()
                        .body(toUpperCase)
                        .build()
        );
    }
}    
```

### Asynchronously transforming streaming request content

Asynchronous content stream transformation is very similar to the synchronous transformation.
The only difference is that asynchronous transformation must be composed with a `flatMap` 
instead of `map`. Otherwise the same discussion apply. As always it is important to take care
of the reference counting.

```java
public class AsyncRequestContentTransformation implements Plugin {

    @Override
    public StyxObservable<HttpResponse> intercept(HttpRequest request, Chain chain) {
        StyxObservable<ByteBuf> contentMapping = request.body()
                .flatMap(buf -> {
                    String content = buf.toString(UTF_8);
                    buf.release();
                    return sendToRemoteServer(content)
                            .map(value -> copiedBuffer(value, UTF_8));
                });

        return chain.proceed(
                request.newBuilder()
                        .body(contentMapping)
                        .build()
        );
    }
    
    StyxObservable<String> sendToRemoteServer(String buf) {
        return StyxObservable.of("modified 3rd party content");
    }
}    
```

### Synchronously transforming streaming response content

```java
public class AsyncResponseContentStreamTransformation implements Plugin {
    @Override
    public StyxObservable<HttpResponse> intercept(HttpRequest request, Chain chain) {
        return chain.proceed(request)
                .map(response -> {
                        StyxObservable<ByteBuf> contentMapping = response.body()
                                .map(buf -> {
                                    buf.release();
                                    return copiedBuffer(buf.toString(UTF_8).toUpperCase(), UTF_8);
                                });
                        return response.newBuilder()
                                .body(contentMapping)
                                .build();
                });
    }
}
```

### Asynchronously transforming streaming response content

```java
public class AsyncResponseContentStreamTransformation implements Plugin {
    @Override
    public StyxObservable<HttpResponse> intercept(HttpRequest request, Chain chain) {
        return chain.proceed(request)
                .map(response -> {
                        StyxObservable<ByteBuf> contentMapping = response.body()
                                .flatMap(buf -> {
                                    String content = buf.toString(UTF_8);
                                    buf.release();
                                    return sendToRemoteServer(content)
                                            .map(value -> copiedBuffer(value, UTF_8));
                                });
                        return response.newBuilder()
                                .body(contentMapping)
                                .build();
                });
    }

    StyxObservable<String> sendToRemoteServer(String buf) {
        return StyxObservable.of("modified 3rd party content");
    }
}    
```