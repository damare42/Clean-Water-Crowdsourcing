package controller;

import fxapp.Main;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import model.Account;
import model.AccountType;
import model.persistence.DataAccessException;

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
     * Registers a new account, or shows the first validation error.
     */
    @FXML
    private void registerPressed() {
        char[] secret = password.getText().toCharArray();
        char[] secretConfirm = passwordConfirm.getText().toCharArray();
        try {
            Account account = application.getContext().getAuthService().register(id.getText(),
                    accountTypeBox.getValue(), firstname.getText(), lastname.getText(),
                    email.getText(), emailConfirm.getText(), secret, secretConfirm);
            if (!application.registrationLogging(account.getId())) {
                showError(MSG_AUTO_LOGIN_FAILED);
            }
        } catch (IllegalArgumentException e) {
            showError(e.getMessage());
        } catch (DataAccessException e) {
            LOGGER.log(Level.SEVERE, "Registration failed because of a database error", e);
            showError("Could not save the account: " + e.getMessage());
        } finally {
            Arrays.fill(secret, '\0');
            Arrays.fill(secretConfirm, '\0');
        }
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
        application.accountLogout();
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
