# ops-statistics-service

> 🇬🇧 English | [🇬🇪 Georgian](#-georgian)

A background service that listens to RabbitMQ transaction events and aggregates statistics into a SQL Server database by customer segment.

## Description

The service consumes messages from the `Transactions` RabbitMQ exchange and counts transactions by the following criteria:
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

## Prerequisites

- Java 21
- Access to a RabbitMQ server
- Access to a SQL Server instance with Windows Authentication
- `mssql-jdbc_auth` DLL for Windows Authentication support

## Configuration

Copy `config.properties.example` and fill in your values:

```
config.properties.example → config.properties
```

```properties
rabbitmq.host=
rabbitmq.port=
rabbitmq.username=
rabbitmq.password=
rabbitmq.virtualhost=
rabbitmq.queue=
rabbitmq.exchange=
sqlserver.url=
```

> For production: place the file in the project root directory.  
> For tests: place it under the `src/` folder.

## Running

Add the following VM option to your IntelliJ Run Configuration:

```
-Djava.library.path=path\to\folder\containing\mssql-jdbc_auth.dll
```

Then run `Application.java`.

## Project Structure

```
ops-statistics-service/
  src/                               — Source code
  test/                              — Unit tests
  lib/                               — Dependency JAR files
  sql/                               — SQL scripts
  config.properties.example          — Configuration template
  README.md
  .gitignore

src/ge/bsb/ops/statistics/
  Application.java                   — Entry point
  consumer/
    RabbitMQConsumer.java            — RabbitMQ connection and message consumption
  handler/
    MessageHandler.java              — Processing pipeline coordinator
  model/
    Transaction.java                 — Transaction model
    TransactionMessage.java          — RabbitMQ message wrapper
  parser/
    MessageParser.java               — JSON body parsing
  repository/
    DatabaseConnection.java          — SQL Server connection
    StatisticsRepository.java        — Statistics persistence
  service/
    SegmentResolver.java             — Customer segment resolution
```

## Database

Statistics are stored in a dedicated table with the following schema:

| Column         | Type        | Description                     |
|----------------|-------------|---------------------------------|
| debit_segment  | VARCHAR(10) | Debit account customer segment  |
| credit_segment | VARCHAR(10) | Credit account customer segment |
| channel_id     | INT         | Transaction channel             |
| doc_date       | DATE        | Document date                   |
| op_count       | INT         | Transaction count               |

> A CHECK constraint prevents `op_count` from going negative. If a delete operation would cause it to drop below zero, the service logs a warning and ignores the operation.

The table creation script is located in `sql/create_tables.sql`.

## RabbitMQ

| Parameter    | Value                                      |
|--------------|--------------------------------------------|
| Exchange     | Transactions                               |
| Type         | Topic                                      |
| Routing keys | `transaction.create`, `transaction.delete` |

- `transaction.create` — increments `op_count` by 1
- `transaction.delete` — decrements `op_count` by 1

## Logs

The service writes logs to both the console and the `logs/` directory. Logs are retained for 30 days.

## Dependencies

All required JAR files are located in the `lib/` folder. After opening the project in IntelliJ:

1. **File → Project Structure → Modules → Dependencies**
2. Click `+` → **JARs or Directories**
3. Select the `lib/` folder and add all JARs

| Library             | Version       | Purpose                  |
|---------------------|---------------|--------------------------|
| amqp-client         | 5.18.0        | RabbitMQ client          |
| mssql-jdbc          | 13.4.0.jre11  | SQL Server JDBC driver   |
| jackson-databind    | 2.17.2        | JSON parsing             |
| jackson-core        | 2.17.2        | JSON parsing             |
| jackson-annotations | 2.17.2        | JSON parsing             |
| slf4j-api           | 2.0.9         | Logging API              |
| logback-classic     | 1.5.32        | Logging implementation   |
| logback-core        | 1.5.32        | Logging implementation   |
| junit               | 4.13.2        | Unit tests               |
| hamcrest-core       | 1.3           | Unit tests               |

---

---

# 🇬🇪 Georgian

# ops-statistics-service

ფონური სერვისი, რომელიც უსმენს RabbitMQ-ს ტრანზაქციის ივენთებს და აგროვებს სტატისტიკას SQL Server-ის ბაზაში კლიენტის სეგმენტების მიხედვით.

## აღწერა

სერვისი კითხულობს მესიჯებს `B6.Transactions` RabbitMQ exchange-იდან და ითვლის ტრანზაქციების რაოდენობას შემდეგი კრიტერიუმებით:
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

## წინაპირობები

- Java 21
- წვდომა RabbitMQ სერვერზე
- წვდომა SQL Server-ზე (`devcluster\devserv`) Windows Authentication-ით
- `mssql-jdbc_auth-13.4.0.x64.dll` Windows Auth-ისთვის

## კონფიგურაცია

დააკოპირეთ `config.properties.example` ფაილი და შეავსეთ მნიშვნელობები:

```
config.properties.example → config.properties
```

```properties
rabbitmq.host=
rabbitmq.port=
rabbitmq.username=
rabbitmq.password=
rabbitmq.virtualhost=
rabbitmq.queue=
rabbitmq.exchange=
sqlserver.url=
```

> პროდაქშენ გაშვებისთვის ფაილი უნდა მოთავსდეს პროექტის root დირექტორიაში.
> ტესტების გასაშვებად კი `src/` საქაღალდეში.

## გაშვება

IntelliJ-ის Run Configuration-ში დაამატეთ VM option:

```
-Djava.library.path=path\to\folder\containing\mssql-jdbc_auth.dll
```

შემდეგ გაუშვით `Application.java`.

## პროექტის სტრუქტურა

```
ops-statistics-service/
  src/                               — საწყისი კოდი
  test/                              — Unit ტესტები
  lib/                               — დამოკიდებულების jar ფაილები
  sql/                               — SQL სკრიპტები
  config.properties.example          — კონფიგურაციის შაბლონი
  README.md
  .gitignore

src/ge/bsb/ops/statistics/
  Application.java                   — საწყისი წერტილი
  consumer/
    RabbitMQConsumer.java            — RabbitMQ კავშირი და მესიჯების მიღება
  handler/
    MessageHandler.java              — დამუშავების პროცესის კოორდინატორი
  model/
    Transaction.java                 — ტრანზაქციის მოდელი
    TransactionMessage.java          — RabbitMQ მესიჯის wrapper
  parser/
    MessageParser.java               — JSON body-ს პარსინგი Transaction-ად
  repository/
    DatabaseConnection.java          — SQL Server კავშირი
    StatisticsRepository.java        — სტატისტიკის ჩაწერა ბაზაში
  service/
    SegmentResolver.java             — კლიენტის სეგმენტის განსაზღვრა SQL-იდან
```

## მონაცემთა ბაზა

სტატისტიკა ინახება `basis.OPS_SEGMENT_STATISTICS_DAVIT` ცხრილში:

| სვეტი          | ტიპი        | აღწერა                     |
|----------------|-------------|----------------------------|
| debit_segment  | VARCHAR(10) | დებეტის კლიენტის სეგმენტი  |
| credit_segment | VARCHAR(10) | კრედიტის კლიენტის სეგმენტი |
| channel_id     | INT         | საბუთის არხი               |
| doc_date       | DATE        | საბუთის თარიღი             |
| op_count       | INT         | ტრანზაქციების რაოდენობა    |

> `op_count` სვეტზე დაწესებულია CHECK constraint (`CHK_op_count_non_negative`), რომელიც არ უშვებს უარყოფით მნიშვნელობებს. თუ წაშლის ოპერაცია გამოიწვევს `op_count`-ის ნულზე დაბლა ჩავარდნას, სერვისი დააიგნორებს ამ ოპერაციას და წერს შესაბამის გაფრთხილებას ლოგში.

ცხრილის შექმნის სკრიპტი მოთავსებულია `sql/create_tables.sql`-ში.

> **შენიშვნა:** ცხრილი უკვე შექმნილია დევ სერვერზე (`BANK2000`). სკრიპტი საჭიროა მხოლოდ ახალი გარემოს კონფიგურაციისას.

## RabbitMQ

| პარამეტრი    | მნიშვნელობა                                      |
|--------------|--------------------------------------------------|
| Exchange     | B6.Transactions                                  |
| ტიპი         | Topic                                            |
| Routing keys | `b6.transaction.create`, `b6.transaction.delete` |

- `b6.transaction.create` — `op_count` იზრდება 1-ით
- `b6.transaction.delete` — `op_count` მცირდება 1-ით

## ლოგები

სერვისი წერს ლოგებს კონსოლსა და `logs/` საქაღალდეში. ლოგები ინახება 30 დღე.

## დამოკიდებულებები

ყველა საჭირო jar ფაილი მოთავსებულია `lib/` საქაღალდეში. IntelliJ-ში პროექტის გახსნის შემდეგ:

1. **File → Project Structure → Modules → Dependencies**
2. დააჭირეთ `+` → **JARs or Directories**
3. მიუთითეთ `lib/` საქაღალდე და დაამატეთ ყველა jar

| ბიბლიოთეკა          | ვერსია       | დანიშნულება              |
|---------------------|--------------|--------------------------|
| amqp-client         | 5.18.0       | RabbitMQ კლიენტი         |
| mssql-jdbc          | 13.4.0.jre11 | SQL Server JDBC დრაივერი |
| jackson-databind    | 2.17.2       | JSON პარსინგი            |
| jackson-core        | 2.17.2       | JSON პარსინგი            |
| jackson-annotations | 2.17.2       | JSON პარსინგი            |
| slf4j-api           | 2.0.9        | ლოგირების API            |
| logback-classic     | 1.5.32       | ლოგირების იმპლემენტაცია  |
| logback-core        | 1.5.32       | ლოგირების იმპლემენტაცია  |
| junit               | 4.13.2       | Unit ტესტები             |
| hamcrest-core       | 1.3          | Unit ტესტები             |
