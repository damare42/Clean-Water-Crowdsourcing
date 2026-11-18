package controller;

import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import model.service.AuthService;
import model.service.LoginResult;

import java.net.URL;
import java.util.Arrays;
import java.time.Instant;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import fxapp.Main;
import javafx.stage.Stage;

/**
 * Controls the functionality of the login page
 */
public class LoginController extends AnchorPane implements Initializable {
    private static final Logger LOGGER = Logger.getLogger(LoginController.class.getName());
    private Stage dialogStage;
    private boolean confirmLogin;
    @FXML
    private TextField accountId;
    @FXML
    private PasswordField password;
    @FXML
    private Label errorMessage;
    @FXML
    private Button signInButton;
    @FXML
    private Button cancelButton;

    private Main application;

    /**
     * Sets up the login page
     * @param application the main application of the login page
     */
    public void setApp(Main application) { this.application = application;}

    @Override
    public void initialize(URL location, ResourceBundle resources) {
    }

    /**
     * Becomes active when is cancel is clicked at the login page
     */
    @FXML
    private void loginCancelPressed() {
        dialogStage.close();
    }

    /**
     * Sets the stage of this dialog.
     *
     * @param dialogStage the stage for this dialog
     */
    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    /**
     * Returns true if the user clicked OK, false otherwise.
     *
     * @return  true if the user clicked the OK button
     */
    public boolean confirmLoginClicked() {
        return confirmLogin;
    }

    /**
     * Becomes active when ok is pressed in the login page. The password is checked on
     * a background thread (hashing takes a moment); the buttons are disabled meanwhile.
     * A refused login shows why: invalid id or password, banned account (only after
     * a correct password) or too many failed attempts.
     */
    @FXML
    private void handleOKPressed() {
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
        if (!dialogStage.isShowing()) {
            // The dialog was closed while the password was being checked.
            return;
        }
        if (result.getAccount().isPresent()) {
            confirmLogin = true;
            application.loginSucceeded(result.getAccount().get());
            dialogStage.close();
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
        cancelButton.setDisable(busy);
        accountId.setDisable(busy);
        password.setDisable(busy);
    }

    private void showError(String text) {
        password.clear();
        errorMessage.setText(text);
        errorMessage.setVisible(true);
        password.requestFocus();
    }

    /**
     * Becomes active when the login key pressed
     * @param event the event when ENTER is pressed
     */
    @FXML
    private void isKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            event.consume();
            handleOKPressed();
        }
    }
    /**
     * Closed the screen if cancel key is entered
     * @param event the event started when the key is pressed
     */
    @FXML
    private void isKeyCanceled(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            event.consume();
            if (!cancelButton.isDisabled()) {
                dialogStage.close();
            }
        }
    }
}