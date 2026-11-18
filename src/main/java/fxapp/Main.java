package fxapp;


import controller.AppViewController;
import controller.LoginController;
import controller.ProfileController;
import controller.RegistrationController;
import controller.WSRViewerController;
import controller.WaterSourceReportController;
import controller.WelcomeController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.fxml.JavaFXBuilderFactory;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import model.Account;
import model.persistence.DataAccessException;
import model.persistence.DatabaseLocation;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Optional;
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
    private final double MINIMUM_WINDOW_WIDTH = 800.0;
    private final double MINIMUM_WINDOW_HEIGHT = 600.0;

    @Override
    public void start(Stage primaryStage) {
        if (context == null) {
            DatabaseLocation location = null;
            try {
                location = DatabaseLocation.fromSystemProperties();
                context = AppContext.open(location);
            } catch (DataAccessException e) {
                String where = location == null ? "(unknown location)" : location.toString();
                LOGGER.log(Level.SEVERE, "Could not open the database " + where, e);
                Alert alert = new Alert(Alert.AlertType.ERROR,
                        "Could not open the database at " + where + ".\n" + e.getMessage());
                alert.setHeaderText("Clean Water Crowdsourcing cannot start");
                alert.showAndWait();
                Platform.exit();
                return;
            }
        }
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
     * Closes the database when the application exits.
     */
    @Override
    public void stop() {
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
     * a method to send the app to the welcome screen
     */
    private void gotoWelcome() {
        try {
            WelcomeController welcome = (WelcomeController) replaceSceneContent("/view/WelcomePage.fxml");
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
            RegistrationController registration = (RegistrationController) replaceSceneContent("/view/RegisterPage.fxml");
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
            ProfileController profile = (ProfileController) replaceSceneContent("/view/ProfilePage.fxml");
            profile.setApp(this);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Could not load scene", ex);
        }
    }

    /**
     * a method to send the app to the map page
     */
    public void gotoApp() {
        try {
            AppViewController appView = (AppViewController) replaceSceneContent("/view/appview.fxml");
            appView.setApp(this);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Could not load scene", ex);
        }
    }

    /**
     * a method to direct the program to the water source creation page
     */
    public void gotoWaterSourceReport() {
        try {
            WaterSourceReportController waterSourceReportController = (WaterSourceReportController) replaceSceneContent("/view/WaterSourceReport.fxml");
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
            WSRViewerController WSRViewers = (WSRViewerController) replaceSceneContent("/view/WSRViewer.fxml");
            WSRViewers.setApp(this);
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Could not load scene", ex);
        }
    }

    /**
     * Checks the id and password and, if they match, logs the account in and shows
     * the app view.
     * @param userId the id of the account to log in, in any case
     * @param password the password; not modified, the caller should clear it
     * @return true if the account was logged in, false if id or password is wrong
     */
    public boolean accountLogging(String userId, char[] password) {
        Optional<Account> account = context.getAuthService().login(userId, password);
        if (account.isEmpty()) {
            return false;
        }
        loggedAccount = account.get();
        gotoApp();
        return true;
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

    /**
     * a method to initialize the page passed from the fxml
     * @param fxml the fxml files used with the app page
     * @return the controller of the page the app is currently on
     * @throws Exception if the loading happened multiple times
     */
    private Initializable replaceSceneContent(String fxml) throws Exception {
        FXMLLoader loader = new FXMLLoader();
        loader.setBuilderFactory(new JavaFXBuilderFactory());
        loader.setLocation(Main.class.getResource(fxml));
        AnchorPane page;
        try (InputStream in = Main.class.getResourceAsStream(fxml)) {
            if (in == null) {
                throw new IOException("FXML resource not found on classpath: " + fxml);
            }
            page = (AnchorPane) loader.load(in);
        }
        Scene scene = new Scene(page, MINIMUM_WINDOW_WIDTH, MINIMUM_WINDOW_HEIGHT);
        stage.setScene(scene);
        stage.sizeToScene();
        return (Initializable) loader.getController();
    }

    /**
     * a method to show the dialog for login
     * @return whether or not the dialog was shown
     */
    public boolean showLoginDialog() {
        try {
            // Load the fxml file and create a new stage for the popup dialog.
            FXMLLoader loader = new FXMLLoader();
            URL location = Main.class.getResource("/view/LoginPage.fxml");
            if (location == null) {
                throw new IOException("FXML resource not found on classpath: /view/LoginPage.fxml");
            }
            loader.setLocation(location);
            AnchorPane page = loader.load();

            // Create the dialog Stage.
            Stage dialogStage = new Stage();
            dialogStage.setTitle("Log in");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(stage);
            Scene scene = new Scene(page);
            dialogStage.setScene(scene);

            // Set the person into the controller.
            LoginController controller = loader.getController();
            controller.setDialogStage(dialogStage);
            controller.setApp(this);

            // Show the dialog and wait until the user closes it
            dialogStage.showAndWait();

            return controller.confirmLoginClicked();

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Could not load the login dialog", e);
            return false;
        }
    }

    /**
     * the method to starts the application
     * @param args any arguments that may be applied to the program
     */
    public static void main(String[] args) {
        launch(args);
    }
}