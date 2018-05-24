# Scenarios

## Transforming Request and Response headers

### Synchronously transforming requests
 
Transforming request object synchronously is trivial. Just call `request.newBuilder()` to
create a new `HttpRequest.Builder` object. It has already been initialised with the copy of
the original `request`. Modify the builder as desired, consturct a new version, and pass
it to the `chain.proceed()`, as the example demonstrates:

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
use a `StyxObservable` `map` method to add an "X-Foo" header to the response.

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

Sometimes it is necessary to transform the request asynchronously. For example, may need to 
look up external key value stores to parametrise the request transformation. The example below
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
        
        return StyxObservable.of(request)                                               // (1)
                .flatMap(na -> asyncUrlReplacement(request.url()))                      // (2)
                .map(newUrl -> request.newBuilder()                                     // (5)
                        .url(newUrl)
                        .build())
                .flatMap(chain::proceed);                                               // (6)
    }

    private static StyxObservable<Url> asyncUrlReplacement(Url url) {
        return StyxObservable.from(pathReplacementService(url.path()))                  // (4)
                .map(newPath -> new Url.Builder(url)
                        .path(newPath)
                        .build());
    }

    private static CompletableFuture<String> pathReplacementService(String url) {
        return CompletableFuture.completedFuture("/replacement/path");                  // (3)
    }
}
```

Step 1. For asynchronous transformation we'll start by constructing a response observable. 
We can construct it with any initial value, but in this example we'll just use the `request`. 
But it doesn't have to be a `request` as it is available from the closure.

Step 2. We will call the `asyncUrlReplacement`, and bind it to the response observable using 
`flatMap` operator. The `asyncUrlReplacement` wraps a call to the remote service and converts 
the outcome into a `StyxObservable`.

Step 3. A call to `pathReplacementService` makes a non-blocking call to the remote key/value store.
Well, at least we pretend to call the key value store, but in this example we'll just return a 
completed future of a constant value.   

Step 4. `CompletableFuture` needs to be converted to `StyxObservable` so that the operation can
be bound to the response observable created previously in step 1.

Step 5. The eventual outcome from the `asyncUrlReplacement` yields a new, modified URL instance.
We will map this to a new `HttpRequest` instance replaced URL.

Step 6. Finally, we will bind the outcome of `chain.proceed` into the response observable.
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
                                .from(callTo3rdParty(response.header(X_MY_HEADER).orElse("default")))            // (1)
                                .map(value ->                                                                    // (4)
                                        response.newBuilder()
                                                .header(X_MY_HEADER, value)
                                                .build())
                );
    }

    private static CompletableFuture<String> callTo3rdParty(String myHeader) {
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


## Transforming HTTP Content	

HTTP content can be processed in a streaming fashion, or it can be aggregated into one big blob and decoded into a 
business domain object. Here we will explain how to do this. And as always, this can be done in a synchronous or 
asynchronous fashion.

Content transformation always involves creating a new copy of a proxied HTTP object and overriding its body content. 
Because the body itself is an Observable<ByteBuf>, it can be transformed using similar techniques as demonstrated for 
the HTTP headers above. The example below transforms the HTTP response content in as streaming fashion using a 
transformContent() function. The new response is created by invoking a response.newBuilder() method. Using the 
resulting builder, the response body is overridden. 

	chain.proceed(request)
	  .map((HttpResponse response) -> response.newBuilder()
	      .body(response.body().content().map(this::transformContent))
	      .build()
	  );

As always, the content can be transformed both synchronously or asynchronously. In this document we will explore all the options.

Content can be decoded to a business domain object for further processing.

### Decode content into business domain objects

Decoding content into a business domain object is always an asynchronous operation. This is because Styx must wait 
until it has received enough HTTP content to attempt decoding content.

HttpResponse object has a decode() method that can be used to decode HTTP response content into business domain objects. 
The decode() method has the following type:

    public <T> Observable<DecodedResponse<T>> decode(Function<ByteBuf, T> decoder, int maxContentBytes)
    
The decode() takes two arguments: a decoder function, and a maxContentBytes integer. It returns an Observable<DecodedResponse<T>> 
which is underlines its asynchronous nature. The decode() accumulates entire HTTP response content into a single aggregated 
byte buffer, and then calls the supplied  decoder method passing in the aggregated buffer as a parameter. The decoder 
is a function, or typically a lambda expression supplied by the plugin, that converts the aggregated http content into 
a business domain object of type T. Styx will take care of managing all aspects related to direct memory buffers, such 
as releasing them if necessary.

The maxContentBytes is a safety valve that prevents styx from accumulating too much content. Styx will only accumulate 
up to maxContentBytes of content. If the content exceeds this amount, Styx will emit a ContentOverflowException. 
This is to prevent out of memory exceptions in face of very large responses.

Because decode() itself is an asynchronous operation, the plugin must call it inside a flatMap() operator. 
When the HTTP response has arrived, and its content decoded, a DecodedResponse instance is emitted. The DecodedResponse 
contains the decoded business domain object along with a HTTP response builder that can be used for further transformations.

In the example below, we will map over the decodedResponse to add a new "bytes_aggregated" header to the response, containing 
a string length (as opposed to content length) of the received response content. Note that it is necessary to add the 
response body back to the new response by calling decodedResponse.responseBuilder().body().

	
	@Override
	public Observable<HttpResponse> intercept(HttpRequest request, Chain chain) {
	    return chain.proceed(request)
	            .flatMap(response -> response.decode((byteBuf) -> byteBuf.toString(UTF_8), maxContentBytes))
	            .map(decodedResponse -> decodedResponse.responseBuilder()
	                    .header("test_plugin", "yes")
	                    .header("bytes_aggregated", decodedResponse.body().length())
	                    .body(decodedResponse.body())
	                    .build());
	} 

### Synchronously transform streaming request content

In the following example, we will transform the HTTP response content to upper case letters. 

Because the transformation to upper case does not involve IO or blocking operations, it can be done synchronously with 
Rx map() operator. First we create a new observable called toUpperCase which contains the necessary transformation on 
the body observable.  Then we create a new HTTP request object passing in the transformed observable as a new body.

Notice that the lambda expression for the map() operator receives a ByteBuf buf as an argument. We must assume it is 
a reference counted buffer, and to avoid leaks we must call buf.release() before returning a copy from the lambda expression.
	
	import com.hotels.styx.api.HttpRequest;
	import com.hotels.styx.api.HttpResponse;
	import io.netty.buffer.ByteBuf;
	import rx.Observable;
	import static com.google.common.base.Charsets.UTF_8;
	import static io.netty.buffer.Unpooled.copiedBuffer;
	 
	...
	 
	@Override
	public Observable<HttpResponse> intercept(HttpRequest request, Chain chain) {
	    Observable<ByteBuf> toUpperCase = request.body()
	            .content()
	            .map(buf -> {
	                String transformed = buf.toString(UTF_8).toUpperCase();
	                buf.release();
	                return copiedBuffer(transformed, UTF_8);
	            });
	 
	    return chain.proceed(
	            request.newBuilder()
	                    .body(toUpperCase)
	                    .build()
	    );
	}
	
### [DRAFT] Replace the HTTP response content with a new body and discarding the existing one

In the following example the plugin replaces the entire HTTP response content with a custom response content.

In this scenario the plugin creates a new HttpResponse based on the existing one. However changing entirely the response
content can be a source of memory leaks if the current content is not programmatically released. This is because the 
Observable content resides in the direct memory whereas the rest of the plugin will be in the heap. If the content will 
not be manually released it will remain in the direct memory and eventually it will cause Styx to fail with a:

java.lang.OutOfMemoryError: Direct buffer memory

In order to avoid memory leaks it is necessary to release the existing ByteBuf by using the doOnNext() operator which 
creates a new Observable with a side-effect behavior. In this specific case the side-effect is a call to a function which 
releases the reference count. Make sure to subscribe to this new Observable otherwise the function will not be executed.
	
	import com.hotels.styx.api.HttpRequest;
	import com.hotels.styx.api.HttpResponse;
	import com.hotels.styx.api.plugins.spi.Plugin;
	import io.netty.util.ReferenceCountUtil;
	import rx.Observable;
	 
	...
	 
	@Override
	public Observable<HttpResponse> intercept(HttpRequest request, Chain chain) {
	    Observable<HttpResponse> responseObservable = chain.proceed(request);
	    return responseObservable.map(response -> {
	        response.body().content().doOnNext(byteBuf -> ReferenceCountUtil.release(byteBuf)).subscribe();
	        return response.newBuilder().body("Custom HTTP response content").build();
	    });
	}
