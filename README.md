# Clean Water Crowdsourcing

A JavaFX desktop client where people register an account and file reports about
water sources (location, source type, and water condition). It started as 2016
coursework and is being modernized step by step; see
[docs/MODERNIZATION_PLAN.md](docs/MODERNIZATION_PLAN.md) for the plan and current status.

Accounts and reports are stored in a local SQLite database (see
[Data storage](#data-storage)), so they survive restarts. Data is not shared between
computers yet.

## Using the app

The app opens on the welcome page: sign in with your username and password, or choose
**Create an account** (pick User, Worker or Manager; administrators are created as
described in [Creating the first administrator](#creating-the-first-administrator)).
After signing in, a sidebar on the left shows the pages your account type allows (see
[Roles and permissions](#roles-and-permissions)), and the menu at the bottom of the
sidebar (your name and role) has **Profile** and **Log out**. The window can be resized
freely (down to 960x640) and keeps its size when you move between pages.

- **Map**: when you sign in, the map starts at the approximate location of this computer
  (see [Privacy](#privacy)), shown as a dot with a circle for how rough it is, and a note
  at the bottom right says where it came from. **Locate me** looks it up again and
  centres the map on it; **All reports** zooms out to show every report. Every water
  source report is a pin on an OpenStreetMap map. Workers and
  managers also see water purity reports, drawn as round dots. Pins and dots are coloured
  by the report's condition: green for Potable or Safe, teal for Clear, amber for Muddy or
  Treatable, red for Waste or Unsafe (the legend at the bottom left explains the shapes
  and colours). Click a marker to see its fields; **Details** in the popup opens the
  full report. The **Reports** panel on the right lists the same reports as cards: nearest
  first once the approximate location is known, each card saying how far away the report
  is (for example "3.2 km away", measured from that rough location); newest first while
  no location is known or it is switched off. Click a card to centre the map on it,
  double-click it (or press ENTER) for the details. The panel starts hidden in narrow
  windows; **Reports** on the map shows it. **Report water source** and **Report purity**
  on the map open the forms.
- **Report water source**: type the latitude and longitude, or press **Pick on map** and
  click the spot; the chosen point fills both fields (up to 6 decimal places). Choose the
  type and condition and press **Submit report** (or ENTER); **Cancel** (or ESC) goes back
  to the map. A problem is shown above the form and the field concerned is highlighted.
- **Water source reports**: a table of all water source reports with number, date, time,
  reporter, type, condition, latitude and longitude. It starts sorted by report number;
  click a column header to sort by that column. Double-click a row (or select it and
  press **Details**, or press ENTER) to open the report. **Show on map** in the report
  window centres the map on it.
- **Report purity** (workers and managers): location (typed or picked on the map),
  overall condition (Safe, Treatable or Unsafe), virus PPM and contaminant PPM. PPM
  values are plain decimal numbers from 0 to 1,000,000 (no exponents). Purity reports are
  numbered separately from source reports, starting at 1.
- **Purity reports** (workers and managers): a sortable table of all purity reports,
  with the same details window and **Show on map**.
- **Purity history** (managers): enter a location (or pick it on the map), a radius in
  kilometres (default 1 km, measured as great-circle distance) and a year, choose
  **Virus PPM** or **Contaminant PPM**, and press **Show**. The line chart shows the
  monthly average of the reports within the radius; months are calendar months in your
  computer's time zone, and months without reports have no point.
- **Accounts** (administrators): every account with its role, name, e-mail and status.
  **Ban** stops an account from logging in, **Unban** lifts the ban, **Delete** (after a
  confirmation) removes the account. Administrators cannot ban or delete their own
  account. Deleting an account keeps the reports it filed, still showing its id as the
  reporter, and its security log entries; the id becomes free again, so a new account
  could later register the same id.
- **Security log** (administrators): the newest 1000 security events, newest first
  (long details wrap onto more lines; the full text is also in the cell's tooltip); see
  [Security log](#security-log).
- **Profile** (user menu): change your name, e-mail, phone and address; the account type
  and username cannot be changed. Its **Privacy** section sets how this computer's
  approximate location is found (see [Privacy](#privacy)).

### Map and network

The map uses [Leaflet](https://leafletjs.com) 1.9.4, bundled with the app under
`src/main/resources/web/leaflet/` (BSD-2-Clause, see the `LICENSE` file there; taken
unmodified from the npm package `leaflet@1.9.4`). Only the map tiles come from the
network, from `tile.openstreetmap.org`. Without a connection the map background stays
blank (a "Map tiles unavailable" notice says so), but pins, popups, the report list and
everything else keep working.

The map page carries a Content-Security-Policy that blocks network requests from
scripts, frames, workers, plug-ins, forms and `<base>`. It cannot limit where scripts,
styles and images come from: the JavaFX WebKit refuses `jar:` subresources under every
source expression, so such a policy would leave the map blank in the packaged jar (see
the comment in `map.html`). The Java side compensates: the page only gets data as JSON
shown with `textContent`, pop-ups are blocked, any navigation away from the bundled page
is reverted, and the bridge into Java is only given to that exact page.

The approximate location at sign-in is looked up with one HTTPS request to
`ipapi.co` (see [Privacy](#privacy)); everything else works without it.

Map data is &copy; [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors;
the attribution is always shown on the map, and its links open in the system browser.
The OpenStreetMap tile servers are run by volunteers for light use under their
[tile usage policy](https://operations.osmfoundation.org/policies/tiles/); the app
identifies itself with a `CleanWaterCrowdsourcing` user-agent suffix. A deployment with
many users should switch to a commercial or self-hosted tile service (the tile URL is at
the top of `src/main/resources/web/map.js`).

## Privacy

When someone signs in, the map starts at the **approximate** location of this computer.
The app never asks the operating system or a browser for a precise position. How the
location is found is set in **Profile → Privacy**; the setting applies to everyone who
uses the app on this computer:

- **Look up using my internet address** (the default): the app sends one HTTPS `GET` of
  `https://ipapi.co/json/` with only a `User-Agent: CleanWaterCrowdsourcing/0.1 (JavaFX
  desktop app)` and an `Accept: application/json` header. No account name or other data,
  no cookies, no referrer and no query parameters are sent, and redirects are not
  followed. ipapi.co receives your **IP address** (as with any request) and answers with
  an approximate city and coordinates. This happens at most once a day: the answer
  (coordinates, place name and time) is kept in the app's database for 24 hours; **Locate
  me** asks again (but reuses an answer less than a minute old). If the lookup fails, the
  time zone is used instead.
- **Use my time zone only**: the position of the computer's time zone (for example New
  York for `America/New_York`) from a table bundled with the app (generated from the
  public-domain IANA tzdata by `tools/gen-zone-coordinates.py`). Nothing leaves the
  computer.
- **Off**: no location; the map shows all reports as before.

Switching to time zone or Off deletes the kept answer. The lookup can also be switched
off for an installation, whatever the setting says, with `-Dcwc.geo.url=off` (with
`mvn javafx:run -Dcwc.geo.url=off`); the Privacy section then shows the option as
disabled. `-Dcwc.geo.url=<https URL>` uses another service; the app understands the
answers of ipapi.co, [ipwho.is](https://ipwho.is/) and [ipinfo.io](https://ipinfo.io/)
(`mvn javafx:run` passes `default` when nothing is given, which means ipapi.co).

ipapi.co's free service, used without an API key, is meant for light use (about 1,000
requests a day) under its [terms](https://ipapi.co/terms/); the default suits a
non-commercial, light deployment. A larger deployment should use a paid plan or another
provider.

**Open item:** the response formats were taken from the providers' documentation and
tested against local copies only (the providers could not be reached from the build
environment). Before a release, run `./mvnw javafx:run` once on a networked computer and
check that the map centres near the real location with "(from your IP address)"; if the
default provider has changed its answer, switch the default to ipwho.is or ipinfo.io.

## Prerequisites

- JDK 17 or newer (JDK 21 LTS recommended)
- Maven is optional: the bundled Maven Wrapper (`./mvnw`, or `mvnw.cmd` on Windows)
  downloads Maven 3.9.11 on first use. An installed Maven 3.6.3 or newer also works;
  replace `./mvnw` with `mvn` in the commands below.

The OpenJFX libraries are downloaded by Maven for your platform; no separate JavaFX
install is needed.

### Look and feel

The UI uses the [AtlantaFX](https://github.com/mkpaz/atlantafx) PrimerLight theme with the
app's own stylesheet on top (`src/main/resources/css/app.css`), and
[Ikonli](https://kordamp.org/ikonli/) Material Design icons. Both come from Maven and
bundle their fonts and stylesheets; nothing is downloaded at run time. AtlantaFX is pinned
to **2.0.1** on purpose: it is the last release built for Java 17 and JavaFX 20, so it runs
on this project's Java 17 / JavaFX 21 (2.1.0 needs Java 21 and JavaFX 23, 3.0.0 needs
Java 25 and JavaFX 27).

## Build, run and test

```sh
# Run the application
./mvnw javafx:run

# Compile and run all tests
./mvnw verify
```

Tests cover the model, persistence and service layers and do not need a display.
The JavaFX screens and the map page are not covered by `mvn verify`.
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

Tables (schema version 3):

- `accounts`: `username_key` (lower-case id, primary key), `username` (as entered),
  `account_type`, `password_hash`, `first_name`, `last_name`, `email`, `phone`, `address`,
  `banned` (0 or 1; added in version 2, existing accounts start unbanned)
- `water_source_reports`: `report_number` (primary key, highest + 1), `reporter`,
  `reported_at_ms` (milliseconds since the epoch, UTC), `latitude`, `longitude`,
  `water_type`, `water_condition`
- `water_purity_reports` (version 2): `report_number`, `reporter`, `reported_at_ms`,
  `latitude`, `longitude`, `overall_condition` (`Safe`, `Treatable`, `Unsafe`),
  `virus_ppm`, `contaminant_ppm` (0 to 1,000,000)
- `security_log` (version 2): `event_id` (increasing), `occurred_at_ms`, `event_type`,
  `actor`, `subject`, `detail`. Triggers reject every UPDATE and DELETE, so the log is
  append-only.

- `app_settings` (version 3): `key` (primary key, not empty), `value`. Settings of this
  installation: `location.mode` (`IP_LOOKUP`, `TIME_ZONE` or `OFF`) and
  `location.cache` (the last IP lookup result, `lat|lon|accuracyKm|epochMillis|place`,
  used for 24 hours and checked again when read).

Version 1 and 2 files are upgraded in place on start; their accounts and reports are kept.

CHECK constraints keep rows consistent with the Java code: `account_type`,
`water_type`, `water_condition`, `overall_condition` and `event_type` only accept the
names of the Java enum constants, coordinates and PPM values must be in range (NaN and
infinity are rejected), and `username_key` must be lower case. The app lower-cases
ids with Java's full Unicode rules, but SQLite's `lower()` only knows ASCII, so the
database checks `username_key = lower(username)` only for ids made of plain ASCII; for
other ids (e.g. `Ärger`) that rule is enforced by the Java code alone.

## Creating the first administrator

Administrator accounts cannot be chosen at registration. To create the first one, start
the app with the `cwc.bootstrapAdmin` system property set to the administrator's id
while no administrator exists:

```sh
mvn javafx:run -Dcwc.bootstrapAdmin=admin
# or, running fxapp.Main directly: java -Dcwc.bootstrapAdmin=admin ...
```

A **Create administrator** dialog then asks for the name, e-mail and password (the id
comes from the property and cannot be changed in the dialog). Cancel it to start without
creating one. Once an administrator exists the property is ignored (a warning is
logged), so leaving it set does nothing harmful; checking for an administrator and
creating it is atomic. Only someone who can start the app with its database can do
this, which is the same person who could read or replace the database file anyway.

## Security notes

- Passwords are never stored. Each password is hashed with PBKDF2-HMAC-SHA256
  (JDK built-in), a random 16-byte salt per account and 210,000 iterations, and stored as
  `pbkdf2-sha256$<iterations>$<salt>$<hash>`. Because the iteration count is stored with
  the hash, it can be raised later; older hashes are upgraded on the next successful login.
  Hashes are compared in constant time, and failed logins for unknown ids take about as
  long as for known ids.
- Passwords are passed to the model as `char[]` and cleared after use; they are never
  logged. (The JavaFX `PasswordField` itself holds the text as a `String`, which cannot
  be cleared.)
- Hashing a password takes a noticeable fraction of a second, so logging in and
  registering run on a background thread; the buttons are disabled meanwhile.
  Stored hashes with more than 2,000,000 iterations are rejected, so a corrupted or
  tampered hash cannot freeze a login. If upgrading a weak hash fails to save, the login
  still succeeds and the upgrade is retried next time.
- Usernames are unique ignoring case and surrounding whitespace (`Alice` and `alice` are
  the same account) and may not contain spaces.
- On Linux and macOS the `.cwc` directory and the database file are created readable by
  the owner only. If they already exist with wider permissions and belong to you, they
  are tightened on start (`700` for `~/.cwc`, `600` for the file); a directory you chose
  with `cwc.db.path` is left alone, and so is anything that is a symbolic link (changing
  it would change the link's target). Anyone who can read the file can see account
  details and attempt to crack the hashes offline, so keep it private.
- `cwc.db.path` may not contain `?`, which the SQLite driver would treat as the start of
  connection options.
- There are no database credentials: SQLite is a local file.
- Each account type has a fixed set of permissions; see
  [Roles and permissions](#roles-and-permissions).
- Permissions are checked against the account as it was loaded at login. If another
  copy of the app using the same database file bans or deletes the account, that takes
  effect at its next login. Running two copies on one database is not supported.
- Logins of banned accounts are refused. Whether an account is banned is only told to
  someone who gave its correct password; a wrong password gives the same "Invalid log
  in" message as an unknown id, so the login form does not reveal which ids exist or are
  banned.
- Repeated failed logins lock the attempted id: 5 failures for the same id (ignoring
  case and surrounding spaces) within 15 minutes lock it for 15 minutes, during which
  even the correct password is refused with "Too many failed log-ins". Unknown ids are
  locked the same way, so locks reveal nothing either. Only failures since the last
  successful login and since the end of the last lock count. The numbers are constants
  in `model.security.LockoutPolicy`. Locks are worked out from the security log, so they
  survive a restart. Anyone can lock someone else's id for 15 minutes by guessing wrong
  five times; that is the usual trade-off of lockouts.
- The map page never inserts report data as HTML: reports are sent to it as JSON (all
  non-ASCII and `< > & '` characters escaped), passed as a function argument rather than
  pasted into script code, and shown with `textContent`.

## Roles and permissions

Each account type has a fixed set of permissions (`model.AccountType`). The links on the
main page follow them, but the service layer checks them again on every call and throws
`PermissionDeniedException` (and writes a security log entry) on denial, whatever the
screens show.

| Permission                                | User | Worker | Manager | Admin |
|-------------------------------------------|:----:|:------:|:-------:|:-----:|
| Submit source reports                     | yes  | yes    | yes     |       |
| View source reports (map, report list)    | yes  | yes    | yes     | yes   |
| Submit purity reports                     |      | yes    | yes     |       |
| View purity reports (map, purity list)    |      | yes    | yes     |       |
| View purity history (chart)               |      |        | yes     |       |
| Manage users (accounts, security log)     |      |        |         | yes   |

Admin accounts cannot be chosen at registration; see
[Creating the first administrator](#creating-the-first-administrator). Anyone may edit
their own profile; editing someone else's needs "Manage users".

## Security log

The app records security events in the `security_log` table, which only administrators
can read (**Security Log** page, newest first):

| Event                 | Recorded when                                                       |
|-----------------------|---------------------------------------------------------------------|
| Login succeeded       | someone logs in                                                     |
| Login failed          | unknown id, wrong password, banned account, or attempt on a locked id (the reason is in the detail) |
| Login locked          | too many failures lock an id                                        |
| Registration          | an account is registered                                            |
| Administrator created | the first administrator is created at start-up                     |
| Account banned / unbanned / deleted | an administrator does so (who did it is in "By")      |
| Permission denied     | an account, or nobody, tries something its type does not allow      |

Login attempts are recorded under the attempted id in normalized form (trimmed, lower
case). Passwords are never recorded. The id field is recorded as typed (normalized),
including ids that do not exist, so a password typed into the id field by mistake ends
up in the log, where administrators can read it. Text is cut to 200 characters and
control characters are replaced, so the login form cannot be used to flood or garble
the log.

If a login event cannot be written, the login is refused. Registration, administrator
creation, ban, unban and delete save the change first and then record it. If the
record cannot be written, the change stands and the event goes to the application log
(java.util.logging, SEVERE) instead. Permission denials are refused whether or not they
can be recorded.

## Project layout

```
pom.xml                         Maven build (Java 17, OpenJFX, AtlantaFX, Ikonli, JUnit 5)
src/main/java/fxapp/            Application entry point (fxapp.Main), navigation and theme (Theme)
src/main/java/controller/       JavaFX controllers, one per screen; ShellController is the sidebar
                                frame of the signed-in pages, LeafletMap drives the map page
src/main/java/fxapp/AppContext  Builds the services and opens the database (composition root)
src/main/java/model/            Accounts, roles/permissions, validation, water source and
                                purity reports, coordinates and distances (GeoPoint)
src/main/java/model/map         Map markers and the user's location as JSON for the map page
src/main/java/model/geo         Approximate location: IP lookup, strict JSON reader, time-zone
                                table, LocationService (mode, cache, fallback chain)
src/main/java/model/persistence Repository interfaces with SQLite and in-memory implementations
src/main/java/model/security    PBKDF2 password hashing, security events, lockout policy
src/main/java/model/service     AuthService, AccountService, ReportService, PurityReportService,
                                SecurityLog, permission checks (AccessControl)
src/main/resources/view/        FXML layouts, loaded as /view/<Name>.fxml
src/main/resources/css/app.css  App stylesheet (brand colours and components) on top of AtlantaFX
src/main/resources/geo/         Time-zone positions (generated by tools/gen-zone-coordinates.py)
src/main/resources/web/         Map page (map.html, map.js, map.css) and bundled Leaflet
src/test/java/                  JUnit 5 tests (repository contract tests run against
                                both the in-memory and the SQLite implementation)
docs/MODERNIZATION_PLAN.md      Modernization phases and status
.github/workflows/ci.yml        GitHub Actions: mvn -B verify on push and pull request
```
