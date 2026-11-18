# Clean Water Crowdsourcing

A JavaFX desktop client where people register an account and file reports about
water sources (location, source type, and water condition). It started as 2016
coursework and is being modernized step by step; see
[docs/MODERNIZATION_PLAN.md](docs/MODERNIZATION_PLAN.md) for the plan and current status.

Data is currently held in memory only and is lost when the app exits.

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

Tests cover the model classes only and do not need a display.

Run the app with `mvn javafx:run`. The jar built by `mvn verify` is not runnable on its
own with `java -jar`: JavaFX must be on the module path, which the JavaFX Maven plugin
sets up for you.

## Project layout

```
pom.xml                         Maven build (Java 17, OpenJFX, JUnit 5)
src/main/java/fxapp/            Application entry point (fxapp.Main) and scene navigation
src/main/java/controller/       JavaFX controllers, one per screen
src/main/java/model/            Accounts, authentication and water source reports
src/main/resources/view/        FXML layouts, loaded as /view/<Name>.fxml
src/test/java/model/            JUnit 5 tests for the model
docs/MODERNIZATION_PLAN.md      Modernization phases and status
.github/workflows/ci.yml        GitHub Actions: mvn -B verify on push and pull request
```
