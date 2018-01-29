# Connection Pool Configuration

Styx uses TCP connection pooling to reduce latency. A TCP connection pool
is created for each origin server. Connection pool settings are configured at 
the backend service level, and they are applied individually to each member 
origin server.

Conceptually Styx connection pool has two components: 
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


Connection pool size is given by *maxConnectionsPerHost* setting. 
The pool initially has no established TCP connections. 
They are created lazily when needed.

The two other parameters, *connectTimeoutMillis* and *socketTimeoutMillis* describe
  * maximum allowed time for TCP connection establishment (*connectTimeoutMillis*)
  * maximum amount of inactivity over the TCP connection before it should be closed
    (*socketTimeoutMillis*).

In case there is a need for periodical termination of connections in the pool, *connectionExpirationSeconds* setting
will create a timer that will be assigned to each connection and initiate a procedure that will close the connection, when it expires.
This is useful when an origin host is specified as a DNS domain name, and you want to ensure that domain names are re-resolved periodically.
Specifing non-positive value turns off the functionality. Closing of the connection is performed lazily on incoming request.

When the connection pool gets full, any additional requests 
for the connections are queued. 
These requests are called *pending connections*. 
The pending connection queue size is given by *maxPendingConnectionsPerHost* attribute. 
The maximum time a connection can be "pending" is limited by the 
*pendingConnectionTimeoutMillis* attribute. When this time expires, a connection
request is removed from the queue, resulting in a *503 Service Unavailable* response (1).

# Metrics

Connection pool metrics are prefixed with `com.hotels.styx.$APPLICATION.$ORIGIN.connectionspool`.

 - `busy-connections` 
     - Number of currently borrowed connections.     
 - `pending-connections` 
     - Number of subscribers waiting in the pending connections queue.                                     
 - `available-connections` 
     - Number of primed TCP connections readily available for consumers to borrow.
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
       closures initiated by the remote peer. The number of connections terminated by
       remote origin can be calculated as `terminated-connections` minus `closed-connections`.
       
# Footnotes

Footnote (1) Note however a [retry mechanism](configure-health-checks.md) kicks in
in this case, and the request will be re-attempted as per configured retry policy.
