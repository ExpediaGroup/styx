# Health Checks

## Introduction

Styx automatically monitors the connectivity and health of configured
backend services. This feature is called "health checking", and
can be enabled or disabled by including a `healthCheck` attribute
in a backend service configuration block.

Health checking is configured for a specific backend service, using the `uri` attribute
to indicate the URI of the health check endpoint.
 Styx polls this endpoint, for every backend service origin, at configured intervals.

When an origin fails to respond to the poll from Styx for `unhealthyThreshold`
times, Styx will mark the origin as *INACTIVE* and remove it from the
load balancer rotation. Conversely, when an *INACTIVE* origin starts
responding to the polls, it must respond at least `healthyThreshold`
times before Styx moves it back into the *ACTIVE* state and puts it back into 
the load balancer rotation.

## Origin State Machine

An origin can be in one of the three possible states:

- *DISABLED* - This is an administrative state. A system administrator has 
removed an origin from load balancer rotation.

- *ACTIVE* - Origin is administratively enabled. It is responding to styx
health checks, and considered active. Styx will load balance traffic
to the origin.

- *INACTIVE* - Origin is administratively enabled but it is not
responding to the health checks and therefore considered inactive.
Styx will not load balance traffic to the origin.

A note about enabling a *DISABLED* origin: to prevent
Styx from sending traffic to a potentially broken origin, the origin
is initially enabled in an *INACTIVE* state. It gets activated only
after health checks have confirmed the origin is healthy.

However, if health checks for the backend service are disabled, the origin will be activated
straight away.


```
    DISABLED <----------- ACTIVE
       ^                    ^
       |                    |
       |                    |
       |                    v
       `--------------> INACTIVE
```


## Configuration

To enable health checking, add the `healthCheck` configuration block
into `BackendService`.

  ```
  healthCheck:
    uri: "/version.txt"
    intervalMillis: 10000
    healthyThreshold: 2
    unhealthyThreshold: 2
  ```

 - `uri`
   - An URI endpoint used for the health check poll.
 - `intervalMillis`
   - Time between two consecutive health check polls.
 - `healthyThreshold`
   - Number of consecutive successful health checks before
     an *INACTIVE* origin is activated (goes into *ACTIVE* state).
 - `unhealthyThreshold`
   - Number of consecutive unsuccessful health checks before
     an *ACTIVE* origin is deactivated (goes into *INACTIVE* state).


## Metrics

A meter of failed health check attempts per backend service:

    origins.healthcheck.failure.<BACKEND-ID>.count
    origins.healthcheck.failure.<BACKEND-ID>.m1_rate
    origins.healthcheck.failure.<BACKEND-ID>.m5_rate
    origins.healthcheck.failure.<BACKEND-ID>.m15_rate
    origins.healthcheck.failure.<BACKEND-ID>.mean_rate
