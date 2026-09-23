# ops-statistics-service

> 🇬🇧 English | [🇬🇪 Georgian](#-georgian)

A Spring Boot background service that listens to RabbitMQ transaction events and aggregates statistics into a SQL Server database by customer segment.

## Description

The service consumes messages from the `transactions.exchange` RabbitMQ exchange and counts transactions by the following criteria:
- Debit account customer segment
- Credit account customer segment
- Channel (ChannelID)
- Document date

## Customer Segments

Segments are resolved in the following priority order (stops at first match):

1. **Company** — client is a legal entity (`IS_JURIDICAL = 1`)
2. **Unique** — client has `UNIQUE_BANKER` attribute
3. **Premium** — client has `PREMIUM_BANKER` attribute
4. **Mass** — all other cases
5. **N/A** — account has no associated client

> If both debit and credit accounts have no client, the transaction is ignored.

Resolved segments are cached (Caffeine) with a time-to-live so a segment change is picked up within the TTL without needing a change event; the cache is size-bounded so it cannot grow without limit.

## Message Processing & Reliability

Each message flows through a single transactional pipeline (`MessageHandler`):

1. **Idempotency** — the message id is recorded in `PROCESSED_MESSAGES`. A re-delivered message hits the primary key, is recognised as a duplicate, and is skipped. Messages without a message id are rejected (they cannot be deduplicated).
2. **Parse & resolve** — the JSON body is parsed and both customer segments are resolved.
3. **Apply** — `op_count` is incremented (`create`) or decremented (`delete`).

Delivery guarantees:
- **Manual acknowledgement.** A message is acked only after it is fully processed and committed.
- **Bounded retry with backoff.** Transient failures (e.g. a brief DB outage) are retried up to `app.retry.max-attempts` times with exponential backoff before the message is dead-lettered — so a blip does not immediately drop a message.
- **Durable dead letters.** Messages that exhaust their retries are routed to the dead-letter queue and persisted to `DEAD_LETTERS` for inspection / replay. If persisting a dead letter fails (e.g. DB down) it is requeued after a short delay rather than lost.

## Prerequisites

- Java 21
- Maven (or use the included Maven Wrapper: `./mvnw`)
- Access to a RabbitMQ server
- Access to a SQL Server instance with Windows Authentication (`integratedSecurity=true`)
- `mssql-jdbc_auth` DLL on the `java.library.path` for Windows Authentication support

## Configuration

Configuration is split by Spring profile:

| File | Purpose |
|------|---------|
| `application.yaml` | Common settings and `app.*` tuning; selects the active profile |
| `application-dev.yaml` | Concrete dev connection values — **runs out of the box** |
| `application-prod.yaml` | Connection values supplied from environment variables — **nothing committed** |

The active profile defaults to `dev`. Select another with `--spring.profiles.active=prod` or `SPRING_PROFILES_ACTIVE=prod`.

**Production environment variables:**

```
DB_HOST, DB_NAME
RABBITMQ_HOST, RABBITMQ_USERNAME, RABBITMQ_PASSWORD, RABBITMQ_VIRTUAL_HOST
RABBITMQ_PORT   (optional, default 5672)
```

> **TLS:** both profiles connect with `encrypt=true`. Dev trusts the self-signed server certificate (`trustServerCertificate=true`); production validates the certificate chain (`trustServerCertificate=false`), so the SQL Server certificate's CA must be trusted by the JVM truststore.

**Tunable behaviour (`application.yaml`):**

```yaml
app:
  retry:
    max-attempts: 3          # attempts before dead-lettering
    initial-interval-ms: 500 # first backoff, doubled each retry
    multiplier: 2.0
  segment-cache:
    ttl-minutes: 60          # max staleness of a resolved segment
    max-size: 50000          # cache entry cap
  dlq:
    requeue-delay-ms: 5000   # pause before requeueing an unpersistable dead letter
```

## Running

Dev (default profile, runs out of the box):

```
./mvnw spring-boot:run
```

Production (build the jar, supply env vars, activate the prod profile):

```
./mvnw clean package
java -jar target/ops-statistics-service-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

For Windows Authentication, add the auth DLL to the library path (IntelliJ Run Configuration VM options, or the `java` command line):

```
-Djava.library.path=path\to\folder\containing\mssql-jdbc_auth.dll
```

> The service is a background worker (`spring.main.web-application-type: none`); it exposes no HTTP endpoint.

## Project Structure

```
ops-statistics-service/
  src/main/java/                     — Application code
  src/main/resources/                — Configuration (application*.yaml)
  src/test/java/                     — Unit tests
  sql/                               — SQL scripts
  pom.xml                            — Maven build & dependencies
  mvnw, mvnw.cmd, .mvn/              — Maven Wrapper
  README.md

src/main/java/ge/bsb/ops/statistics/
  OpsStatisticsApplication.java      — Entry point
  config/
    RabbitMQConfig.java              — Queues, exchange, DLX/DLQ, listener container
    JacksonConfig.java               — ObjectMapper bean
  listener/
    TransactionEventListener.java    — Consumes transaction events; ack / retry / nack
    DeadLetterListener.java          — Persists dead-lettered messages
  handler/
    MessageHandler.java              — Transactional processing pipeline coordinator
  parser/
    MessageParser.java               — JSON body parsing
  service/
    SegmentResolver.java             — Customer segment resolution (cached)
  repository/
    StatisticsRepository.java        — Statistics increment / decrement
    ProcessedMessageRepository.java  — Idempotency ledger
    DeadLetterRepository.java        — Dead-letter persistence
  model/
    Transaction.java                 — Parsed transaction
    TransactionMessage.java          — Raw RabbitMQ message wrapper
    DeadLetter.java                  — Dead-letter record
    Segment.java                     — Segment label constants
```

## Database

The service uses three tables in the `dbo` schema. The creation script is in `sql/create_tables.sql`.

**`SEGMENT_STATISTICS`** — the aggregated counts:

| Column         | Type        | Description                     |
|----------------|-------------|---------------------------------|
| debit_segment  | VARCHAR(10) | Debit account customer segment  |
| credit_segment | VARCHAR(10) | Credit account customer segment |
| channel_id     | INT         | Transaction channel             |
| doc_date       | DATE        | Document date                   |
| op_count       | INT         | Transaction count               |

> The decrement statement guards `op_count > 0`, so it never attempts to go negative; a delete that would drop below zero (or targets a missing row) is skipped with a warning. A `CHECK (op_count >= 0)` constraint remains as a database-level backstop.

**`PROCESSED_MESSAGES`** — the idempotency ledger:

| Column       | Type          | Description                          |
|--------------|---------------|--------------------------------------|
| message_id   | NVARCHAR(255) | Processed message id (primary key)   |
| processed_at | DATETIME2     | When it was recorded (UTC, default)  |

**`DEAD_LETTERS`** — persisted dead letters:

| Column               | Type          | Description                              |
|----------------------|---------------|------------------------------------------|
| id                   | BIGINT        | Identity primary key                     |
| message_id           | NVARCHAR(255) | Original message id                      |
| original_routing_key | NVARCHAR(255) | Original routing key (from `x-death`)    |
| death_reason         | NVARCHAR(255) | Dead-letter reason (from `x-death`)      |
| death_count          | INT           | Redelivery count (from `x-death`)        |
| body                 | NVARCHAR(MAX) | Raw message body                         |
| created_at           | DATETIME2     | When it was persisted (UTC, default)     |

> **Note:** run this script once to provision the tables before the first run.

## RabbitMQ

| Parameter    | Value                                            |
|--------------|--------------------------------------------------|
| Exchange     | transactions.exchange                                  |
| Type         | Topic                                            |
| Queue        | statistics.queue                        |
| Routing keys | `transaction.create`, `transaction.delete` |
| DLX          | statistics.queue.dlx                    |
| DLQ          | statistics.queue.dlq                    |

- `transaction.create` — increments `op_count` by 1
- `transaction.delete` — decrements `op_count` by 1

Failed messages are retried (see [Message Processing & Reliability](#message-processing--reliability)); once retries are exhausted they are dead-lettered to the DLQ and stored in `DEAD_LETTERS`.

## Logging

The service logs via SLF4J / Logback (Spring Boot default) to the console.

## Build & Dependencies

Built with Maven and Spring Boot 4.0.6 on Java 21. Dependency versions are managed by the Spring Boot BOM (except `mssql-jdbc`, which is pinned).

| Dependency                        | Version      | Purpose                        |
|-----------------------------------|--------------|--------------------------------|
| spring-boot-starter-amqp          | (BOM)        | RabbitMQ integration           |
| spring-boot-starter-jdbc          | (BOM)        | JDBC / `JdbcTemplate`          |
| spring-boot-starter-validation    | (BOM)        | Validation                     |
| spring-boot-starter-actuator      | (BOM)        | Operational endpoints          |
| jackson-databind                  | (BOM)        | JSON parsing                   |
| caffeine                          | (BOM)        | Segment cache (TTL + max size) |
| mssql-jdbc                        | 13.4.0.jre11 | SQL Server JDBC driver         |

---

---

# 🇬🇪 Georgian

# ops-statistics-service

Spring Boot-ზე დაწერილი ფონური სერვისი, რომელიც უსმენს RabbitMQ-ს ტრანზაქციის ივენთებს და აგროვებს სტატისტიკას SQL Server-ის ბაზაში კლიენტის სეგმენტების მიხედვით.

## აღწერა

სერვისი კითხულობს მესიჯებს `transactions.exchange` RabbitMQ exchange-იდან და ითვლის ტრანზაქციების რაოდენობას შემდეგი კრიტერიუმებით:
- დებეტის ანგარიშის კლიენტის სეგმენტი
- კრედიტის ანგარიშის კლიენტის სეგმენტი
- არხი (ChannelID)
- საბუთის თარიღი

## კლიენტის სეგმენტები

სეგმენტი განისაზღვრება შემდეგი პრიორიტეტით (ვჩერდებით პირველივე პირობის დაკმაყოფილებისას):

1. **Company** — კლიენტი არის იურიდიული პირი (`IS_JURIDICAL = 1`)
2. **Unique** — კლიენტს გააჩნია `UNIQUE_BANKER` ატრიბუტი
3. **Premium** — კლიენტს გააჩნია `PREMIUM_BANKER` ატრიბუტი
4. **Mass** — ყველა სხვა შემთხვევა
5. **N/A** — ანგარიშს არ ჰყავს მფლობელი კლიენტი

> თუ ორივე მხარეს (დებეტი და კრედიტი) უკლიენტო ანგარიშია, საბუთი იგნორირდება.

დადგენილი სეგმენტები ინახება ქეშში (Caffeine) მოქმედების ვადით (TTL) — სეგმენტის ცვლილება აისახება TTL-ის განმავლობაში, ცალკე ცვლილების ივენთის საჭიროების გარეშე; ქეშს აქვს ზომის ლიმიტი, ამიტომ ის უსაზღვროდ ვერ გაიზრდება.

## მესიჯების დამუშავება და საიმედოობა

თითოეული მესიჯი გადის ერთ ტრანზაქციულ პროცესს (`MessageHandler`):

1. **იდემპოტენტურობა** — მესიჯის id ინახება `PROCESSED_MESSAGES` ცხრილში. ხელახლა მოტანილი მესიჯი ხვდება primary key-ს, ცნობილია როგორც დუბლიკატი და იგნორირდება. მესიჯი, რომელსაც id არ აქვს, უარყოფილია (მისი დედუპლიკაცია შეუძლებელია).
2. **პარსინგი და სეგმენტების დადგენა** — JSON body იპარსება და ორივე კლიენტის სეგმენტი დგინდება.
3. **გამოყენება** — `op_count` იზრდება (`create`) ან მცირდება (`delete`).

მიწოდების გარანტიები:
- **ხელით დადასტურება (manual ack).** მესიჯი დასტურდება მხოლოდ სრული დამუშავებისა და ბაზაში ჩაწერის (commit) შემდეგ.
- **შეზღუდული ხელახალი მცდელობა დაყოვნებით.** დროებითი შეფერხებები (მაგ. ბაზის ხანმოკლე გათიშვა) მეორდება `app.retry.max-attempts`-ჯერ ექსპონენციალური დაყოვნებით, სანამ მესიჯი DLQ-ში გადავა — ერთი შეფერხება მესიჯს მაშინვე არ კარგავს.
- **მუდმივი (durable) dead letter-ები.** მესიჯები, რომლებმაც ხელახალი მცდელობები ამოწურეს, გადადის dead-letter რიგში და ინახება `DEAD_LETTERS` ცხრილში შესამოწმებლად / ხელახლა გასაშვებად. თუ dead letter-ის ჩაწერა ვერ ხერხდება (მაგ. ბაზა გათიშულია), ის ხელახლა ბრუნდება რიგში მცირე დაყოვნების შემდეგ და არ იკარგება.

## წინაპირობები

- Java 21
- Maven (ან ჩაშენებული Maven Wrapper: `./mvnw`)
- წვდომა RabbitMQ სერვერზე
- წვდომა SQL Server-ზე Windows Authentication-ით (`integratedSecurity=true`)
- `mssql-jdbc_auth` DLL `java.library.path`-ზე Windows Auth-ისთვის

## კონფიგურაცია

კონფიგურაცია დაყოფილია Spring პროფილების მიხედვით:

| ფაილი | დანიშნულება |
|-------|-------------|
| `application.yaml` | საერთო პარამეტრები და `app.*` პარამეტრები; ირჩევს აქტიურ პროფილს |
| `application-dev.yaml` | კონკრეტული დევ მნიშვნელობები — **მუშაობს პირდაპირ** |
| `application-prod.yaml` | კავშირის მნიშვნელობები გარემოს ცვლადებიდან — **არაფერი ინახება რეპოზიტორიაში** |

აქტიური პროფილი ნაგულისხმევად არის `dev`. სხვის ასარჩევად: `--spring.profiles.active=prod` ან `SPRING_PROFILES_ACTIVE=prod`.

**პროდაქშენის გარემოს ცვლადები:**

```
DB_HOST, DB_NAME
RABBITMQ_HOST, RABBITMQ_USERNAME, RABBITMQ_PASSWORD, RABBITMQ_VIRTUAL_HOST
RABBITMQ_PORT   (არასავალდებულო, ნაგულისხმევი 5672)
```

> **TLS:** ორივე პროფილი უკავშირდება `encrypt=true`-ით. დევ ენდობა თვით-ხელმოწერილ სერტიფიკატს (`trustServerCertificate=true`); პროდაქშენი ამოწმებს სერტიფიკატის ჯაჭვს (`trustServerCertificate=false`), ამიტომ SQL Server-ის სერტიფიკატის CA უნდა იყოს ნდობით აღჭურვილი JVM-ის truststore-ში.

**კონფიგურირებადი ქცევა (`application.yaml`):**

```yaml
app:
  retry:
    max-attempts: 3          # მცდელობები DLQ-ში გადასვლამდე
    initial-interval-ms: 500 # პირველი დაყოვნება, ორმაგდება ყოველ მცდელობაზე
    multiplier: 2.0
  segment-cache:
    ttl-minutes: 60          # სეგმენტის ქეშის მაქსიმალური სიძველე
    max-size: 50000          # ქეშის ჩანაწერების ლიმიტი
  dlq:
    requeue-delay-ms: 5000   # დაყოვნება ჩაუწერელი dead letter-ის ხელახლა რიგში დაბრუნებამდე
```

## გაშვება

დევ (ნაგულისხმევი პროფილი, მუშაობს პირდაპირ):

```
./mvnw spring-boot:run
```

პროდაქშენი (ააგე jar, მიაწოდე გარემოს ცვლადები, გაააქტიურე prod პროფილი):

```
./mvnw clean package
java -jar target/ops-statistics-service-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod
```

Windows Authentication-ისთვის დაამატეთ auth DLL library path-ზე (IntelliJ Run Configuration-ის VM options, ან `java` ბრძანების ხაზზე):

```
-Djava.library.path=path\to\folder\containing\mssql-jdbc_auth.dll
```

> სერვისი ფონური მუშაკია (`spring.main.web-application-type: none`); ის არ ხსნის HTTP endpoint-ს.

## პროექტის სტრუქტურა

```
ops-statistics-service/
  src/main/java/                     — აპლიკაციის კოდი
  src/main/resources/                — კონფიგურაცია (application*.yaml)
  src/test/java/                     — Unit ტესტები
  sql/                               — SQL სკრიპტები
  pom.xml                            — Maven build და დამოკიდებულებები
  mvnw, mvnw.cmd, .mvn/              — Maven Wrapper
  README.md

src/main/java/ge/bsb/ops/statistics/
  OpsStatisticsApplication.java      — საწყისი წერტილი
  config/
    RabbitMQConfig.java              — რიგები, exchange, DLX/DLQ, listener container
    JacksonConfig.java               — ObjectMapper bean
  listener/
    TransactionEventListener.java    — ტრანზაქციის ივენთების მიღება; ack / retry / nack
    DeadLetterListener.java          — dead letter-ების ჩაწერა ბაზაში
  handler/
    MessageHandler.java              — ტრანზაქციული დამუშავების კოორდინატორი
  parser/
    MessageParser.java               — JSON body-ს პარსინგი
  service/
    SegmentResolver.java             — კლიენტის სეგმენტის დადგენა (ქეშირებული)
  repository/
    StatisticsRepository.java        — სტატისტიკის ზრდა / კლება
    ProcessedMessageRepository.java  — იდემპოტენტურობის ჟურნალი
    DeadLetterRepository.java        — dead letter-ების ჩაწერა
  model/
    Transaction.java                 — დაპარსული ტრანზაქცია
    TransactionMessage.java          — RabbitMQ მესიჯის wrapper
    DeadLetter.java                  — dead letter-ის ჩანაწერი
    Segment.java                     — სეგმენტების ლეიბლების კონსტანტები
```

## მონაცემთა ბაზა

სერვისი იყენებს სამ ცხრილს `dbo` სქემაში. შექმნის სკრიპტი მოთავსებულია `sql/create_tables.sql`-ში.

**`SEGMENT_STATISTICS`** — აგრეგირებული რაოდენობები:

| სვეტი          | ტიპი        | აღწერა                     |
|----------------|-------------|----------------------------|
| debit_segment  | VARCHAR(10) | დებეტის კლიენტის სეგმენტი  |
| credit_segment | VARCHAR(10) | კრედიტის კლიენტის სეგმენტი |
| channel_id     | INT         | საბუთის არხი               |
| doc_date       | DATE        | საბუთის თარიღი             |
| op_count       | INT         | ტრანზაქციების რაოდენობა    |

> კლების ოპერაცია იცავს პირობას `op_count > 0`, ამიტომ არასდროს ცდილობს უარყოფით მნიშვნელობას; წაშლა, რომელიც ნულზე დაბლა ჩავარდნას გამოიწვევდა (ან ეხება არარსებულ ჩანაწერს), იგნორირდება გაფრთხილებით. `CHECK (op_count >= 0)` constraint რჩება ბაზის დონის დამატებით დაცვად.

**`PROCESSED_MESSAGES`** — იდემპოტენტურობის ჟურნალი:

| სვეტი        | ტიპი          | აღწერა                                 |
|--------------|---------------|----------------------------------------|
| message_id   | NVARCHAR(255) | დამუშავებული მესიჯის id (primary key)  |
| processed_at | DATETIME2     | ჩაწერის დრო (UTC, ნაგულისხმევი)        |

**`DEAD_LETTERS`** — შენახული dead letter-ები:

| სვეტი                | ტიპი          | აღწერა                                   |
|----------------------|---------------|------------------------------------------|
| id                   | BIGINT        | Identity primary key                     |
| message_id           | NVARCHAR(255) | თავდაპირველი მესიჯის id                  |
| original_routing_key | NVARCHAR(255) | თავდაპირველი routing key (`x-death`-იდან)|
| death_reason         | NVARCHAR(255) | dead-letter-ის მიზეზი (`x-death`-იდან)   |
| death_count          | INT           | ხელახალი მიწოდების რაოდენობა (`x-death`)  |
| body                 | NVARCHAR(MAX) | მესიჯის დაუმუშავებელი body               |
| created_at           | DATETIME2     | ჩაწერის დრო (UTC, ნაგულისხმევი)          |

> **შენიშვნა:** გაუშვით ეს სკრიპტი ერთხელ ცხრილების შესაქმნელად პირველ გაშვებამდე.

## RabbitMQ

| პარამეტრი    | მნიშვნელობა                                      |
|--------------|--------------------------------------------------|
| Exchange     | transactions.exchange                                  |
| ტიპი         | Topic                                            |
| რიგი         | statistics.queue                        |
| Routing keys | `transaction.create`, `transaction.delete` |
| DLX          | statistics.queue.dlx                    |
| DLQ          | statistics.queue.dlq                    |

- `transaction.create` — `op_count` იზრდება 1-ით
- `transaction.delete` — `op_count` მცირდება 1-ით

წარუმატებელი მესიჯები მეორდება (იხ. [მესიჯების დამუშავება და საიმედოობა](#მესიჯების-დამუშავება-და-საიმედოობა)); მცდელობების ამოწურვის შემდეგ ისინი გადადის DLQ-ში და ინახება `DEAD_LETTERS`-ში.

## ლოგები

სერვისი წერს ლოგებს SLF4J / Logback-ის მეშვეობით (Spring Boot-ის ნაგულისხმევი) კონსოლში.

## აგება და დამოკიდებულებები

აგებულია Maven-ითა და Spring Boot 4.0.6-ით Java 21-ზე. დამოკიდებულებების ვერსიებს მართავს Spring Boot BOM (გარდა `mssql-jdbc`-ისა, რომელიც ფიქსირებულია).

| დამოკიდებულება                    | ვერსია       | დანიშნულება                     |
|-----------------------------------|--------------|---------------------------------|
| spring-boot-starter-amqp          | (BOM)        | RabbitMQ ინტეგრაცია             |
| spring-boot-starter-jdbc          | (BOM)        | JDBC / `JdbcTemplate`           |
| spring-boot-starter-validation    | (BOM)        | ვალიდაცია                       |
| spring-boot-starter-actuator      | (BOM)        | ოპერაციული endpoint-ები         |
| jackson-databind                  | (BOM)        | JSON პარსინგი                   |
| caffeine                          | (BOM)        | სეგმენტების ქეში (TTL + ლიმიტი)  |
| mssql-jdbc                        | 13.4.0.jre11 | SQL Server JDBC დრაივერი        |
