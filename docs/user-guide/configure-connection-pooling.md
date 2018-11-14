# Connection Pool Configuration

Styx uses TCP connection pooling to reduce latency. A pool
is created for each origin server. Connection pool settings are configured at 
the backend service level, and they are applied individually to each member 
origin server.

Conceptually, a Styx connection pool has two components: 
  * A pool of TCP connections
  * A queue of waiting subscribers. These are called *pending connections*.

An example configuration block looks like this:

    connectionPool:      
      maxConnectionsPerHost: 45
      connectTimeoutMillis: 1000
      socketTimeoutMillis: 120000
      
      maxPendingConnectionsPerHost: 15
      pendingConnectionTimeoutMillis: 8000
      connectionExpirationSeconds: 1000 # default value 0


## General settings.
* *maxConnectionsPerHost* : size of the connection pool for each origin server. 
It is important to notice that, initially,  the pool has no established TCP connections. 
They are created lazily when needed.

* *connectTimeoutMillis*: maximum allowed time for TCP connection establishment.

* *socketTimeoutMillis*: maximum length of time a TCP connection can remain idle before being closed. 
*Warning:* this property is reserved for future usage and not currently honoured by Styx.

* *connectionExpirationSeconds*: allows connections to be terminated after the configured 
number of seconds has elapsed since the connection was created. This is useful when an origin 
host is specified as a DNS domain name, and you want to ensure that domain names are re-resolved 
periodically. If the value of the setting is non-positive, connections will not expire. 
Connection age is checked on each incoming request, so connections may live longer than their 
expiration time if they do not serve any requests.

## Connection pending settings.

Sometimes the pool doesn't have a connection available immediately, and a 
subscriber must wait for one. The waiting subscribers are queued in a fixed-size waiting 
queue. The waiting subscribers are also called *pending connections*  

A subscriber can be waiting for two reasons:

 1. The pool doesn't have a readily available TCP connection. A new connection must be
    established, and the subscriber is placed in the waiting queue until a TCP connection
    is established.

 2. The pool is full. In this case the subscribers are queued until a connection 
    becomes available. 

Returned or newly established connections are given to the longest waiting subscriber first.

* *maxPendingConnectionsPerHost*: maximum number of waiting subscribers for an origin.

* *pendingConnectionTimeoutMillis*: maximum time a subscriber can be "pending". 
When this timeout expires, a connection request is removed from the queue,
 resulting in a *503 Service Unavailable* response (1).

# Metrics

Connection pool metrics are prefixed with `com.hotels.styx.$BACKEND_SERVICE.$ORIGIN.connectionspool`.

 - `busy-connections` 
     - Number of currently borrowed connections.     
 - `pending-connections` 
     - Number of subscribers waiting in the pending connections queue.                                     
 - `available-connections` 
     - Number of TCP connections readily available for consumers to borrow.
 - `connection-attempts`
     - Number of connection establishment attempts that have been initiated from the
       connection pool. This metric is incremented every time a new TCP connection 
       has to be established.
 - `connection-failures`      
     - Number of failed TCP connection establishment attempts. Number of successful
      connection establishment attempts is `connection-attempts` minus `failed-connection-attempts`.        
 - `connections-closed`
     - Number of connections closed by Styx, for whatever reason.
 - `connections-terminated`
     - Number of terminated connections, for whatever reason, including the connection
       closures initiated by the remote peer. The number of connections terminated by a 
       remote origin can be calculated as `terminated-connections` minus `closed-connections`.
       
# Footnotes

Footnote (1) Note however a [retry mechanism](configure-health-checks.md) kicks in
in this case, and the request will be re-attempted as per the configured retry policy.
