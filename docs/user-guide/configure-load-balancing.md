# Load Balancing Configuration

Styx supports three load balancing strategies:

 - Round robin
 - Busy
 - Adaptive

Styx also provides a mechanism to bypass the load balancer and force
the origin at source.


## Round Robin

Cycles through available origins, choosing the next in sequence on each use,
 skipping over the origins with saturated connection pools.

## Busy

This load balancing algorithm attempts to find a best origin
for serving the request, based on various instantaneous metrics like
number of readily available TCP connections, current pool usage, and
the observed 5xx response rate from the origins.

## Adaptive

Adaptive load balancing strategy is a combination of *Round Robin* and
*Busy* strategies. It always starts off with *Round Robin*, and after a while
it switches over to *Busy* strategy. It remains in *Busy* strategy until
a new origin is added to rotation. It then reverts to *Round Robin* adapts
as described.

## Origins Restriction

When origins restriction feature is enabled, you can force the HTTP
request to be proxied to the origin of choice.

For example, to force a request to be forwarded to an origin called
*landing-03*, you would add the origins restriction cookie to the
request with a value of "landing-03".

The value can be set to a comma-separated list of regular expressions too.
In this case Styx proxies the request to any one of the origins matching 
the specified regular expressions.

To enable this feature, add `originRestrictionCookie` configuration
attribute to the Styx proxy configuration file as follows:

    originRestrictionCookie: <restriction-cookie-name>

The *restriction-cookie-name* is a string value of your choice. It is
names the cookie Styx considers as an origins restriction cookie.

## Configuration

Load balancing strategies and the origin restriction feature are configured
in the Styx proxy configuration file.

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
remains in the Round Robin phase before switching over to *Busy* strategy.
Its value is the number of requests proxied *per origin*.

To enable Round Robin load balancing strategy:

    loadBalancing:
      strategy:
        factory: {class: "com.hotels.styx.client.loadbalancing.strategies.RoundRobinStrategy$Factory"}

