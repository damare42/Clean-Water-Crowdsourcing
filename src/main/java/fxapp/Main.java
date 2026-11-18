package fxapp;


import controller.AppViewController;
import controller.LoginController;
import controller.MapPickerController;
import controller.ProfileController;
import controller.RegistrationController;
import controller.ReportDetailsController;
import controller.WSRViewerController;
import controller.WaterSourceReportController;
import controller.WelcomeController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Modality;
import javafx.stage.Stage;
import model.Account;
import model.GeoPoint;
import model.WaterSourceReport;
import model.persistence.DatabaseLocation;

import java.io.IOException;
import java.net.URL;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * the main method of the application that deals with the creation of windows
 * and handling of the interactions between the views and the controllers
 */
public class Main extends Application {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    /** the main container for the application window */
    private Stage stage;
    private AppContext context;
    private Account loggedAccount;
    /** Runs slow work (password hashing) off the FX thread, one job at a time. */
    private ExecutorService background;
    private final double MINIMUM_WINDOW_WIDTH = 800.0;
    private final double MINIMUM_WINDOW_HEIGHT = 600.0;

    @Override
    public void start(Stage primaryStage) {
        if (context == null && !openContext()) {
            Platform.exit();
            return;
        }
        background = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "cwc-background");
            thread.setDaemon(true);
            return thread;
        });
        try {
            stage = primaryStage;
            stage.setTitle("CWC");
            stage.setMinWidth(MINIMUM_WINDOW_WIDTH);
            stage.setMinHeight(MINIMUM_WINDOW_HEIGHT);
            gotoWelcome();
            primaryStage.show();
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Could not load scene", ex);
        }

    }

    /**
     * Opens the database. On any failure (bad path, unreadable file, missing native
     * SQLite library, ...) shows an error and returns false.
     */
    private boolean openContext() {
        DatabaseLocation location = null;
        try {
            location = DatabaseLocation.fromSystemProperties();
            context = AppContext.open(location);
            return true;
        } catch (RuntimeException | LinkageError e) {
            String where = location == null ? "" : " at " + location;
            LOGGER.log(Level.SEVERE, "Could not open the database" + where, e);
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Could not open the database" + where + ".\n" + e.getMessage());
            alert.setHeaderText("Clean Water Crowdsourcing cannot start");
            alert.showAndWait();
            return false;
        }
    }

    /**
     * Stops background work and closes the database when the application exits.
     */
    @Override
    public void stop() {
        if (background != null) {
            background.shutdownNow();
            background = null;
        }
        if (context != null) {
            context.close();
            context = null;
        }
    }

    /**
     * @return the app's services
     */
    public AppContext getContext() {
        return context;
    }

    /**
     * A method to get the account of the user who logged in
     * @return the account of the user who logged in
     */
    public Account getLoggedAccount() { return loggedAccount;}

    /**
     * Runs work on a background thread in a {@link Task}. Exactly one of the
     * callbacks is then called on the FX thread.
     *
     * @param work the work; must not touch the UI
     * @param onSuccess gets the result of the work
     * @param onFailure gets the exception the work threw
     * @param <T> the result type
     */
    public <T> void runInBackground(Callable<T> work, Consumer<T> onSuccess,
                                    Consumer<Throwable> onFailure) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(e -> onSuccess.accept(task.getValue()));
        task.setOnFailed(e -> onFailure.accept(task.getException()));
        background.execute(task);
    }

    /**
     * a method to send the app to the welcome screen
     */
    private void gotoWelcome() {
        try {
            WelcomeController welcome = replaceSceneContent("/view/WelcomePage.fxml");
            welcome.setApp(this);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Could not load scene", ex);
        }
    }

    /**
     * a method to send the app to the registration page
     */
    public void gotoRegistration() {
        try {
            RegistrationController registration = replaceSceneContent("/view/RegisterPage.fxml");
            registration.setApp(this);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Could not load scene", ex);
        }
    }

    /**
     * a method to send the app to the profile page
     */
    public void gotoProfile() {
        try {
            ProfileController profile = replaceSceneContent("/view/ProfilePage.fxml");
            profile.setApp(this);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Could not load scene", ex);
        }
    }

    /**
     * a method to send the app to the map page
     */
    public void gotoApp() {
        loadAppView();
    }

    private Optional<AppViewController> loadAppView() {
        try {
            AppViewController appView = replaceSceneContent("/view/appview.fxml");
            appView.setApp(this);
            return Optional.of(appView);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Could not load scene", ex);
            return Optional.empty();
        }
    }

    /**
     * Shows the map page centred on a report, with its popup open.
     * @param report the report to show
     */
    public void showOnMap(WaterSourceReport report) {
        loadAppView().ifPresent(appView -> appView.focusReport(report));
    }

    /**
     * a method to direct the program to the water source creation page
     */
    public void gotoWaterSourceReport() {
        try {
            WaterSourceReportController waterSourceReportController = replaceSceneContent("/view/WaterSourceReport.fxml");
            waterSourceReportController.setApp(this);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Could not load scene", ex);
        }
    }

    /**
     * a method to send the application to the Water Source Report Viewer page
     */
    public void gotoWSRViewer() {
        try {
            WSRViewerController WSRViewers = replaceSceneContent("/view/WSRViewer.fxml");
            WSRViewers.setApp(this);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Could not load scene", ex);
        }
    }

    /**
     * Logs in an account whose password has been checked and shows the app view.
     * @param account the account that logged in
     */
    public void loginSucceeded(Account account) {
        loggedAccount = account;
        gotoApp();
    }

    /**
     * Logs in a just-registered account and shows its profile page.
     * @param userId the id of the registered account
     * @return true if the account exists and was logged in, false otherwise
     */
    public boolean registrationLogging(String userId) {
        Optional<Account> account = context.getAuthService().findAccount(userId);
        if (account.isEmpty()) {
            return false;
        }
        loggedAccount = account.get();
        gotoProfile();
        return true;
    }

    /**
     * a method to confirm that the user has logged out of the app
     * @return whether or not the user has logged out of the app
     */
    public boolean accountLogout() {
        loggedAccount = null;
        gotoWelcome();
        return true;
    }

    private static FXMLLoader load(String fxml) throws IOException {
        URL location = Main.class.getResource(fxml);
        if (location == null) {
            throw new IOException("FXML resource not found on classpath: " + fxml);
        }
        FXMLLoader loader = new FXMLLoader(location);
        loader.load();
        return loader;
    }

    /**
     * a method to initialize the page passed from the fxml
     * @param fxml the fxml files used with the app page
     * @return the controller of the page the app is currently on
     * @throws IOException if the page cannot be loaded
     */
    private <C> C replaceSceneContent(String fxml) throws IOException {
        FXMLLoader loader = load(fxml);
        Scene scene = new Scene(loader.getRoot(), MINIMUM_WINDOW_WIDTH, MINIMUM_WINDOW_HEIGHT);
        stage.setScene(scene);
        stage.sizeToScene();
        return loader.getController();
    }

    private Stage dialogStage(Parent root, String title) {
        Stage dialog = new Stage();
        dialog.setTitle(title);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.initOwner(stage);
        dialog.setScene(new Scene(root));
        return dialog;
    }

    /**
     * a method to show the dialog for login
     * @return whether or not the dialog was shown
     */
    public boolean showLoginDialog() {
        try {
            FXMLLoader loader = load("/view/LoginPage.fxml");
            Stage dialog = dialogStage(loader.getRoot(), "Log in");
            LoginController controller = loader.getController();
            controller.setDialogStage(dialog);
            controller.setApp(this);

            // Show the dialog and wait until the user closes it
            dialog.showAndWait();

            return controller.confirmLoginClicked();

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not load the login dialog", e);
            return false;
        }
    }

    /**
     * Opens a window with all the details of a report. Does not wait for it to close.
     * @param report the report to show
     */
    public void showReportDetails(WaterSourceReport report) {
        try {
            FXMLLoader loader = load("/view/ReportDetails.fxml");
            Stage dialog = dialogStage(loader.getRoot(), report.getTitle());
            ReportDetailsController controller = loader.getController();
            controller.setUp(this, dialog, report);
            dialog.show();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not load the report details", e);
        }
    }

    /**
     * Lets the user pick a location on a map and waits until the window is closed.
     * @param initial the location to show first, if any
     * @return the chosen location, or empty if the user cancelled
     */
    public Optional<GeoPoint> pickLocation(Optional<GeoPoint> initial) {
        try {
            FXMLLoader loader = load("/view/MapPicker.fxml");
            Stage dialog = dialogStage(loader.getRoot(), "Pick a location");
            MapPickerController controller = loader.getController();
            controller.setUp(this, dialog, initial);
            dialog.showAndWait();
            return controller.getResult();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not load the location picker", e);
            return Optional.empty();
        }
    }

    /**
     * Opens a web link (e.g. the map's attribution) in the system browser instead of
     * inside the app. Only http and https links are opened.
     * @param url the link
     */
    public void openExternalLink(String url) {
        String lower = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) {
            LOGGER.log(Level.WARNING, "Not opening link {0}", url);
            return;
        }
        getHostServices().showDocument(url);
    }

    /**
     * the method to starts the application
     * @param args any arguments that may be applied to the program
     */
    public static void main(String[] args) {
        launch(args);
    }
}
