package controller;

import fxapp.Main;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import model.Account;
import model.AccountType;
import model.service.AuthService;

import java.net.URL;
import java.util.Arrays;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by Sheng on 9/19/16.
 * Controls the registration page.
 */
public class RegistrationController extends AnchorPane implements Initializable {
    private static final Logger LOGGER = Logger.getLogger(RegistrationController.class.getName());
    static final String MSG_AUTO_LOGIN_FAILED =
            "Your account was created, but you could not be logged in. Please log in.";

    private Main application;
    @FXML
    private TextField id;
    @FXML
    private TextField firstname;
    @FXML
    private TextField lastname;
    @FXML
    private TextField email;
    @FXML
    private TextField emailConfirm;
    @FXML
    private PasswordField password;
    @FXML
    private PasswordField passwordConfirm;
    @FXML
    private Label errorMessage;
    @FXML
    private ComboBox<AccountType> accountTypeBox;
    @FXML
    private Button registerButton;
    @FXML
    private Button backButton;

    private final ObservableList<AccountType> accountTypeList =
            FXCollections.observableArrayList(AccountType.selfRegistrable());

    /**
     * Setup the main application link so we can call methods there
     * @param application the reference to the FX Application instance
     */
    public void setApp(Main application) {
        this.application = application;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        accountTypeBox.setItems(accountTypeList);
    }

    /**
     * Registers a new account, or shows the first validation error. The password is
     * hashed on a background thread; the buttons are disabled meanwhile.
     */
    @FXML
    private void registerPressed() {
        if (registerButton.isDisabled()) {
            return;
        }
        String userId = id.getText();
        AccountType type = accountTypeBox.getValue();
        String first = firstname.getText();
        String last = lastname.getText();
        String mail = email.getText();
        String mailConfirm = emailConfirm.getText();
        char[] secret = password.getText().toCharArray();
        char[] secretConfirm = passwordConfirm.getText().toCharArray();
        AuthService auth = application.getContext().getAuthService();
        setBusy(true);
        application.runInBackground(() -> {
            try {
                return auth.register(userId, type, first, last, mail, mailConfirm, secret, secretConfirm);
            } finally {
                Arrays.fill(secret, '\0');
                Arrays.fill(secretConfirm, '\0');
            }
        }, this::registered, this::registrationFailed);
    }

    private void registered(Account account) {
        setBusy(false);
        if (!application.registrationLogging(account.getId())) {
            showError(MSG_AUTO_LOGIN_FAILED);
        }
    }

    private void registrationFailed(Throwable error) {
        setBusy(false);
        if (error instanceof IllegalArgumentException) {
            showError(error.getMessage());
            return;
        }
        LOGGER.log(Level.SEVERE, "Registration failed because of an error", error);
        showError("Could not save the account: " + error.getMessage());
    }

    private void setBusy(boolean busy) {
        registerButton.setDisable(busy);
        backButton.setDisable(busy);
    }

    private void showError(String text) {
        errorMessage.setText(text);
        errorMessage.setVisible(true);
    }

    /**
     * Registers when ENTER is pressed
     * @param event the key event
     */
    @FXML
    private void registerEntered(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            // Consume so the page-wide handler does not register a second time.
            event.consume();
            registerPressed();
        }
    }

    /**
     * Goes back to the welcome page
     */
    @FXML
    private void backPressed() {
        if (!backButton.isDisabled()) {
            application.accountLogout();
        }
    }

    /**
     * Goes back to the welcome page when ENTER is pressed
     * @param event the key event
     */
    @FXML
    private void backKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            // Consume so the page-wide ENTER handler does not register.
            event.consume();
            backPressed();
        }
    }
}
