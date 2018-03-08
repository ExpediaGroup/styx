# Styx Reverse Proxy and Routing

## Overview

Styx is a reverse proxy server with a powerful, programmable *interceptor 
pipeline*. All received traffic is exposed to the interceptor pipeline before 
being proxied to the *Backend Services*. 
 
The interceptor pipeline can be extended with *plugins*. 

The interceptor pipeline alters the way Styx handles the received traffic. The
pluggable nature with an intuitive API makes it easy to implement custom HTTP proxy 
applications on top of Styx.

At the end of the interceptors pipeline, there is a *Backend Services Router*. Using
a set of configurable rules, this router determines which backend service a received HTTP 
request is proxied to. In this model, an *origin* server is each of the hosts implementing 
a backend service.

Some things to note about this model:

  * Request propagates through the pipeline in the order the interceptors (plugins)
    are configured in the pipeline. The response is intercepted in the reverse order.
    
  * An interceptor may *modify*, *respond* or *drop* a request. A technical
    detail: 
    *Typically interceptors wouldn't have to drop the request. They can simply
    respond with a relevant status code instead. However if a request is dropped, 
    Styx automatically responds on the plugin's behalf.*

  * If the request makes it to the end of the interceptors pipeline, it is 
    proxied to the relevant backend service.
    

## Protocols Conversion
 
Styx server can consume HTTP, HTTPS, or both protocols at the same time 
(but on different ports). It terminates these protocols before passing the
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
and forward the full request/response should it be necessary for the business requirements. 

Styx uses flow control to adapt the relative speeds of upstream and downstream 
when proxying, and thus protecting Styx from being overwhelmed when the sender 
is sending faster than the receiver can consume.

## Response codes
For the list of possible response codes, see [Response codes](response-codes.md)
  
  
  