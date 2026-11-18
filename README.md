# Clean Water Crowdsourcing

A JavaFX desktop client where people register an account and file reports about
water sources (location, source type, and water condition). It started as 2016
coursework and is being modernized step by step; see
[docs/MODERNIZATION_PLAN.md](docs/MODERNIZATION_PLAN.md) for the plan and current status.

Accounts and reports are stored in a local SQLite database (see
[Data storage](#data-storage)), so they survive restarts. Data is not shared between
computers yet.

## Prerequisites

- JDK 17 or newer
- Maven 3.6.3 or newer (checked by the build via maven-enforcer-plugin)

The OpenJFX libraries are downloaded by Maven for your platform; no separate JavaFX
install is needed.

## Build, run and test

```sh
# Run the application
mvn javafx:run

# Compile and run all tests
mvn verify
```

Tests cover the model, persistence and service layers and do not need a display.
SQLite tests use temporary files, never your real database.

Run the app with `mvn javafx:run`. The jar built by `mvn verify` is not runnable on its
own with `java -jar`: JavaFX must be on the module path, which the JavaFX Maven plugin
sets up for you.

## Data storage

The app keeps everything in one SQLite file (via the bundled
[xerial sqlite-jdbc](https://github.com/xerial/sqlite-jdbc) driver; nothing to install):

| Platform        | Default location                    |
|-----------------|-------------------------------------|
| Linux / macOS   | `~/.cwc/cwc.db`                     |
| Windows         | `%USERPROFILE%\.cwc\cwc.db`         |

The directory, file and tables are created on first start. To use another file, set
the `cwc.db.path` system property; `:memory:` gives a throw-away database that is
discarded when the app exits:

```sh
mvn javafx:run -Dcwc.db.path=/tmp/cwc-demo.db
mvn javafx:run -Dcwc.db.path=:memory:
```

(The pom passes the Maven property on to the app's JVM; when running `fxapp.Main`
directly, use `java -Dcwc.db.path=... ...` instead.)

To start over, quit the app and delete the file. The schema version is kept in
SQLite's `PRAGMA user_version`; the app upgrades older files on start and refuses
to open a file written by a newer version.

Tables (schema version 1):

- `accounts`: `username_key` (lower-case id, primary key), `username` (as entered),
  `account_type`, `password_hash`, `first_name`, `last_name`, `email`, `phone`, `address`
- `water_source_reports`: `report_number` (primary key, highest + 1), `reporter`,
  `reported_at_ms` (milliseconds since the epoch, UTC), `latitude`, `longitude`,
  `water_type`, `water_condition`

## Security notes

- Passwords are never stored. Each password is hashed with PBKDF2-HMAC-SHA256
  (JDK built-in), a random 16-byte salt per account and 210,000 iterations, and stored as
  `pbkdf2-sha256$<iterations>$<salt>$<hash>`. Because the iteration count is stored with
  the hash, it can be raised later; older hashes are upgraded on the next successful login.
  Hashes are compared in constant time, and failed logins for unknown ids take about as
  long as for known ids.
- Passwords are handled as `char[]` and cleared after use; they are never logged.
- Usernames are unique ignoring case and surrounding whitespace (`Alice` and `alice` are
  the same account) and may not contain spaces.
- On Linux and macOS the `.cwc` directory and the database file are created readable by
  the owner only. Anyone who can read the file can see account details and attempt to
  crack the hashes offline, so keep it private.
- There are no database credentials: SQLite is a local file.
- Each account type has a fixed set of permissions (`model.AccountType`), checked in the
  service layer, which throws `PermissionDeniedException` on denial:

  | Permission             | User | Worker | Manager | Admin |
  |------------------------|:----:|:------:|:-------:|:-----:|
  | Submit source reports  | yes  | yes    | yes     |       |
  | View source reports    | yes  | yes    | yes     | yes   |
  | Submit purity reports  |      | yes    | yes     |       |
  | View purity reports    |      | yes    | yes     |       |
  | View purity history    |      |        | yes     |       |
  | Manage users           |      |        |         | yes   |

  Admin accounts cannot be chosen at registration.

## Project layout

```
pom.xml                         Maven build (Java 17, OpenJFX, JUnit 5)
src/main/java/fxapp/            Application entry point (fxapp.Main) and scene navigation
src/main/java/controller/       JavaFX controllers, one per screen
src/main/java/fxapp/AppContext  Builds the services and opens the database (composition root)
src/main/java/model/            Accounts, roles/permissions, validation, water source reports
src/main/java/model/persistence Repository interfaces with SQLite and in-memory implementations
src/main/java/model/security    PBKDF2 password hashing
src/main/java/model/service     AuthService, AccountService, ReportService, permission checks
src/main/resources/view/        FXML layouts, loaded as /view/<Name>.fxml
src/test/java/                  JUnit 5 tests (repository contract tests run against
                                both the in-memory and the SQLite implementation)
docs/MODERNIZATION_PLAN.md      Modernization phases and status
.github/workflows/ci.yml        GitHub Actions: mvn -B verify on push and pull request
```
