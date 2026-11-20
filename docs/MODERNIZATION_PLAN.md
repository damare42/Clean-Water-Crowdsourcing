# Clean Water Crowdsourcing – Modernization Plan

The app is a JavaFX desktop client (2016 coursework) where users register and file
water source reports. This plan brings it to a buildable, tested, persistent and
feature-complete state. Each phase is implemented, then reviewed and approved
before the next phase starts.

## Ground rules (all phases)
- Build: Maven, Java 17+, OpenJFX. `mvn -B verify` must pass with no failing tests.
- Keep the package layout (`model`, `controller`, `view`, `fxapp`) unless a phase says otherwise.
- Business logic goes in `model`/services and gets JUnit 5 tests; controllers stay thin.
- No debug `System.out` in new code; use `java.util.logging`.
- No secrets or hardcoded credentials in source.
- Each phase updates the "Status" section below.

## Phase 0 – Make it build
1. `pom.xml`: Java 17, OpenJFX (controls, fxml, web), JUnit 5, surefire, `javafx-maven-plugin` (`mvn javafx:run`), main class `fxapp.Main`.
2. Move FXML to `src/main/resources/view/`; load with absolute resource paths (`/view/X.fxml`).
3. Remove `CWC.iml`/`.DS_Store` from git; `.gitignore` for `target/`, IDE files, OS files.
4. JUnit tests for existing model behaviour (Authenticator, Account, WaterSourceReport, holder).
5. GitHub Actions workflow running `mvn -B verify`.
6. README with build/run/test instructions.

## Phase 1 – Correctness fixes
- `WaterSourceCondition.Portable` → `Potable`.
- Report form: require type and condition; lat/long are numeric `double`s validated to [-90,90]/[-180,180]; no fake defaults; report number label shown.
- Profile: don't save anything when validation fails; dedupe email regex into one shared validator.
- `Account.of()` must not create phantom accounts; consistent property usage for phone/address.
- Registration instantiates the correct subclass (or a role field) for the chosen type.
- Report numbering deterministic; report list ordered by report number.
- Remove debug prints and dead commented-out code.

## Phase 2 – Persistence & security
- Replace `Database.java` stub with SQLite (xerial `sqlite-jdbc`) behind repository interfaces (`AccountRepository`, `ReportRepository`) with in-memory implementations for tests.
- Schema created on startup; DB file in the user's home app dir (configurable via system property).
- Password hashing with PBKDF2 (JDK built-in) + per-user salt; constant-time compare.
- Role-based permission checks in the model/service layer.
- Remove static global maps as the source of truth.

## Phase 3 – Crowdsourcing features
- Map: Leaflet + OpenStreetMap in the WebView (no API key); reports as clickable pins; click-to-pick location on report form.
- Water Purity Reports (Worker+): location, overall condition (Safe/Treatable/Unsafe), virus PPM, contaminant PPM.
- Manager: historical purity chart (virus/contaminant PPM by month for a location and year).
- Admin: ban/unban, delete accounts, security log of login attempts and admin actions.
- Report list as a sortable TableView with detail view.

## Phase 4 – (Decision pending) shared backend
REST server + web/mobile client so reports are actually shared between users.

## Status
- [x] Phase 0
- [x] Phase 1
- [x] Phase 2
- [x] Phase 3
- [x] Cleanup (pre-PR polish: security-log wording and fallback, report-number permission check, admin-dialog close guard, docs)
- [x] UI/UX overhaul (map painting fix and one-scene window, AtlantaFX 2.0.1 theme with Ikonli icons and css/app.css, sidebar shell with role-aware navigation, inline sign-in, remodelled forms, tables, dialogs and map page)
- [ ] Phase 4 (decision pending)
