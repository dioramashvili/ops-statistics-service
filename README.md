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

## გაშვება

IntelliJ-ის Run Configuration-ში დაამატეთ VM option:

```
-Djava.library.path=path\to\folder\containing\mssql-jdbc_auth.dll
```

შემდეგ გაუშვით `Application.java`.

## პროექტის სტრუქტურა

```
src/
  ge/bsb/ops/statistics/
    Application.java                 — საწყისი წერტილი
    consumer/
      RabbitMQConsumer.java          — RabbitMQ კავშირი და მესიჯების მიღება
    handler/
      MessageHandler.java            — დამუშავების პროცესის კოორდინატორი
    model/
      Transaction.java               — ტრანზაქციის მოდელი
      TransactionMessage.java        — RabbitMQ მესიჯის wrapper
    parser/
      MessageParser.java             — JSON body-ს პარსინგი Transaction-ად
    repository/
      DatabaseConnection.java        — SQL Server კავშირი
      StatisticsRepository.java      — სტატისტიკის ჩაწერა ბაზაში
    service/
      SegmentResolver.java           — კლიენტის სეგმენტის განსაზღვრა SQL-იდან
```

## მონაცემთა ბაზა

სტატისტიკა ინახება `basis.OPS_SEGMENT_STATISTICS_DAVIT` ცხრილში:

| სვეტი | ტიპი | აღწერა |
|---|---|---|
| debit_segment | VARCHAR(10) | დებეტის კლიენტის სეგმენტი |
| credit_segment | VARCHAR(10) | კრედიტის კლიენტის სეგმენტი |
| channel_id | INT | საბუთის არხი |
| doc_date | DATE | საბუთის თარიღი |
| op_count | INT | ტრანზაქციების რაოდენობა |

> `op_count` სვეტზე დაწესებულია CHECK constraint (`CHK_op_count_non_negative`), რომელიც არ უშვებს უარყოფით მნიშვნელობებს. თუ წაშლის ოპერაცია გამოიწვევს `op_count`-ის ნულზე დაბლა ჩავარდნას, სერვისი დააიგნორებს ამ ოპერაციას და წერს შესაბამის გაფრთხილებას ლოგში.

## RabbitMQ

| პარამეტრი | მნიშვნელობა |
|---|---|
| Exchange | B6.Transactions |
| ტიპი | Topic |
| Routing keys | `b6.transaction.create`, `b6.transaction.delete` |

- `b6.transaction.create` — `op_count` იზრდება 1-ით
- `b6.transaction.delete` — `op_count` მცირდება 1-ით

## ლოგები

სერვისი წერს ლოგებს კონსოლსა და `logs/` საქაღალდეში. ლოგები ინახება 30 დღე.

## დამოკიდებულებები

| ბიბლიოთეკა | ვერსია | დანიშნულება |
|---|---|---|
| amqp-client | 5.18.0 | RabbitMQ კლიენტი |
| mssql-jdbc | 13.4.0.jre11 | SQL Server JDBC დრაივერი |
| jackson-databind | 2.17.2 | JSON პარსინგი |
| jackson-core | 2.17.2 | JSON პარსინგი |
| jackson-annotations | 2.17.2 | JSON პარსინგი |
| slf4j-api | 2.0.9 | ლოგირების API |
| logback-classic | 1.5.32 | ლოგირების იმპლემენტაცია |
| logback-core | 1.5.32 | ლოგირების იმპლემენტაცია |
| junit | 4.13.2 | Unit ტესტები |
| hamcrest-core | 1.3 | Unit ტესტები |

> ყველა jar ფაილი ხელით არის დამატებული პროექტის `lib/` საქაღალდეში (Maven/Gradle არ გამოიყენება).
