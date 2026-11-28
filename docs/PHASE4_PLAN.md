# Phase 4: shared server — decisions and implementation plan

Phase 4 turns the app from a single-computer desktop program into a shared service:
reports filed by one person are seen by everyone. This document records the decisions
taken, the target architecture and the milestones, each with its acceptance checklist.
The status of each milestone is tracked in [MODERNIZATION_PLAN.md](MODERNIZATION_PLAN.md).

The decisions below were confirmed on 2026-11-20.

## 1. Decisions

1. **Clients: one API, two clients, desktop first.**
   - Build the server API first.
   - The JavaFX desktop app then gets a **"Server" mode** (P4.3). It keeps **"This computer only"** (today's local SQLite mode).
   - The **web client** comes later, as P4.7.
2. **Backend: Spring Boot 4.2** (Java 21 runtime; code at Java 17 level so `cwc-core` is shared).
   - Spring MVC and **Spring Security**.
   - **Spring JDBC `JdbcClient`**: explicit SQL, no JPA. The server implements the core repository interfaces.
   - **Flyway** migrations, HikariCP, Actuator health.
   - **PostgreSQL 16.** Locations are plain latitude/longitude columns with B-tree indexes. History uses a bounding-box pre-filter, then the core's exact great-circle filter.
   - PostGIS is not used.
3. **Authentication: opaque server-side sessions** (no JWT); details in §3.4.
   - Web: cookie plus CSRF token. Desktop: Bearer token kept in memory.
   - Ban, delete, password change and logout delete sessions, so the next request is 401.
   - Throttling: per-account lockout (the existing `LockoutPolicy`) plus per-IP token buckets.
4. **Hosting: decide later.** P4.6 prepares:
   - a portable **Dockerfile**;
   - **Docker Compose** (server, postgres:16, **Caddy** for automatic HTTPS);
   - **example** `render.yaml` and `fly.toml`;
   - a backup script.

   **Nothing is deployed by the project itself.** Deploying needs the owner's hosting
   account and credentials.
5. **Existing local data:** local mode stays separate, with no sync. An admin `import-sqlite` command (P4.5) loads a desktop database into the server.

Rejected alternatives are summarised in Appendix A.

## 2. Versions and environment notes

- **Spring Boot 4.2.0** (baseline Java 17). Its BOM pins Flyway 12.11, pgJDBC 42.7.14,
  Jackson 3.1.8 and Testcontainers 2.0.5. Further libraries: bucket4j_jdk17-core 8.22.0,
  springdoc-openapi 3.2.0, Caffeine 3.3.1. These versions were noted while planning;
  confirm them against the Spring Boot BOM when P4.1 starts.
- **PostgreSQL 16.** The extensions `cube`, `earthdistance` and `pgcrypto` are enough;
  **PostGIS is not required.**
- **Docker is optional for development.** Integration tests take a database URL
  (`CWC_TEST_DB_URL`) and only fall back to Testcontainers when a Docker daemon is
  available (§3.6).
- **Embedded PostgreSQL (zonky) is not used:** its `initdb` refuses to run as root, which
  is how many CI and container builds run.
- **Web client tests (P4.7)** use Playwright with an installed Chrome and Node 22.
- **Offline builds:** the multi-module build of P4.0 needs no plugins beyond the ones the
  single-module build already used, so `./mvnw -o` keeps working once the cache is warm.

## 3. Architecture (target)

### 3.1 Modules

```
pom.xml            parent (packaging pom): versions, pluginManagement, enforcer, modules
cwc-core/          plain Java: domain, validation, permissions, services, repository interfaces,
                   in-memory repositories, security (hashing, lockout). No JavaFX, no JDBC driver.
cwc-desktop/       JavaFX app, SQLite repositories (local mode), approximate location (model.geo),
                   map JSON (model.map), resources (FXML, CSS, web/map, geo table)
cwc-server/        (P4.1+) Spring Boot: REST API, security, Postgres repositories, Flyway, CLI
                   commands; later (P4.7) the web client pages and static assets
deploy/            (P4.6) Dockerfile, compose.yaml, Caddyfile, render.yaml, fly.toml examples, backup.sh
```

**Dependency rules**
- `cwc-desktop` → `cwc-core`
- `cwc-server` → `cwc-core`
- `cwc-server` never depends on `cwc-desktop`.

### 3.2 Service seams (P4.3)

- Add small client interfaces in core: `AuthClient`, `ProfileClient`, `ReportsClient`, `PurityClient`, `HistoryClient`, `AdminClient`, `SecurityLogClient`.
  - The **local** implementations wrap today's services.
  - The **remote** implementations use `java.net.http` with the session token.
- The desktop's `AppContext` picks one set by mode.
- The server is authoritative for permissions. The desktop uses the shared `Permission` matrix only to hide navigation.

### 3.3 Data model (PostgreSQL, Flyway)

**`accounts`** (stable ids; usernames can be reused after a delete, but reports keep their link)
- `id uuid PK default gen_random_uuid()`
- `username`
- `username_key`, unique `WHERE deleted_at IS NULL`
- `account_type` CHECK
- `password_hash` (the same PBKDF2 format as the desktop, so imports keep passwords)
- `first_name`, `last_name`, `email`, `phone`, `address`
- `banned`
- `created_at timestamptz default now()`
- `deleted_at` (soft delete; personal fields cleared)

**`sessions`**
- `id`
- `token_hash bytea UNIQUE` (SHA-256 of a 256-bit random token)
- `account_id` FK `ON DELETE CASCADE`
- `client` web|desktop
- `created_at`, `last_seen_at`, `expires_at`
- `ip inet`
- `user_agent`

**`login_failures`** `(username_key, occurred_at)`; pruned daily.

**`source_reports`**
- `number bigint GENERATED ALWAYS AS IDENTITY PK`
- `reporter_id uuid NULL` FK
- `reporter_name` (snapshot)
- `reported_at timestamptz default now()` (server clock)
- `latitude`, `longitude` double precision, with the same CHECK ranges as SQLite
- `water_type`, `water_condition` CHECKs
- indexes `(latitude, longitude)` and `(reported_at)`

**`purity_reports`**
- the same pattern, plus `overall_condition` and the PPM CHECKs
- index `(reported_at, latitude, longitude)`

**`security_log`**
- `id bigint identity`
- `occurred_at timestamptz default now()`
- `event_type` CHECK (existing types plus `SESSION_REVOKED`, `RATE_LIMITED`, `IMPORTED`)
- `actor_id`, `actor_name`, `subject_id`, `subject_name`
- `ip inet`
- `detail`
- **Append-only triggers.** DELETE is allowed only when `current_setting('cwc.retention_job', true) = 'on'`; only the nightly retention job sets it. Default retention is 365 days.

Also **`app_settings`** (key/value) and **`imports`** (file SHA-256, time, summary).

### 3.4 Security model

**Sessions**

| Client | Transport | Idle expiry | Absolute expiry |
|---|---|---|---|
| Web | cookie `__Host-cwc_session` (HttpOnly, Secure, SameSite=Lax) plus Spring Security CSRF (`X-CSRF-TOKEN`) | 30 min | 12 h |
| Desktop | `Authorization: Bearer` (no cookie, no CSRF); token in memory only | 14 days | 30 days |

- **Revocation:** ban, delete, password change, admin "sign out everywhere" and logout delete the account's sessions in the same transaction.
- The role and account are re-read from the database on every request.

**Throttling**
- Per-account lockout: 5 failures in 15 minutes locks for 15 minutes (the existing `LockoutPolicy`, persisted).
- Per-IP limits (bucket4j, in memory):
  - login: 10/min;
  - register: 5/h;
  - report creation: 30/min per account.
- Over a limit: 429 with `Retry-After`.
- The client IP is taken from `X-Forwarded-For` only when the request comes from a configured trusted proxy.

**Authorization:** reuse `Permission`/`AccountType` and `AccessControl`. A denial returns 403 and writes a `PERMISSION_DENIED` event with the IP.

**Validation**
- Reuse core `Validation` and `GeoPoint`.
- Request bodies are limited to 16 KB; unknown JSON fields are rejected; enums must match the exact constant name.
- Errors use RFC 9457 `application/problem+json`.

**Web headers** (P4.1 sets them for the API; P4.7 relies on them)
- **Strict CSP:** `default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data: https://<tile-host>; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'; object-src 'none'`
- HSTS in production; `nosniff`; `Referrer-Policy: same-origin`; `Permissions-Policy: geolocation=(self), camera=(), microphone=()`

**Admin bootstrap** (server-side): `java -jar cwc-server.jar create-admin --id <id>`.
- The password comes from stdin or `CWC_ADMIN_PASSWORD_FILE`.
- It works only while no admin exists, and never echoes or logs the password.

**Secrets:** only in environment variables or files; never committed; never logged (no passwords, tokens or request bodies).

**Location:** no server endpoint and no server-side IP lookup.
- The desktop keeps its client-side feature.
- The web client (P4.7) uses browser geolocation with consent, falling back to the browser time zone.

### 3.5 API (`/api/v1`, JSON)

**Auth**

| Method | Path | Notes |
|---|---|---|
| POST | `/auth/register` | Admin type is never accepted. Returns 201 with the account; the session is a cookie for web, or `{token}` when the client sends `X-CWC-Client: desktop`. |
| POST | `/auth/login` | 200, or 401 with today's generic message (lockout message included), or 429. |
| POST | `/auth/logout` | 204 |
| GET | `/auth/me` | id, username, type, permissions, profile fields |
| POST | `/auth/password` | revokes the other sessions |
| GET | `/auth/csrf` | web only |

**Profile**

| Method | Path | Notes |
|---|---|---|
| PATCH | `/me` | profile fields only |

**Reports and history**

| Method | Path | Notes |
|---|---|---|
| GET / POST | `/source-reports` | `?bbox=minLat,minLon,maxLat,maxLon&after=<number>&limit≤500` (keyset paging) |
| GET | `/source-reports/{number}` | |
| GET / POST | `/purity-reports` | same query parameters |
| GET | `/purity-reports/{number}` | |
| GET | `/purity-history` | `?lat&lon&radiusKm&year&metric=VIRUS\|CONTAMINANT&zone=<IANA id>`. Returns `{count, months:[{month, average, reports}]}`. |

**Admin** (`MANAGE_USERS`)

| Method | Path | Notes |
|---|---|---|
| GET | `/admin/accounts` | `?after&limit` |
| POST | `/admin/accounts/{id}/ban` | revokes sessions |
| POST | `/admin/accounts/{id}/unban` | |
| DELETE | `/admin/accounts/{id}` | soft delete + revoke |
| POST | `/admin/accounts/{id}/sessions/revoke` | |
| GET | `/admin/security-log` | `?before=<id>&limit≤200` (newest first, keyset) |

**Ops**
- `/actuator/health/liveness`, `/actuator/health/readiness`.
- `openapi.json` is generated and committed under `docs/api/`. CI fails if it changes without being committed.

### 3.6 Testing strategy

- **Existing tests** (656 before P4.0): they keep their names and move with their classes. P4.0 proves this by comparing the set of surefire test-case names (`class#method`) before and after the split.
- **Default `./mvnw verify` stays hermetic:**
  - core and desktop unit tests;
  - server MockMvc slice tests;
  - security configuration tests (CSRF, headers, 401/403);
  - rate limits with a fake clock.
- **Integration tests** (Maven failsafe, profile `-Pit`) run against real PostgreSQL 16. A JUnit extension picks the database in this order:
  1. `CWC_TEST_DB_URL`;
  2. Testcontainers, if a Docker daemon exists;
  3. otherwise a clear failure.
- **Where the database comes from:**
  - **CI:** GitHub Actions `services: postgres:16`.
  - **Local development without Docker:** start a local PostgreSQL 16 cluster (Debian/Ubuntu
    shown), then create the test role and database.

    ```
    pg_ctlcluster 16 main start
    runuser -u postgres -- psql -c "create role cwc_test login password 'test'" -c "create database cwc_test owner cwc_test"
    export CWC_TEST_DB_URL=jdbc:postgresql://localhost:5432/cwc_test?user=cwc_test&password=test
    ```

    Stop the cluster afterwards.
- **No H2, no zonky:** H2 behaves differently from PostgreSQL (triggers, partial indexes, `inet`, identity columns), and zonky's `initdb` refuses to run as root.
- **Repository contract tests:** the core's abstract `*RepositoryContract` tests (shipped in the core test-jar since P4.0) run against in-memory, SQLite and Postgres.
- **API tests:**
  - every endpoint × every role (User, Worker, Manager, Admin, anonymous, banned);
  - validation;
  - paging;
  - **history parity** with core `PurityReportService.monthlyAverages` on random data, including the antimeridian and the poles;
  - `EXPLAIN` shows index use.
- **Contract and end-to-end tests:**
  - the desktop remote client is tested against a stub JDK `HttpServer` replaying recorded responses;
  - one end-to-end test runs the real server (random port, real Postgres) with the headless desktop remote client: two users, a shared report, ban revokes the session.
- **Desktop UI checks** (screenshot, flow, map, navigation-guard, CSP, error, keyboard and location checks, run against the real app under Xvfb) keep passing in local mode. A remote-mode flow check is added in P4.3.
- **P4.7:** Playwright with the installed Chrome, plus axe accessibility checks.

## 4. Milestones

Each milestone is shippable and reviewable on its own. Every one ends with `./mvnw -B verify` green.

### P4.0 — Multi-module split, no behaviour change

Start from a clean tree.

#### P4.0.1 Classification rules

Packages are unchanged: no class is renamed and no import changes. The same package (`model.persistence`) may now live in both jars. That is legal on the classpath, and the app is not a JPMS module.

**To `cwc-core`**

| Main sources | Tests |
|---|---|
| `src/main/java/model/*.java` (every file directly in `model/`: Account, AccountSummary, AccountType, Admin, GeoPoint, Manager, MonthlyAverage, Permission, PurityHistory, PurityMetric, Report, ReportField, User, Validation, WaterPurity*, WaterSource*, Worker) | `src/test/java/model/*Test.java` (Account, GeoPoint, PermissionMatrix, Validation, WaterPurityReport, WaterSourceReport) |
| `model/security/**` | `model/security/**` |
| `model/service/**` | `model/service/**` |
| `model/persistence/`: AccountRepository, DataAccessException, DuplicateKeyException, PurityReportRepository, ReportRepository, SecurityLogRepository, SettingsRepository, InMemoryAccountRepository, InMemoryPurityReportRepository, InMemoryReportRepository, InMemorySecurityLogRepository, InMemorySettingsRepository | `model/persistence/InMemory*Test.java` and all `*RepositoryContract.java` |
| | `testutil/**` (MutableClock, FailingSecurityLogRepository) |

**To `cwc-desktop`** (everything else)

| Main | Tests |
|---|---|
| `fxapp/**`, `controller/**` | `fxapp/AppContextTest` |
| `model/geo/**`, `model/map/**` (client-side features) | `model/geo/**`, `model/map/**` |
| `model/persistence/DatabaseLocation.java`, `model/persistence/Sqlite*.java` | `model/persistence/Sqlite*Test.java`, `model/persistence/DatabaseLocationTest.java` |
| **all of `src/main/resources/`**: view, css, web (incl. Leaflet), geo | |

**Rule for files added later**
- **New top-level `model/*.java` files go to core.** If one needs JavaFX, SQLite, `model.geo` or `model.map`, that is a design error: move the dependency, do not move the file.
- **New `model/persistence` files:** interfaces and in-memory implementations go to core; SQLite goes to desktop.
- **Anything under `fxapp/`, `controller/`, `model/geo/`, `model/map/` or `resources/`** goes to desktop.
- **Dependency check** (run it on the tree being split, before moving):

  ```
  grep -l "Sqlite\|DatabaseLocation\|model\.geo\|model\.map\|javafx\|java\.sql\|org\.sqlite" <core files>
  ```

  It must list only `model/Account.java` (fixed in P4.0.2), and the same check on core tests must list nothing.

**How to move:** `git mv` only (run from the repository root on a clean tree), so history follows every file. Afterwards `src/` must be empty and is removed. The result is pure renames (R100), with `Account.java` as the one edited rename.

**Path references to update** (text only)
- README: the "Map and network", "Look and feel" and "Project layout" sections reference `src/main/...`; these become `cwc-desktop/src/main/...` or `cwc-core/src/main/...`.
- `tools/gen-zone-coordinates.py`: docstring and usage now write to `cwc-desktop/src/main/resources/geo/zone-coordinates.tsv`.
- `docs/MODERNIZATION_PLAN.md`: the Phase 0 note can stay as history; add the P4.0 status line.
- `.gitignore`: `target/` already matches nested `cwc-*/target/`; make that explicit with a comment.

#### P4.0.2 Remove JavaFX from `model.Account` (the only code change)

Exact edit:
- Delete the imports `javafx.beans.property.SimpleStringProperty` and `javafx.beans.property.StringProperty`.
- Replace `private final StringProperty fname = new SimpleStringProperty();`, and the same for `lname`, `email`, `phone` and `address`, with `private String fname;` and so on.
- In the constructor and `updateProfile`, replace `this.x.set(v)` with `this.x = v`, keeping the existing `phone == null ? "" : phone` defaulting.
- In the getters, replace `return x.get();` with `return x;`.

**Why controllers don't break:** the properties were private and no `xxxProperty()` accessor existed. A grep of `controller/`, `fxapp/` and `model/` found no use. The public API (`getFname()`…`getAddress()`, `updateProfile`, `create`, `getId`, `getIdKey`, permissions) is unchanged.

**Thread-safety:** unchanged. `StringProperty` gave no synchronisation either. `Account` objects stay confined to the thread that uses them; the server builds its own instances per request.

#### P4.0.3 POM structure

**Root `pom.xml`**
- `edu.cwc:clean-water-crowdsourcing-parent:${revision}` (the version is set once in the parent; see the README "Releasing" note), packaging `pom`, `<modules>cwc-core, cwc-desktop</modules>`.
- **Properties:** encoding, `maven.compiler.release=17`, every library and plugin version (javafx, junit, sqlite-jdbc, atlantafx with its pin comment, ikonli, all plugin versions).
- **`dependencyManagement`:**
  - `cwc-core` (`${project.version}`);
  - the `cwc-core` test-jar (`<type>test-jar</type>`, scope test);
  - `junit-jupiter`.
- **`pluginManagement`:** clean, install, deploy, compiler, resources, surefire and jar with their versions, plus `javafx-maven-plugin` configured with `<skip>true</skip><mainClass>none</mainClass>`.
  - **Why:** `javafx:run` invoked from the root then no-ops in the parent and in core, instead of failing with "parameters 'mainClass' … missing".
- **`build/plugins`:** `maven-enforcer-plugin` (`requireMavenVersion [3.6.3,)`, `requireJavaVersion [17,)`, plus `<dependencyConvergence/>`), inherited by both modules.

**`cwc-core/pom.xml`**
- Parent as above; artifactId `cwc-core`.
- Only dependency: `junit-jupiter`, scope test.
- An enforcer execution `core-stays-plain-java` with `bannedDependencies` excluding `org.openjfx`, `io.github.mkpaz`, `org.kordamp.ikonli` and `org.xerial`. Adding `javafx-base` to core must fail `validate` with `BannedDependencies failed`.
- `maven-jar-plugin` goal `test-jar`, which publishes the contract tests and testutil to the desktop tests.

**`cwc-desktop/pom.xml`**
- Parent as above; artifactId `cwc-desktop`.
- **App properties moved here** from the old root: `main.class=fxapp.Main`, `cwc.db.path`, `cwc.bootstrapAdmin`, `cwc.geo.url=default`, with their comments.
- **Dependencies:**
  - `cwc-core`;
  - javafx-controls, javafx-fxml, javafx-web;
  - atlantafx-base, with the existing javafx-controls exclusion and its comment;
  - ikonli-javafx and ikonli-material2-pack;
  - sqlite-jdbc;
  - junit-jupiter (test);
  - the `cwc-core` test-jar (test).
- **`javafx-maven-plugin` (only here):** `<skip>false</skip>`, `<mainClass>${main.class}</mainClass>`, and the three `-D` options as today.

**Maven Wrapper:** `.mvn/`, `mvnw` and `mvnw.cmd` stay at the root, unchanged. `./mvnw` builds all modules.

**Commands**

| Task | Command |
|---|---|
| Build and test everything | `./mvnw verify` |
| Run the app | `./mvnw -pl cwc-desktop -am compile javafx:run` (accepts `-Dcwc.db.path=…`, `-Dcwc.geo.url=…`, `-Dcwc.bootstrapAdmin=…`) |
| Core tests only | `./mvnw -pl cwc-core test` |

- **Run the app:** from a clean tree it compiles core and desktop and runs the app. Nothing is installed into `~/.m2`.
- Plain `./mvnw javafx:run` from the root no longer works: the reactor would need `-am`. The README documents the command above.

#### P4.0.4 CI workflow (`.github/workflows/ci.yml`)

- The root `mvn -B verify` already builds both modules. Change it to `./mvnw -B verify` so the wrapper-pinned Maven is used.
- Add a step that uploads `**/target/surefire-reports/` on failure (`actions/upload-artifact@v4`, `if: failure()`).
- JDK 17 setup and the Maven cache stay.
- No other jobs until P4.1.

#### P4.0.5 Tools outside the build

Tools that run the desktop app from compiled classes (UI checks, screenshot scripts) need
two path changes:

- App classes move from `target/classes` to `cwc-desktop/target/classes`; the packaged app
  jar is `cwc-desktop/target/cwc-desktop-<version>.jar`.
- The core classes come from `cwc-core/target/cwc-core-<version>.jar`, which is on the
  desktop's dependency classpath. Generate that classpath with

  ```
  ./mvnw -B -q -pl cwc-desktop -am package -DskipTests dependency:build-classpath -Dmdep.outputFile=<file>
  ```

  It must be `package`, not `compile`: the desktop's test-scope dependency on the core
  test-jar must exist, or `build-classpath` fails with "Could not find artifact
  edu.cwc:cwc-core:jar:tests".

#### P4.0.6 Acceptance checklist for P4.0

1. `./mvnw -B clean verify` is green, and the test total is unchanged: core + desktop = the single-module total before the split.
2. **Test identity:** after `./mvnw test` on the trees before and after the split, the sorted lists of surefire test cases (`class#method`, from `**/surefire-reports/TEST-*.xml`) are **identical**.
3. **History:** `git show -M --name-status` lists only `R100` renames, plus `R0xx` for `Account.java`, plus added `cwc-core/pom.xml` and `cwc-desktop/pom.xml`, plus modified `pom.xml`, README, the tool, CI and docs. `git log --follow` on a moved file shows its old history.
4. **Core purity:**
   - `grep -rn "import javafx\|java\.awt\|javax\.swing\|java\.sql\|org\.sqlite" cwc-core/src` finds nothing;
   - the enforcer `bannedDependencies` rule is active (a reviewer can prove it by temporarily adding `javafx-base` to core: `validate` fails);
   - `dependencyConvergence` passes.
5. `./mvnw -pl cwc-desktop -am compile javafx:run` starts the app from a clean tree. `-Dcwc.db.path`, `-Dcwc.geo.url` and `-Dcwc.bootstrapAdmin` still work. Nothing is written to `~/.m2/repository/edu`.
6. **No behaviour change:** all desktop UI checks pass with the new classpath (P4.0.5); screenshots match the pre-split screenshots (spot-check the map, a form and the admin pages).
7. **Docs:** the README build and run commands and the project-layout section are updated; the tool path is updated; CI uses `./mvnw` and uploads reports on failure.
8. The only Java source edit is `Account.java` (P4.0.2): `git diff -M --stat` shows no other `.java` content change.

### P4.1 — Server skeleton and authentication

**Work**
- `cwc-server`: Spring Boot 4.2 parent BOM imported in the module via `dependencyManagement`; the root stays a plain parent.
- Flyway V1: `accounts`, `sessions`, `login_failures`, `security_log`, `app_settings`.
- Auth endpoints and `/me`; sessions and throttling per §3.4.
- `create-admin` command; security headers; problem+json; health; generated OpenAPI.
- `-Pit` integration tests per §3.6.

**Acceptance**
1. Register/login/logout.
2. A wrong password gives the generic message.
3. The 6th failure locks for 15 minutes.
4. The 11th login per minute from one IP gives 429 with `Retry-After`.
5. A logged-out token gets 401.
6. `/auth/password` revokes the other sessions.
7. A banned or deleted account's existing session gets 401 on its next request.
8. The DB holds only the token hash (search the DB for the raw token: nothing found).
9. `create-admin` works once, then refuses. Captured logs contain no password and no token.
10. Security headers are on every response, and CSRF is enforced for cookie sessions.
11. `./mvnw verify` (hermetic) and `./mvnw -Pit verify` (against PG) are both green. The CI job uses `services: postgres:16`.
12. `cwc-core` can only use the `java.base` and `java.logging` JDK modules (compiler `--limit-modules` or a test that scans its imports), so the server never depends on desktop or JDBC code through core.

### P4.2 — Reports and history API

**Work**
- Flyway V2: report tables and indexes.
- Create, list (bounding box plus keyset paging) and get endpoints.
- History with the bounding-box pre-filter.
- Permission enforcement and auditing.

**Acceptance**
1. The role × endpoint matrix is all green, including anonymous and banned.
2. History parity holds on at least 200 random cases (antimeridian, poles).
3. Report numbers are monotonic.
4. Reporter id plus name snapshot: deleting an account keeps its reports.
5. `EXPLAIN` uses the indexes.
6. Body-limit, unknown-field and enum tests.

### P4.3 — Desktop "Server" mode

**Work**
- The client interfaces from §3.2: local and remote implementations.
- A mode choice on the sign-in page: "This computer only" / "Server: <url>". The URL comes from `-Dcwc.server.url` or is remembered in `app_settings`; only `https://` URLs, or `http://` on loopback.
- On 401: back to sign-in with a message. Offline: a clear banner.

**Acceptance**
1. All local-mode harnesses are unchanged and passing.
2. A new remote-mode flow check against a local server:
   - two desktop instances (alice, max): alice's report appears for max after a refresh;
   - the admin bans alice, and her next action returns her to sign-in with the ban message;
   - the token never touches disk (scan the local database and the home directory).
3. Contract tests against recorded responses pass.

### P4.4 — Admin and security log over the API

**Work**
- Accounts list, ban, unban, delete, revoke sessions.
- Security log keyset paging.
- Nightly retention job.
- Desktop admin screens work in server mode ("Load older" paging).

**Acceptance**
1. An admin cannot ban or delete themselves.
2. Retention deletes only events older than N days, and only from the job; a manual `DELETE` fails.
3. Paging has no duplicates or gaps under concurrent inserts.
4. Every admin action is logged with actor id and IP.

### P4.5 — SQLite import

**Work:** `import-sqlite <file> [--dry-run] [--on-conflict=skip|rename|merge-by-email] [--force]`.

**Acceptance**
1. Importing a seeded desktop database gives the expected counts.
2. Imported users log in with their old passwords.
3. A clash produces a rename report.
4. Re-import is refused without `--force`.
5. The dry run writes nothing.

### P4.6 — Deployment configuration (nothing deployed)

**Work**
- `deploy/Dockerfile`: multi-stage, `eclipse-temurin:21-jre`, non-root user, `HEALTHCHECK`.
- `deploy/compose.yaml`: server, `postgres:16` with a named volume, `caddy:2` with a Caddyfile for automatic HTTPS for `${CWC_DOMAIN}`.
- `deploy/render.yaml` and `deploy/fly.toml` examples, with comments on which variables and secrets the operator must set.
- `deploy/backup.sh` (`pg_dump` with rotation) and a restore guide.
- README "Run your own server" with an environment-variable table.
- CI: the image build and `docker compose config` validation. A push to a registry happens only if the owner later adds credentials.

**Acceptance**
1. The image builds in CI.
2. `docker compose config` passes.
3. The restore drill is documented and run against a local PostgreSQL cluster.
4. A secret scan of the repo is clean.
5. "Deploy" is explicitly listed as an **owner action** that needs their hosting account.

### P4.7 — Web client (later)

**Proposed approach (confirm when P4.7 starts):**
- Server-rendered pages (Spring MVC + Thymeleaf) with small vanilla ES modules.
- The bundled Leaflet and an adapted map.js reading `/api/v1` JSON.
- Cookie sessions with CSRF; the strict CSP from §3.4.
- Browser geolocation with consent, and the time-zone fallback.
- A configurable tile provider: the OSM volunteer tiles are not suitable for a public site; a free-tier key from MapTiler or Stadia, or self-hosting.

**Can be split** into P4.7a (map, lists, details: read-only) and P4.7b (forms, profile, admin).

**Acceptance**
1. Playwright (installed Chrome): the main flows for each role pass, with zero CSP violations and zero console errors.
2. Keyboard and focus checks; axe has no serious issues.
3. Responsive at 375, 768 and 1440 px.
4. The role matrix holds through the UI.

**Follow-ups after Phase 4**
- Email verification and password reset (needs a mail provider).
- Desktop "remember me" through the OS keychain.
- PostGIS and clustering at scale.
- jpackage installers.
- Moderation tools.

## Appendix A — Alternatives considered (not chosen)

- **Clients:** desktop-only REST client (no phone reach, no strict CSP), or web-first with desktop as legacy (rebuild all screens before any sharing). Both were rejected in favour of an API-first approach with desktop first and web later.
- **Backend:** Javalin 7 + Jdbi (smaller, but sessions, CSRF, headers and validation would be hand-rolled); Helidon/Quarkus (fewer examples, native builds add complexity); JPA (it would replace the explicit-SQL repository pattern).
- **Auth:** JWT access and refresh tokens. Revocation needs state anyway, and they add a larger attack surface.
- **Test database:** H2 PostgreSQL mode (behaves differently) and zonky embedded-postgres (its `initdb` refuses to run as root).
- **Hosting**, to be decided later. Rough monthly costs from public pricing at the time of the decision (check current prices):

  | Option | Approx. cost per month |
  |---|---|
  | Render web service + Postgres | about $13–15 |
  | Small VPS (Hetzner / DigitalOcean) with the Compose and Caddy setup | about €4–6 (self-managed) |
  | Fly.io | about $2–6 compute; managed Postgres from about $38 |
  | Railway | about $5–10 |
