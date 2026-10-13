# Blog API

## What It Does

This project is a Spring Boot REST API for a simple multi-user blog.

It allows clients to:

- register and authenticate users
- establish server-side authenticated sessions
- obtain CSRF tokens for state-changing requests
- create posts as authenticated users
- read posts publicly
- retrieve paginated post lists
- update posts as the post owner
- allow administrators to update any post

The project focuses on Spring Boot backend fundamentals including Spring MVC, Spring Security, Spring Data JPA, PostgreSQL, Flyway migrations, transactions, authorization, optimistic locking, and integration testing with Testcontainers.

## Technology

- Java 21
- Maven
- Spring Boot 4.1.1
- Spring MVC
- Spring Security
- Spring Data JPA
- Hibernate
- PostgreSQL
- Flyway
- Jakarta Bean Validation
- JUnit 5
- Mockito
- Testcontainers

## API

### GET /auth/csrf

Returns the CSRF token required for state-changing requests.

Authentication is not required.

Example response:

```json
{
  "token": "<csrf-token>",
  "headerName": "X-CSRF-TOKEN"
}
```

The client must preserve the session associated with this request and send the returned token using the supplied header name.

Typical flow:

```text
GET /auth/csrf
↓
retain JSESSIONID
↓
read returned CSRF token
↓
POST /auth/login
Cookie: JSESSIONID=...
X-CSRF-TOKEN: ...
```

### POST /auth/register

Registers a new user.

The request does not require authentication, but it is CSRF-protected.

Example request:

```json
{
  "email": "alice@example.com",
  "password": "secret123"
}
```

Success:

```text
201 Created
```

Example response:

```json
{
  "id": 1,
  "email": "alice@example.com",
  "role": "USER"
}
```

Registration:

- trims the email
- converts it to lowercase
- encodes the password with Spring Security's configured `PasswordEncoder`
- assigns the `USER` role

Possible errors:

- `400 Bad Request` — blank request fields
- `403 Forbidden` — missing or invalid CSRF token
- `409 Conflict` — normalized email already exists

### POST /auth/login

Authenticates a user and establishes a server-side session.

Authentication is not required before login, but the request is CSRF-protected.

Example request:

```json
{
  "email": "alice@example.com",
  "password": "secret123"
}
```

Success:

```text
204 No Content
```

Spring Security authenticates the credentials through a `DaoAuthenticationProvider`.

After successful authentication:

```text
credentials verified
↓
session authentication strategy runs
↓
session ID is changed
↓
SecurityContext is created
↓
SecurityContext is explicitly stored
↓
later requests use the authenticated session
```

Invalid credentials return:

```text
401 Unauthorized
```

with:

```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "Invalid email or password"
}
```

### GET /auth/me

Returns the currently authenticated user.

Authentication is required.

Example response:

```json
{
  "id": 1,
  "email": "alice@example.com",
  "role": "USER"
}
```

Unauthenticated requests return:

```text
401 Unauthorized
```

### POST /posts

Creates a post owned by the currently authenticated user.

Authentication and a valid CSRF token are required.

Example request:

```json
{
  "title": "Learning Spring",
  "content": "My first post"
}
```

Success:

```text
201 Created
```

The response also includes:

```text
Location: /posts/{id}
```

Example body:

```json
{
  "id": 1,
  "title": "Learning Spring",
  "content": "My first post"
}
```

The authenticated user's ID becomes the post's `author_id`.

### GET /posts/{id}

Returns a post by ID.

This endpoint is public.

Example response:

```json
{
  "id": 1,
  "title": "Learning Spring",
  "content": "My first post"
}
```

A missing post returns:

```text
404 Not Found
```

### GET /posts

Returns a paginated list of posts.

This endpoint is public.

Parameters:

```text
page
default = 0
minimum = 0
```

```text
size
default = 20
minimum = 1
maximum = 100
```

Example:

```text
GET /posts?page=0&size=20
```

Example response:

```json
{
  "items": [
    {
      "id": 5,
      "title": "Newest post",
      "content": "..."
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 5,
  "totalPages": 1
}
```

Posts are ordered by ID descending, so newer persisted posts appear first.

### PUT /posts/{id}

Updates an existing post.

Authentication and a valid CSRF token are required.

Example request:

```json
{
  "title": "Updated title",
  "content": "Updated content"
}
```

A normal `USER` may update only their own post.

An `ADMIN` may update any post.

Possible responses include:

- `200 OK` — update succeeded
- `400 Bad Request` — invalid request fields
- `401 Unauthorized` — no authenticated user
- `403 Forbidden` — invalid CSRF token or authenticated user does not own the post
- `404 Not Found` — post does not exist

## Architecture

The code is organized around API, application/domain logic, persistence, security, and configuration.

```text
HTTP Client
    |
    v
Spring Security Filter Chain
    |
    +--> authentication
    +--> session security
    +--> CSRF validation
    |
    v
Controllers
    |
    +--> AuthController
    |
    +--> PostController
    |
    v
Application Services
    |
    +--> RegistrationService
    |
    +--> PostService
    |
    v
Domain Repository Interfaces
    |
    v
JPA Repository Adapters
    |
    v
Spring Data JPA / Hibernate
    |
    v
PostgreSQL
```

### Component Responsibilities

- `SecurityConfig` defines authentication, authorization, CSRF, and session-security rules.
- `AuthController` exposes registration, login, current-user, and CSRF bootstrap endpoints.
- `RegistrationService` normalizes registration data and encodes passwords.
- `DatabaseUserDetailsService` loads persisted users for Spring Security authentication.
- `AuthenticatedUser` represents the authenticated principal and its role.
- `PostController` maps post HTTP operations to application behavior.
- `PostService` manages post creation, retrieval, updates, ownership authorization, and transactional boundaries.
- `PostRepository` defines the persistence operations required by the application layer.
- `JpaPostRepositoryAdapter` maps between the domain model and JPA entities.
- `PostEntity` represents persisted post state and uses optimistic locking.
- `UserEntity` represents persisted authentication and role data.
- Flyway owns database schema evolution.

## Request Flows

### Registration

```text
GET /auth/csrf
↓
obtain CSRF token + session
↓
POST /auth/register
↓
validate request
↓
normalize email
↓
encode password
↓
create USER
↓
persist through JPA
↓
201 Created
```

### Login

```text
GET /auth/csrf
↓
retain session + CSRF token
↓
POST /auth/login
↓
AuthenticationManager
↓
DaoAuthenticationProvider
↓
DatabaseUserDetailsService
↓
password verification
↓
ChangeSessionIdAuthenticationStrategy
↓
create authenticated SecurityContext
↓
save SecurityContext to session
↓
204 No Content
```

Changing the session ID after successful authentication protects against session-fixation attacks while preserving the authenticated session.

### Create Post

```text
authenticated session
+
valid CSRF token
↓
POST /posts
↓
PostController
↓
authenticated principal ID
↓
PostService.createPost(...)
↓
@Transactional
↓
PostRepository
↓
JPA / PostgreSQL
↓
201 Created
```

The post author is derived from the authenticated principal rather than accepted from arbitrary client input.

### Update Post

```text
authenticated session
+
valid CSRF token
↓
PUT /posts/{id}
↓
load existing post
↓
compare authorId with callerId
↓
owner?
 ├── yes → update allowed
 └── no
       ↓
     ADMIN?
       ├── yes → update allowed
       └── no  → 403
```

## Persistence and Concurrency

### Flyway Migrations

Database schema changes are managed by Flyway.

Current migrations create and evolve:

```text
posts
comments
post optimistic-lock version
users
post ownership
```

Spring JPA is configured with:

```properties
spring.jpa.hibernate.ddl-auto=validate
```

Hibernate therefore validates the mapped schema rather than creating or silently changing it.

### Development-Only Destructive V5 Migration

`V5__add_post_author.sql` introduced post ownership.

At that stage of development, existing posts had no author information that could be safely backfilled.

The migration therefore contains:

```sql
DELETE FROM comments;
DELETE FROM posts;
```

before adding the required `author_id` column.

This is intentionally documented as a **development-stage destructive migration**.

It is not an appropriate pattern for migrating valuable production data.

A production migration would require a deliberate compatibility strategy such as:

```text
add nullable author_id
↓
backfill valid ownership data
↓
verify all rows
↓
add foreign key
↓
enforce NOT NULL
```

The existing V5 migration is not modified because already-applied Flyway migrations should be treated as immutable migration history.

### Transaction Boundaries

Post creation and updates use Spring's `@Transactional`.

The test suite verifies important transaction behavior, including:

- successful commits
- rollback after runtime exceptions
- default behavior for checked exceptions
- explicit `rollbackFor`
- PostgreSQL transaction-isolation behavior

The isolation tests demonstrate differences between `READ COMMITTED` and `REPEATABLE READ` using concurrent transactions.

### Optimistic Locking

`PostEntity` contains:

```java
@Version
private Long version;
```

and Flyway migration V3 adds the corresponding database column.

The version is used by JPA/Hibernate to detect conflicting concurrent writes.

The integration tests verify a stale writer cannot silently overwrite a newer committed update.

Conceptually:

```text
Transaction A reads version 0
Transaction B reads version 0

A updates
↓
database version becomes 1

B attempts update using version 0
↓
optimistic-lock failure
```

The current API does not translate optimistic-lock failures into a dedicated HTTP `409 Conflict` response.

## Failure Behavior

The application uses structured responses for application-level errors.

### Validation Failure

Example:

```json
{
  "code": "VALIDATION_FAILED",
  "errors": {
    "title": "must not be blank"
  }
}
```

HTTP status:

```text
400 Bad Request
```

### Missing Post

Example:

```json
{
  "code": "POST_NOT_FOUND",
  "message": "Post not found: 999"
}
```

HTTP status:

```text
404 Not Found
```

### Duplicate Email

Example:

```json
{
  "code": "DUPLICATE_EMAIL",
  "message": "Email is already registered"
}
```

HTTP status:

```text
409 Conflict
```

### Invalid Credentials

Example:

```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "Invalid email or password"
}
```

HTTP status:

```text
401 Unauthorized
```

### Post Authorization Failure

Example:

```json
{
  "code": "POST_ACCESS_DENIED",
  "message": "Post access denied"
}
```

HTTP status:

```text
403 Forbidden
```

### Spring Security Failures

Protected endpoints require an authenticated session.

A request with valid CSRF state but no authenticated user is rejected with:

```text
401 Unauthorized
```

A state-changing request without a valid CSRF token is rejected by Spring Security before reaching the controller:

```text
403 Forbidden
```

## Configuration

Runtime database configuration is provided through environment variables.

### Database

Required variables:

| Variable | Purpose |
|---|---|
| `DB_URL` | PostgreSQL JDBC URL |
| `DB_USERNAME` | PostgreSQL username |
| `DB_PASSWORD` | PostgreSQL password |

Example:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/blog_api"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "<your-password>"
```

### JPA

The application configures:

```properties
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
```

`ddl-auto=validate` keeps Flyway responsible for schema management.

`open-in-view=false` prevents persistence-context access from being implicitly extended through the HTTP rendering phase.

### HTTP Server

No custom application port is configured.

Spring Boot therefore uses its standard server configuration unless overridden externally.

## Running Locally

### Requirements

- Java 21
- Maven
- PostgreSQL

Docker is required for the complete automated test suite because integration tests use Testcontainers.

### PostgreSQL

Create a PostgreSQL database, for example:

```text
blog_api
```

Set:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/blog_api"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "<your-password>"
```

Flyway automatically applies the migrations in:

```text
src/main/resources/db/migration
```

when the application starts against a new database.

### Start the Application

Run:

```powershell
mvn spring-boot:run
```

By default, the application is available at:

```text
http://localhost:8080
```

### Example Authentication Flow

First obtain a CSRF token:

```text
GET /auth/csrf
```

Preserve the returned session cookie.

Then send the returned token using the header specified by `headerName`:

```text
POST /auth/login

Cookie: JSESSIONID=<session>
X-CSRF-TOKEN: <token>
Content-Type: application/json
```

with:

```json
{
  "email": "alice@example.com",
  "password": "secret123"
}
```

After successful login, continue sending the authenticated session cookie for protected requests.

State-changing requests must also include valid CSRF state.

## Testing

Run the complete suite with:

```powershell
mvn test
```

Docker must be running.

Spring Boot's Testcontainers integration starts disposable PostgreSQL instances for database-backed tests through `@ServiceConnection`.

The automated tests cover areas including:

- Spring application startup
- security rules
- CSRF enforcement and real CSRF token acquisition
- registration
- normalized email persistence
- encoded passwords
- duplicate registration
- login success and failure
- authenticated sessions
- `/auth/me`
- post request validation
- post creation
- public post retrieval
- pagination
- post ownership
- administrator override
- JPA persistence behavior
- post/comment relationships
- transaction commit and rollback
- PostgreSQL transaction isolation
- optimistic locking and stale concurrent writers

### Integration Tests

Integration tests execute against real PostgreSQL through Testcontainers.

This is important because several behaviors depend on the real persistence engine and transaction manager rather than mocks, including:

```text
Flyway migration execution
JPA mappings
database constraints
transaction commits and rollbacks
isolation semantics
optimistic locking
ownership persistence
```

MockMvc is used for HTTP/security integration scenarios while the persistence layer remains backed by real PostgreSQL.

## Design Decisions

### Session Authentication Instead of JWTs

The application uses Spring Security's server-side session model.

After login, the authenticated `SecurityContext` is stored in the session and reused on later requests.

This keeps authentication revocation and server-side session state straightforward for the current project scope.

### Explicit CSRF Bootstrap Endpoint

State-changing endpoints remain protected by Spring Security CSRF protection.

Instead of disabling CSRF for a JSON API, the application exposes:

```text
GET /auth/csrf
```

so real clients can obtain the token required by Spring Security.

This preserves CSRF protection while still giving API clients a usable authentication flow.

### Session Fixation Protection

Successful login runs:

```text
ChangeSessionIdAuthenticationStrategy
```

so the existing session ID is changed when authentication succeeds.

### Post Ownership Comes From Authentication

Clients do not provide arbitrary `authorId` values when creating posts.

The server obtains the author ID from:

```text
@AuthenticationPrincipal
```

and persists that authenticated identity with the post.

This prevents a client from claiming another user's identity simply by submitting a different author ID.

### Owner-or-Admin Authorization

Updates are authorized in the application service.

A request is allowed when:

```text
callerId == post.authorId
```

or:

```text
callerRole == ADMIN
```

Otherwise the service rejects the operation.

### Flyway Owns Schema Evolution

Hibernate runs with schema validation rather than schema generation.

This makes database evolution explicit, reviewable, and reproducible through versioned Flyway migrations.

### Optimistic Locking

Post updates use JPA optimistic locking through `@Version`.

This prevents a stale concurrent writer from silently overwriting a newer committed change.

### Open Session in View Is Disabled

The application sets:

```properties
spring.jpa.open-in-view=false
```

so persistence access is expected to occur inside deliberate application/service boundaries instead of being silently available throughout the HTTP response lifecycle.

## Known Limitations

This project intentionally stops at a portfolio-sized Spring Boot backend rather than a complete blogging platform.

Known limitations include:

- No delete-post endpoint.
- Comments are represented in the database/JPA model but do not yet have a public REST API.
- No user profile management.
- No password-reset flow.
- No email verification.
- No multi-factor authentication.
- No role-management API; normal registration creates `USER` accounts.
- No login rate limiting or account lockout.
- No explicit production session-store configuration for horizontally scaled application instances.
- Session cookie production settings are not explicitly hardened for a specific deployment environment.
- Optimistic-lock conflicts are not translated into a dedicated HTTP `409 Conflict` response.
- Flyway V5 destructively removed existing development posts/comments when ownership was introduced.
- No zero-downtime migration strategy is demonstrated.
- No structured production logging, metrics, or distributed tracing.
- No production secret-management integration.
- No automated deployment pipeline.

These limitations are documented boundaries of the project rather than implemented capabilities.