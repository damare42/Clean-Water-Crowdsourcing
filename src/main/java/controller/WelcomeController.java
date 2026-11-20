package controller;

import fxapp.Main;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import model.service.AuthService;
import model.service.LoginResult;

import java.time.Instant;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The welcome page with the sign-in form. The password is checked on a background thread
 * (hashing takes a moment); the form is disabled meanwhile. A refused sign-in shows why:
 * invalid id or password, banned account (only after a correct password) or too many
 * failed attempts.
 */
public class WelcomeController implements Page {
    private static final Logger LOGGER = Logger.getLogger(WelcomeController.class.getName());
    /** Below this window width the brand panel is hidden. */
    static final double HERO_MIN_WIDTH = 1000;

    @FXML
    private HBox welcomePage;
    @FXML
    private Region hero;
    @FXML
    private HBox compactBrand;
    @FXML
    private TextField accountId;
    @FXML
    private PasswordField password;
    @FXML
    private Label errorMessage;
    @FXML
    private Button signInButton;
    @FXML
    private Button createAccountButton;

    private final ProgressIndicator busyIndicator = new ProgressIndicator();
    private Main application;

    @FXML
    private void initialize() {
        busyIndicator.setPrefSize(16, 16);
        busyIndicator.setMaxSize(16, 16);
        busyIndicator.setMinSize(16, 16);
        responsiveHero(welcomePage, hero, compactBrand);
        Platform.runLater(accountId::requestFocus);
    }

    /**
     * Shows the brand panel only when the page is wide enough; the card then shows a
     * small brand line instead.
     */
    static void responsiveHero(Region page, Node hero, Node compactBrand) {
        page.widthProperty().addListener((observable, old, width) -> {
            boolean wide = width.doubleValue() >= HERO_MIN_WIDTH;
            hero.setVisible(wide);
            hero.setManaged(wide);
            compactBrand.setVisible(!wide);
            compactBrand.setManaged(!wide);
        });
    }

    /**
     * Sets the welcome page up.
     * @param application the main application of welcome page
     */
    @Override
    public void setApp(Main application) {
        this.application = application;
    }

    /**
     * Checks the username and password and, if they are right, signs the account in.
     */
    @FXML
    private void signInPressed() {
        if (signInButton.isDisabled()) {
            return;
        }
        String id = accountId.getText();
        char[] secret = password.getText().toCharArray();
        AuthService auth = application.getContext().getAuthService();
        setBusy(true);
        application.runInBackground(() -> {
            try {
                return auth.authenticate(id, secret);
            } finally {
                Arrays.fill(secret, '\0');
            }
        }, this::loginChecked, this::loginFailed);
    }

    private void loginChecked(LoginResult result) {
        setBusy(false);
        if (welcomePage.getScene() == null) {
            // The app left the welcome page while the password was being checked.
            return;
        }
        if (result.getAccount().isPresent()) {
            application.loginSucceeded(result.getAccount().get());
            return;
        }
        showError(result.message(Instant.now()));
    }

    private void loginFailed(Throwable error) {
        setBusy(false);
        LOGGER.log(Level.SEVERE, "Login failed because of an error", error);
        showError("Could not log in: " + error.getMessage());
    }

    private void setBusy(boolean busy) {
        signInButton.setDisable(busy);
        signInButton.setGraphic(busy ? busyIndicator : null);
        createAccountButton.setDisable(busy);
        accountId.setDisable(busy);
        password.setDisable(busy);
    }

    private void showError(String text) {
        password.clear();
        Banners.show(errorMessage, text, Banners.Kind.DANGER);
        password.requestFocus();
    }

    /**
     * Opens the registration page.
     */
    @FXML
    private void registrationPressed() {
        if (!createAccountButton.isDisabled()) {
            application.gotoRegistration();
        }
    }
}
