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
import model.Authenticator;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Created by Sheng on 9/19/16.
 * Controls the registration page.
 */
public class RegistrationController extends AnchorPane implements Initializable {
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

    private final ObservableList<AccountType> accountTypeList = FXCollections.observableArrayList(
            AccountType.User, AccountType.Worker, AccountType.Manager);

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
        try {
            Account account = Authenticator.register(id.getText(), accountTypeBox.getValue(),
                    firstname.getText(), lastname.getText(),
                    email.getText(), emailConfirm.getText(),
                    password.getText(), passwordConfirm.getText());
            application.registrationLogging(account.getId());
        } catch (IllegalArgumentException e) {
            errorMessage.setText(e.getMessage());
            errorMessage.setVisible(true);
        }
    }

    /**
     * Registers when ENTER is pressed
     * @param event the key event
     */
    @FXML
    private void registerEntered(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
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
            backPressed();
        }
    }
}
