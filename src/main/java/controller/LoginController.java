package controller;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import model.persistence.DataAccessException;

import java.net.URL;
import java.util.Arrays;
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
    private static final String MSG_INVALID_LOGIN = "Invalid log in. Please try again";
    private Stage dialogStage;
    private boolean confirmLogin;
    @FXML
    private TextField accountId;
    @FXML
    private PasswordField password;
    @FXML
    private Label errorMessage;

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
     * Becomes active when ok is pressed in the login page.
     */
    @FXML
    private void handleOKPressed() {
        char[] secret = password.getText().toCharArray();
        try {
            if (application.accountLogging(accountId.getText(), secret)) {
                confirmLogin = true;
                dialogStage.close();
                return;
            }
            errorMessage.setText(MSG_INVALID_LOGIN);
        } catch (DataAccessException e) {
            LOGGER.log(Level.SEVERE, "Login failed because of a database error", e);
            errorMessage.setText("Could not log in: " + e.getMessage());
        } finally {
            Arrays.fill(secret, '\0');
        }
        password.clear();
        errorMessage.setVisible(true);
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
            dialogStage.close();
        }
    }
}