# Load Balancing Configuration

Styx supports three load balancing strategies:

 - Round robin
 - Busy

Styx also provides a mechanism to bypass the load balancer and force
the origin at source.

## Load balancing strategies

### Round Robin

This mode cycles through available origins, choosing the next in sequence on each use,
while skipping over the origins with saturated connection pools.

### Busy

This algorithm always returns the origin with a least number of 
simultaneously ongoing requests. Randomly chooses a winner
when there is a tie between the "best" origins.

### Power Of Two

This load balancing algorithm randomly picks two origins, and chooses the
better out of the two. 


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

To enable Busy load balancing strategy:

    loadBalancing:
      strategy:
        factory: {class: "com.hotels.styx.client.loadbalancing.strategies.PowerOfTwoStrategy$Factory"}

To enable Busy load balancing strategy:

    loadBalancing:
      strategy:
        factory: {class: "com.hotels.styx.client.loadbalancing.strategies.BusyConnectionsStrategy$Factory"}

To enable Round Robin load balancing strategy:

    loadBalancing:
      strategy:
        factory: {class: "com.hotels.styx.client.loadbalancing.strategies.RoundRobinStrategy$Factory"}

The *requestCount* attribute determines how long the adaptive strategy
remains in the Round Robin phase before switching over to the *Busy* strategy.
Its value is the number of requests proxied *per origin*.

