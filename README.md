# Blog API

A production-oriented REST API built with Java and Spring Boot.

The project started as a simple Blog API and was progressively extended to practice backend engineering concerns beyond basic CRUD:

- layered Spring architecture
- PostgreSQL persistence
- database migrations
- transactions and optimistic locking
- session-based authentication
- resource ownership authorization
- Redis caching
- failure degradation
- configuration validation
- health probes
- structured production logging
- unit, MVC, and integration testing with real infrastructure

The goal of the project is not to model every feature of a real blogging platform. It is to demonstrate the design, testing, security, persistence, and operational concerns of a backend service.

---

## Tech Stack

- Java 21
- Spring Boot 4.1.1
- Spring MVC
- Spring Security
- Spring Data JPA / Hibernate
- PostgreSQL
- Flyway
- Redis
- Spring Cache
- Spring Boot Actuator
- Maven
- JUnit 5
- Mockito
- Testcontainers

---

## Architecture

The application follows a layered structure:

```text
HTTP Client
    |
    v
Spring Security Filter Chain
    |
    v
Controller
    |
    v
Application Service
    |
    +------> Cache
    |
    v
Repository
    |
    v
PostgreSQL
```

Main responsibilities:

### Controller

Owns the HTTP boundary.

Responsibilities include:

- reading path/query/body data
- request validation
- obtaining the authenticated principal
- invoking application services
- translating results into HTTP responses

Controllers do not own persistence or authorization business rules.

### Application Service

Owns use-case behavior.

Examples:

- creating a post
- retrieving a post
- updating a post
- checking ownership
- coordinating transactions
- interacting with the caching boundary

### Repository

Provides persistence operations without exposing HTTP concerns to the application layer.

PostgreSQL remains the authoritative source of data.

### DTO Boundary

API request/response models are kept separate from persistence entities so database changes do not automatically become public API changes.

---

## Main Request Flow

Example:

```text
GET /posts/42
```

```text
Client
  |
  v
Spring Security
  |
  v
PostController
  |
  v
PostService
  |
  v
PostCacheService
  |
  +---- cache HIT ----> Redis ----> PostView
  |
  +---- cache MISS
             |
             v
        PostRepository
             |
             v
         PostgreSQL
             |
             v
           Redis
             |
             v
          PostView
```

PostgreSQL is the source of truth. Redis is an optimization.

---

## API

### Register

```http
POST /auth/register
```

Creates a new user.

Important behavior:

- email is normalized
- passwords are encoded before persistence
- duplicate email is rejected
- clients cannot assign themselves privileged roles
- CSRF protection applies

---

### Login

```http
POST /auth/login
```

Authenticates credentials and creates session-backed authentication.

The authenticated identity comes from server-side user data, not from values supplied by the client.

---

### Current User

```http
GET /auth/me
```

Returns information about the currently authenticated user.

Requires authentication.

---

### Create Post

```http
POST /posts
```

Requires authentication.

The post author is derived from the authenticated principal.

Clients cannot choose another user as the authoritative owner.

---

### Get Post

```http
GET /posts/{id}
```

Public read endpoint.

Successful reads are cached in Redis.

---

### List Posts

```http
GET /posts?page={page}&size={size}
```

Returns paginated posts.

Pagination prevents an unbounded API response.

This endpoint is intentionally not part of the current individual-post Redis caching strategy.

---

### Update Post

```http
PUT /posts/{id}
```

Requires authentication.

Authorization policy:

```text
owner OR ADMIN
```

A normal authenticated user cannot modify another user's post.

Admin override allows the modification but does not transfer ownership.

---

## Authentication

The project uses session-based authentication.

Simplified flow:

```text
POST /auth/login
        |
        v
AuthenticationManager
        |
        v
AuthenticationProvider
        |
        v
UserDetailsService
        |
        v
Password verification
        |
        v
authenticated Authentication
        |
        v
SecurityContext
        |
        v
HTTP session
```

Subsequent requests load authentication from the session.

---

## Authentication vs Authorization

These are treated as separate decisions.

Authentication asks:

```text
Who is making this request?
```

Authorization asks:

```text
Is that user allowed to perform this operation?
```

For example:

```text
authenticated Bob
        |
        v
PUT /posts/42
        |
        v
post 42 belongs to Alice
        |
        v
403 Forbidden
```

Being authenticated does not automatically grant access to another user's resources.

---

## Ownership / BOLA Protection

Resource ownership is checked using authoritative server-side data.

The application does not trust a client-provided owner ID.

Conceptually:

```text
authenticated user ID
        +
post owner loaded from PostgreSQL
        |
        v
compare
        |
        +---- same owner ----> allow
        |
        +---- ADMIN ---------> allow
        |
        +---- otherwise -----> 403
```

This prevents a client from modifying another user's resource simply by changing an ID in the request.

---

## 401 vs 403

The API deliberately distinguishes:

```text
401 Unauthorized
```

The request does not have valid authentication.

```text
403 Forbidden
```

The user is authenticated, but the requested operation is not permitted.

---

## CSRF

Because authentication is stored in a browser session/cookie, state-changing requests are protected against CSRF.

Authentication and CSRF solve different problems:

```text
Authentication
→ Who is the user?

CSRF protection
→ Was this authenticated browser intentionally making this state-changing request?
```

---

## Persistence

PostgreSQL is the system of record.

Persistence uses:

- JPA
- Hibernate
- PostgreSQL
- Flyway migrations

The application does not rely on Hibernate automatically creating the production schema.

Schema evolution is represented explicitly through Flyway migrations.

Current migrations cover areas including:

- posts
- comments
- optimistic-lock versioning
- users
- post ownership

---

## Transactions

Application operations that must behave as one unit are executed within transaction boundaries.

Conceptually:

```text
begin transaction
    |
    +-- database operations
    |
success
    |
commit
```

or:

```text
begin transaction
    |
    +-- database operations
    |
exception
    |
rollback
```

Transaction placement is intentional rather than adding `@Transactional` simply until a test passes.

---

## Optimistic Locking

Posts contain a version used for optimistic concurrency control.

This protects against a stale writer silently overwriting a newer update.

Conceptually:

```text
Client A reads version 1
Client B reads version 1

Client A updates
→ database becomes version 2

Client B attempts update using stale version 1
→ optimistic locking detects conflict
```

This project uses optimistic locking as protection against lost updates rather than pessimistically locking every read.

---

## Redis Caching

Redis is used only for a justified read path:

```text
GET /posts/{id}
```

Cache:

```text
name = posts
key  = post ID
```

The current default TTL is:

```text
5 minutes
```

The TTL is externally configurable.

---

## Cache Policy

### Successful read

```text
GET /posts/42
        |
        v
Redis lookup
   |          |
 HIT         MISS
   |          |
return       PostgreSQL
              |
              v
           cache result
              |
              v
            return
```

### Not found

A `404` result is not cached.

This avoids turning a temporary absence into a longer stale negative result.

### Create

Creating a new post does not require invalidating an individual post cache entry because no previous individual entry should exist for the newly generated ID.

### Update

A successful update evicts:

```text
posts::<id>
```

The next read reloads the authoritative value from PostgreSQL.

---

## Transaction-Aware Cache Eviction

Cache eviction is aligned with successful database transaction completion.

Desired behavior:

```text
DB commit succeeds
→ evict cache entry
```

```text
DB transaction rolls back
→ do not evict because authoritative data did not change
```

This reduces incorrect cache invalidation around failed updates.

It does not make PostgreSQL and Redis one distributed atomic transaction. Small race windows still exist, which is a known limitation.

---

## Redis Failure Policy

Redis is treated as an optional dependency.

If Redis becomes unavailable:

```text
cache GET fails
→ suppress cache infrastructure error
→ fall back to PostgreSQL
```

For cache write or eviction failures, PostgreSQL remains authoritative and the request is not converted into a database failure merely because the optimization layer failed.

This is deliberate graceful degradation.

The tradeoff is that an eviction failure can temporarily leave stale cached data until expiration.

---

## Externalized Configuration

Cache behavior is configured through typed Spring Boot configuration.

Example:

```properties
app.cache.ttl=5m
```

Configuration is validated at startup.

Invalid values such as a non-positive TTL cause configuration binding/startup validation to fail rather than silently running with an invalid cache policy.

---

## Health Checks

Spring Boot Actuator exposes operational health information.

The project distinguishes:

```text
liveness
```

from:

```text
readiness
```

### Liveness

Question:

> Would restarting this application instance help?

External PostgreSQL or Redis outages do not mean the JVM itself is dead.

### Readiness

Question:

> Can this instance currently serve the required workload?

The project's dependency policy is:

| State | Overall Health | Liveness | Readiness |
|---|---|---|---|
| Healthy | UP | UP | UP |
| PostgreSQL unavailable | DOWN | UP | DOWN |
| Redis unavailable | DOWN | UP | UP |

PostgreSQL is a required dependency.

Redis is optional because requests can fall back to PostgreSQL.

This means:

```text
overall health = DOWN
readiness      = UP
```

is valid when only an optional dependency is unavailable.

---

## Actuator Exposure

Only required operational health endpoints are exposed.

Operational endpoints are treated as a trust boundary because excessive Actuator exposure may reveal:

- dependency information
- environment details
- topology
- configuration
- other operational information

The application does not intentionally expose the entire `/actuator/**` surface publicly.

---

## Structured Logging

Production uses structured JSON logging.

Production configuration:

```properties
logging.structured.format.console=logstash
```

Cache failures emit fields such as:

```json
{
  "event": "cache_operation_failed",
  "operation": "GET",
  "cache": "posts",
  "key": 1,
  "exceptionClass": "org.springframework.data.redis.RedisConnectionFailureException"
}
```

Structured fields make logs easier to search and aggregate than parsing free-form text.

---

## Logging Security

Logs are treated as another trust boundary.

The application avoids intentionally logging raw:

- passwords
- session IDs
- Authorization headers
- authentication tokens
- request bodies
- cached values

Cache keys are not assumed to be safe automatically.

Known-safe numeric post IDs may be logged, while unknown future cache-key formats should be redacted by default.

---

## Testing Strategy

The project uses several test boundaries rather than proving everything through one type of test.

### Unit Tests

Used for isolated application behavior.

Examples:

- service rules
- exception behavior
- cache error-handler behavior

Mocks are useful here because the test is about application logic rather than PostgreSQL or Redis.

### MVC / Security Tests

Used to verify the HTTP and filter-chain boundary.

Examples:

- request validation
- HTTP status codes
- authentication requirements
- authorization behavior
- Actuator security rules

### PostgreSQL Integration Tests

Use Testcontainers rather than replacing PostgreSQL with an in-memory database.

These tests can verify behavior that mocks or fake databases cannot prove:

- mappings
- constraints
- Flyway migrations
- transaction behavior
- optimistic locking
- persistence relationships

### Redis Integration Tests

Redis Testcontainers verifies real cache behavior including:

- cache population
- cache reads
- invalidation
- Redis outage behavior

### Operational Failure Tests

The suite also deliberately stops dependencies after application startup.

Examples:

```text
PostgreSQL down
→ readiness becomes DOWN
→ liveness stays UP
```

```text
Redis down
→ overall health becomes DOWN
→ readiness stays UP
→ liveness stays UP
```

These tests verify runtime failure policy rather than only successful startup.

The latest verified full local run:

```text
Tests run: 80
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

---

## Running the Tests

Requirements:

- Java 21
- Maven
- Docker

Testcontainers starts the required PostgreSQL and Redis infrastructure.

Run:

```bash
mvn clean test
```

---

## Production Logging Profile

Run tests or the application with the production profile when structured JSON console logs are required.

Example:

```bash
mvn "-Dspring.profiles.active=prod" test
```

On PowerShell, quoting Maven `-D` arguments avoids argument-parsing issues.

---

## Database Migrations

Flyway owns schema evolution.

Migration scripts are applied in version order when the application starts against the configured database.

The project currently contains five migrations.

Do not manually modify an already-applied migration in a real environment. Add a new migration for subsequent schema changes.

---

## Important Design Decisions

### PostgreSQL is authoritative

Redis accelerates reads but does not become the source of truth.

### Cache only a simple read path

The individual post endpoint has a straightforward cache key and invalidation rule.

Paginated query caching was intentionally avoided because:

- there are many possible page keys
- one write may invalidate many cached pages
- stale-data behavior becomes more difficult to reason about

### Session authentication instead of JWT

The project uses server-side session authentication because it provides a clear model for:

- server-controlled authentication state
- logout
- Spring Security integration
- CSRF learning

JWT is not automatically a better choice for every API.

### Real PostgreSQL in integration tests

H2 or mocks cannot prove PostgreSQL-specific constraints and transaction behavior.

Testcontainers provides stronger production-like evidence.

### Redis failure is fail-open for reads

Because PostgreSQL is authoritative and Redis is only an optimization, cache failure should not normally make a post unreadable.

### Explicit readiness policy

Not every unhealthy dependency should remove an instance from traffic.

Required and optional dependencies are classified deliberately.

---

## Known Limitations

This is a learning and portfolio backend, not a complete production blogging platform.

Known limitations include:

- no distributed deployment or high-availability architecture
- no Kubernetes/deployment automation
- no message broker/event-driven processing
- no distributed tracing
- no advanced metrics/alerting platform
- no OAuth/OIDC provider integration
- no full-text search
- no media/file upload system
- no comprehensive rate limiting
- Redis and PostgreSQL updates are not one distributed transaction
- cache invalidation can still have small race windows
- observability is intentionally limited to the scope required by this project
- performance has not been validated under production-scale load
- security has not undergone an external penetration test

These are deliberate scope boundaries rather than claims that the application is production-ready.

---

## What I Would Improve Next

If the service needed to evolve beyond the current learning/portfolio scope, I would prioritize based on evidence rather than adding infrastructure by default.

Possible next steps include:

1. establish load/performance measurements before further optimization
2. improve metrics and alerting based on real operational requirements
3. add request correlation/tracing if multi-service diagnosis requires it
4. evaluate deployment/container orchestration only when the deployment model requires it
5. review query performance using real production-like workloads
6. strengthen security review for an internet-facing deployment

These are future directions, not current project requirements.

---

## Engineering Lessons Demonstrated

This project reinforced several backend principles:

```text
The database remains authoritative.

Authentication and authorization are different decisions.

Client input is untrusted.

Transactions need explicit boundaries.

Caching requires an invalidation and failure policy.

Mocks cannot prove infrastructure behavior.

Operational health is a policy decision.

Logs are also a security boundary.

Production-oriented engineering includes failure behavior,
not only the successful request path.
```

---

## Project Status

The implementation scope is feature-complete for its current objective.

Remaining work is primarily:

- project review
- interview defense
- documentation refinement when implementation changes
- retained mastery verification

No additional infrastructure or architectural complexity is planned unless a concrete requirement justifies it.