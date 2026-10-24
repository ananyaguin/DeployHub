# Database compatibility

Inspection date: 2026-10-24. No JDBC driver, datasource, or migration has been added. This is the choice to approve before Stage 2.

## What is installed

Two MySQL servers are on this machine. Only one is accepting connections.

| Install | Binary | Version | State |
| --- | --- | --- | --- |
| Official package | `/usr/local/mysql/bin/mysqld` | **9.3.0** Community, Innovation, protocol 10 | Running as `_mysql` since 2026-09-04 (pid 559). `mysqladmin --protocol=TCP -h 127.0.0.1 -P 3306 ping` reached it and returned `Access denied` for `root` with no password. |
| Homebrew | `/opt/homebrew/opt/mysql/bin/mysqld` | **26.7.0** Innovation. `mysql_version.h` names 9.7.0 as the previous LTS. | Not the server on port 3306. Its error log says bind failed because port 3306 is already in use, then the process aborted. `brew services` lists the formula as stopped. |

The `mysql` client on `PATH` is Homebrew 26.7.0. It can speak to the 9.3.0 server far enough to receive an authentication error. No password was supplied, and no login succeeded. No database or user was created.

CloudFlow has no MySQL dependency yet. `backend/pom.xml` does not declare a JDBC driver. The driver version below is the one Spring Boot 3.5.16 would manage if the dependency is added later.

## Driver and server floor

From the local Maven cache of `spring-boot-dependencies` 3.5.16:

| Component | Version | Constraint |
| --- | --- | --- |
| `com.mysql:mysql-connector-j` | **9.7.0** | Managed by the Spring Boot 3.5.16 BOM. Released 2026-03-18. |
| Flyway | 11.7.2 | Managed by the same BOM. Not on the classpath yet. |
| Hibernate ORM | 6.6.53.Final | `MySQLDialect` sets `MINIMUM_VERSION` to `DatabaseVersion.make(8)`, so MySQL 8.0 is the oldest server Hibernate will accept. |

Connector/J 9.7.0 and 26.7.0 were both read from Maven Central. The 9.7.0 driver parses a server version as major.minor.patch and treats every check as a minimum. MySQL 9.3.0 passes those 8.0 minimum checks. The 26.7.0 driver is a newer line that matches the Homebrew server, which is not the server listening on 3306.

MySQL 5.7 is below Hibernate's minimum and is end of life. It is not a target.

## Proposed choice

Use the running **MySQL 9.3.0** server on `127.0.0.1:3306` with Spring Boot's managed **Connector/J 9.7.0**. Do not override the driver version. Do not install another server for this desktop.

That pair is safe to propose because:

- 9.3.0 is newer than Hibernate's MySQL 8.0 floor and older than driver 9.7.0.
- The driver and the server both use protocol 10.
- Java 17 and Spring Boot 3.5.16 stay as they are.

Other desktops can use another MySQL 8.0 or newer server. Homebrew on this Mac also offers `mysql@8.0`, `mysql@8.4`, and `mysql@9.7`. Those are optional elsewhere. They are not required here, and starting one of them on this Mac would collide with port 3306.

Homebrew MySQL 26.7.0 is a valid newer server, but it is not the one CloudFlow can reach today. Using it would mean stopping the 9.3.0 service and overriding the JDBC driver to `mysql-connector-j` 26.7.0. That is a larger change than this desktop needs.

## Connection

Stage 2, after approval, should read only these environment variables:

```
SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/cloudflow
SPRING_DATASOURCE_USERNAME=cloudflow
SPRING_DATASOURCE_PASSWORD=change-me
```

Create a database named `cloudflow` and a user that can use only that database. Keep the password out of Git. Do not point the application at `root`. Spring Boot maps those variable names onto the datasource. `.env.example` would gain these placeholders only when Stage 2 is approved. The file does not contain them yet.

## Migrations

Flyway SQL should stay inside the MySQL 8.0 feature set so 8.0, 8.4, 9.3, and 9.7 can apply the same scripts:

- `InnoDB` and `utf8mb4`, with the server's default collation
- integer keys, `varchar`, `datetime`, and ordinary indexes
- no version-specific syntax, stored programs, or MySQL 9-only features

## Changes that wait for approval

1. Add `spring-boot-starter-data-jpa`, `flyway-core`, `flyway-mysql`, and `mysql-connector-j` to `cloudflow-backend`, with versions taken from the Spring Boot 3.5.16 BOM.
2. Add the three datasource placeholders to `.env.example`.
3. Point the backend datasource at those environment variables.
4. Add the first portable Flyway migration when the first table exists.

No dependency, configuration, or migration was changed for this document.
