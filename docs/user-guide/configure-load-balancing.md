# Load Balancing Configuration

Styx supports three load balancing strategies:

 - Round robin
 - Busy
 - Adaptive

Styx also provides a mechanism to bypass the load balancer and force
the origin at source.

## Load balancing strategies

### Round Robin

This mode cycles through available origins, choosing the next in sequence on each use,
while skipping over the origins with saturated connection pools.

### Busy

This load balancing algorithm attempts to find the best origin
to serve the request, based on various real-time metrics such as the number 
of readily available TCP connections, current pool usage, and 
the observed 5xx response rate from the origins.

### Adaptive

Adaptive load balancing strategy is a combination of *Round Robin* and
*Busy* strategies. It always starts off with *Round Robin*, and after a while
it switches over to *Busy* strategy. It remains in *Busy* strategy until
a new origin is added to rotation. It then reverts to *Round Robin*, and adapts
as described.

## Origins Restriction

When origins restriction feature is enabled, you can force the HTTP
request to be proxied to the origin of choice.

For example, to force a request to be forwarded to an origin called
*landing-03*, you would add the origins restriction cookie to the
request with a value of "landing-03".

The value can also be set to a comma-separated list of regular expressions.
In that case, Styx proxies the request to any of the origins matching 
the specified regular expressions.

To enable this feature, add `originRestrictionCookie` configuration
attribute to the Styx proxy configuration file as follows:

    originRestrictionCookie: <restriction-cookie-name>

The *restriction-cookie-name* is a string value of your choice. It is
the name of the cookie that should contain the origins restriction information.

## Configuration

Load balancing strategies and the origin restriction feature are configured
in the Styx proxy configuration file.


To enable Round Robin load balancing strategy:

    loadBalancing:
      strategy:
        factory: {class: "com.hotels.styx.client.loadbalancing.strategies.RoundRobinStrategy$Factory"}
        
To enable Busy load balancing strategy:

    loadBalancing:
      strategy:
        factory: {class: "com.hotels.styx.client.loadbalancing.strategies.BusyConnectionsStrategy$Factory"}

To enable Adaptive load balancing strategy:

    loadBalancing:
      strategy:
        factory:
          class: "com.hotels.styx.client.loadbalancing.strategies.AdaptiveStrategy$Factory"
          config:
            requestCount: 100


The *requestCount* attribute determines how long the adaptive strategy
remains in the Round Robin phase before switching over to the *Busy* strategy.
Its value is the number of requests proxied *per origin*.

