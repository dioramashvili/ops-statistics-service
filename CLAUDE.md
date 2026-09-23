# CLAUDE.md

Guidance for AI agents and developers working in this repository.

## What this is

A headless Spring Boot worker that consumes transaction events from RabbitMQ and
maintains per-segment aggregate counts in SQL Server. There is **no web layer**
(`spring.main.web-application-type: none`) — it is a message consumer, not an API.

- Java 21, Spring Boot 4.0.6, Maven (use the wrapper `./mvnw`)
- Persistence is **JDBC via `JdbcTemplate`** — there is no JPA/Hibernate and no entities
- Package root: `com.dioramashvili.opsstatistics`

## Build & test

```bash
./mvnw test                      # unit tests (JUnit 5 + Mockito), no infra needed
./mvnw -Dtest=SegmentResolverTest test
./mvnw clean package             # build the jar
./mvnw spring-boot:run           # run with the dev profile
```

Notes for running commands here:
- On Windows the shell is PowerShell; the Bash tool is available for POSIX scripts.
- The `./mvnw` output pipes reliably through the Bash tool; the `.cmd` wrapper
  invoked from PowerShell may run detached and swallow stdout — prefer Bash for builds.
- `OpsStatisticsApplicationTests.contextLoads` is a `@SpringBootTest` that needs a live
  DB/RabbitMQ; it is skipped by the `*Test` name filter used above.

## Message flow

`TransactionEventListener` (`@RabbitListener`, manual ack)
→ `MessageHandler.handle` (`@Transactional`, the whole pipeline)
→ `MessageParser` (Jackson `readTree`, manual field extraction)
→ `SegmentResolver` (segment per customer, cached)
→ `StatisticsRepository` (increment on `transaction.create`, decrement on `transaction.delete`).

Reliability model (know this before touching the listener or handler):
- **Idempotency:** `MessageHandler` records the message id in `dbo.PROCESSED_MESSAGES`
  (primary key). A duplicate re-delivery fails the insert and is skipped. A missing/blank
  message id is rejected. Because it is inside the transaction, a failed attempt rolls the
  mark back, so retries are safe and do not double-count.
- **Retry then DLQ:** the listener retries `handle()` up to `app.retry.max-attempts`
  (default 3) with exponential backoff, then `basicNack(requeue=false)` → DLX/DLQ.
- **Durable dead letters:** `DeadLetterListener` persists dead letters to `dbo.DEAD_LETTERS`
  (recovering the original routing key/reason/count from the `x-death` header) then acks;
  if persistence fails it requeues after `app.dlq.requeue-delay-ms`.

## Conventions & constraints

- **No JPA.** Repositories are `@Repository` classes wrapping `JdbcTemplate` with
  hand-written, parameterized SQL. Keep SQL parameterized (no string concatenation).
- **Let `DataAccessException` propagate** from repositories/resolver so a failed message is
  retried/dead-lettered. Do not swallow DB errors into a default value.
- **`SegmentResolver` caching** uses Caffeine directly (`expireAfterWrite` +
  `maximumSize`), not the Spring Cache abstraction. It caches `customerId → segment`;
  errors are never cached. TTL exists because there is no segment-change event — bounded
  staleness is accepted deliberately.
- **`decrement` floors at zero in the SQL** (`AND op_count > 0`); it does not rely on a DB
  CHECK constraint or on parsing exception messages.
- **Config is profile-split:** `application.yaml` (common + `app.*` tuning),
  `application-dev.yaml` (local placeholders, committed), `application-prod.yaml`
  (env-var driven, no secrets committed). Default profile is `dev`.
- **Segment labels** are constants in `model/Segment.java` — reuse them, don't inline strings.

## Tunable settings (`application.yaml`)

```
app.retry.max-attempts / initial-interval-ms / multiplier   # listener retry
app.segment-cache.ttl-minutes / max-size                    # Caffeine cache
app.dlq.requeue-delay-ms                                     # DLQ requeue throttle
```

## Testing approach

- Unit tests only (JUnit 5 + Mockito); collaborators are mocked. No Testcontainers.
- Repository tests mock `JdbcTemplate`, so they cover control flow, **not** the actual SQL.
  Verifying the upsert/decrement against real SQL Server is still open work.
- When mocking `JdbcTemplate` varargs, match each element (`anyString(), any(), any(), ...`);
  a single `(Object[]) any()` matcher does not reliably intercept the call under Mockito 5.

## Gotchas / known gaps

- The **validation** starter is on the classpath but unused (no `@Valid`/constraints).
- **Actuator** is on the classpath but, with no web server, its HTTP endpoints are not exposed.
- No migration tool (Flyway/Liquibase). Schema lives in `sql/create_tables.sql` and must be
  applied manually.
- `SegmentResolver` reads external tables `dbo.CLIENTS` / `dbo.CLIENT_ATTRIBUTES` that this
  project does not create; they must already exist.
- Schema/queue/table names in this repo are generic placeholders, not production identifiers.

## Git

- Default branch: `main`, which now holds the Spring Boot service (merged from
  `spring-boot-migration`). The original plain-Java implementation is preserved in
  history prior to the merge.
- Commit style is Conventional Commits (`fix:`, `feat:`, `refactor:`, `docs:`).
