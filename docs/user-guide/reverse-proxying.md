# Styx Reverse Proxy and Routing

## Overview

Styx is a reverse proxy server with a powerful, programmable *interceptor 
pipeline*. All received traffic is exposed to the interceptor pipeline before 
being proxied to the *Backend Services*. 
 
The interceptor pipeline can extended with *plugins*. 

The interceptor pipeline alters the way Styx handles the received traffic. The
pluggable nature with intuitive APIs makes it easy to implement custom HTTP proxy 
applications on top of Styx.

At the end of the interceptors pipeline is a *Backend Services Router*. Using
a set of configurable rules it determines which backend service a received HTTP 
request is proxied to.

Some things to note about this model:

  * Request propagates through the pipeline in the order the interceptors (plugins)
    are configured in the pipeline. The response is intercepted in the reverse order.
    
  * An interceptor may *modify*, *respond* or *drop* the request. A technical
    detail: 
    *Typically interceptors wouldn't have to drop the request. They can simply
    respond with a relevant status code instead. However if a request is dropped, 
    Styx automatically responds on plugin's behalf.*

  * If the request makes it to the end of the interceptors pipeline, it is 
    proxied to the relevant backend service.
    

## Protocols Conversion
 
Styx server can consume HTTP, HTTPS, or both protocols at the same time 
(though on different ports). It terminates these protocols before passing the
traffic to the interceptors pipeline.

When Styx proxies a request to backend services, it sends the request over the 
protocol configured for the backend: either HTTP or HTTPS.

It is important to understand that Styx converts the protocols when necessary.
For example, if Styx proxy server is configured to receive HTTP traffic, 
and the backend service is configured to use HTTPS, then Styx will convert
from the HTTP protocol to HTTPS.

## Cut-Through Proxying and Flow Control

To reduce latency, Styx starts proxying a request immediately when the HTTP headers
have been received. It does not have to wait for the request body, nor store the
full request before it can make a forwarding decision. The rest of the request (the 
body) will stream through Styx.

However, the interceptor plugins can change this behaviour. They are able to store 
and forward the request/response should it be necessary for the business requirements. 

Styx uses flow control to adapt the relative speeds of upstream and downstream 
when proxying, protecting Styx from being overwhelmed when the sender 
is sending faster than the receiver can consume.

## Styx Response Codes

Styx responds with specific error codes when things don't go according to plan.
Here is a summary of those codes:

#### 502 Bad Gateway

* No route configured to backend service. An unexpected path prefix is received,
  and no default backend has been configured. A default backend is one associated
  with "/" (root path) prefix. 

* There are no origin servers available for the backend service. For example,
  the origins are marked as inactive.

* Styx is unable to establish a TCP connection to an origin.

* Invalid HTTP response is received from the backend origin server.

* Origin does an *abortive TCP connection release* by setting the *RST*
  bit on the TCP response segment.


#### 503 Service Unavailable

* The connection pool for the origin is full. That is, the maximum number of both busy connections 
 and pending (waiting) connections has been reached.
  
* The connection pool for the origin is full and the connection has been pending 
  (waiting) for maximum amount of time allowed `pendingConnectionTimeoutMillis`.
  

#### 504 Gateway Timeout
 
* Styx has been waiting for a backend service origin to send data, but has not received anything 
  for `responseTimeout` milliseconds.
  
  
  