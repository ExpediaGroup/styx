# Scenarios

## Transforming Request and Response headers

### Synchronously transforming requests
 
Transforming request object synchronously is trivial. Just transform the HttpRequest object via the HttpRequest.Builder 
object, and pass it to the chain.proceed(), as the example demonstrates.

    import com.hotels.styx.api.HttpInterceptor;
    import com.hotels.styx.api.HttpRequest;
    import com.hotels.styx.api.HttpResponse;
 
    public class FooAppendingInterceptor implements HttpInterceptor {
        @Override
        public Observable<HttpResponse> intercept(HttpRequest request, Chain chain) {
            return chain.proceed(
                    request.newBuilder()
                            .header("X-Foo", "Bar")
                            .build());
        }
    }
    
### Synchronously transforming response

In this example, we will synchronously transom the response by adding an "X-Foo" header to it.

Use Rx Java map() to transform a response object synchronously. 

Remember that chain.proceed() returns an Rx Observable of HttpResponse. We'll use map() to register a callback that Java
Rx framework calls back once the response becomes available. In the example below, it is the lambda expression 
HttpResponse -> HttpResponse that is called when response is available. The lambda expression constructs a new 
HttpResponse object with the new header added.

The map() itself returns a new Rx Observable<HttpResponse>, satisfying the type signature for intercept() method. 
Therefore it can be returned as such from intercept().

	import com.hotels.styx.api.HttpInterceptor;
	import com.hotels.styx.api.HttpRequest;
	import com.hotels.styx.api.HttpResponse;
	 
	public class FooAppendingInterceptor implements HttpInterceptor {
	    @Override
	    public Observable<HttpResponse> intercept(HttpRequest request, Chain chain) {
	        return chain.proceed(request)
	                .map(response -> response.newBuilder()
	                                         .header("X-Foo", "Bar")
	                                         .build());
	    }
	}
	
	
### Asynchronously transform request object

Sometimes it is necessary to transform the request asynchronously. For example, it may be necessary to consult an 
external service(s) like databases and such to look up necessary information for the request transformation. In this case:
1. Perform a non-blocking call to an asynchronous API. It is best to wrap such API behind Rx Java observables.
2. Call the chain.proceed(request) when the the asynchronous operation completes.
3. Because chain.proceed() itself is an asynchronous operation returning Observable[HttpResponse], it needs to be called from inside Rx flatMap() operator.

In the following example, the interceptor either allows or rejects the request based on a query to an external service.
The API for external service is wrapped behind requestAccessQueryAsynch() method. Normally a method like this would 
initiate a transaction to the external service, and return a Future, Promise, Rx Observable, etc. of the outcome. 
For sake of simplicity, here it returns an Observable.just(true).

The flatMap() operator allows us to register a function that itself performs an asynchronous operation. In our example, 
requestAccessQueryAsynch() returns back an Observable[Boolean] indicating if the access is granted or not. When the 
access query completes, Rx framework calls out to the registered function, which in this example is a lambda expression 
of type Boolean => Observable[HttpResponse]. The lambda expression transforms the access grant outcome into a HttpResponse 
object. This is an asynchronous transformation, because the lambda expression calls out to asynchronous chain.proceed().

Our lambda expression looks into the access grant outcome. When true, the request is proxied onwards by a call to 
chain.proceed(request). Otherwise a FORBIDDEN response is returned.
	
	import com.hotels.styx.api.HttpInterceptor;
	import com.hotels.styx.api.HttpRequest;
	import com.hotels.styx.api.HttpResponse;
	...
	 
    public class AsyncRequestDelayInterceptor implements HttpInterceptor {
   	    private static final HttpResponse rejected = HttpResponse.Builder.response(FORBIDDEN).build();
        @Override
        public Observable<HttpResponse> intercept(HttpRequest request, Chain chain) {
            return requestAccessQueryAsync(request)
                .flatMap((allowed) -> allowed ? chain.proceed(request) : Observable.just(rejected));
        }
        
        private Observable<Boolean> requestAccessQueryAsync(HttpRequest request) {
            // do something asynchronous
            // ...
	        return Observable.create((downstream) -> {
	           CompletableFuture.supplyAsync(() -> {
	               downstream.onNext("foo"); 
	               downstream.onComplete();
	           });
	        });
        }
    }
	
### Asynchronously transform response object

Processing a HTTP response asynchronously is very similar to processing response synchronously. Instead of Rx map() 
operator, you'll use a flatMap().  In this example, we assume asyncOperation() makes a network call, performs I/O, 
or off-loads response processing to a separate thread pool. For this reason it returns an Observable[HttpResponse] 
so that it can emit a transformed response once completed. 

The Rx framework calls asyncOperation() once the the response becomes available from the adjacent plugin. A call to 
asyncOperation() triggers another asynchronous operation, whose result becomes available via the returned observable, 
which is linked to the Observable chain using the Rx flatMap() operator.


	import rx.Observable;
	import rx.schedulers.Schedulers;
	import rx.lang.scala.ImplicitFunctionConversions.*;
	import com.hotels.styx.api.HttpInterceptor;
	import com.hotels.styx.api.HttpRequest;
	import com.hotels.styx.api.HttpResponse;
	import com.hotels.styx.api.HttpInterceptor.Chain;
	...
	 
	public class AsyncResponseProcessingPlugin implements HttpInterceptor {
	  public Observable<HttpResponse> intercept(HttpRequest request, Chain chain) {
	    return chain.proceed(request).flatMap(this::asyncOperation);
	  }
	   
	  private Observable<HttpResponse> asyncOperation(HttpResponse response) {
	    return Observable.just(response);
	  }
	}
	
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
