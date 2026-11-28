# Changelog

All notable changes to this project are documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.0.0] - YYYY-MM-DD

First packaged release of the desktop app. Reports are stored on this computer only
("This computer only" mode); sharing through a server is planned (Phase 4).

### Added
- Desktop installers for Linux (.deb and .tar.gz), macOS (.dmg and .zip, Apple silicon and
  Intel) and Windows (.msi and .zip), each with its own Java runtime. No Java install is needed.
- MIT licence (`LICENSE`) and third-party notices (`THIRD-PARTY-NOTICES.md`), also included
  in every installer.
- Approximate location at sign-in: from the internet address (ipapi.co) or the computer's time
  zone, or off. Chosen in Profile → Privacy, with a "Locate me" button and an accuracy circle
  on the map (#3).
- Map markers coloured by condition with a legend; the report panel lists the nearest
  reports first, with their distance (#4).
- Modern interface: AtlantaFX theme, sidebar navigation by role, inline sign-in, forms with
  inline validation, toasts, report cards, badges and resizable windows (#2).
- Water purity reports, purity history chart, account administration (ban, unban, delete)
  and security log; Leaflet/OpenStreetMap map with report pins and location picking;
  SQLite storage with PBKDF2 password hashing and login lockout (#1).
- Maven Wrapper, so building needs only a JDK (#1).

### Changed
- The code is split into the modules `cwc-core` (model and services, no JavaFX) and
  `cwc-desktop` (the JavaFX app); the app is started with
  `./mvnw -pl cwc-desktop -am compile javafx:run` (#5).
- The window keeps its size when moving between pages (#2).

### Fixed
- The map is now drawn completely; parts used to stay grey (#2).
- The security log's Detail column wraps instead of cutting text off (#4).

[Unreleased]: https://github.com/damare42/Clean-Water-Crowdsourcing/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/damare42/Clean-Water-Crowdsourcing/releases/tag/v1.0.0
