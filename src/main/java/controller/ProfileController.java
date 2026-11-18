package controller;

import fxapp.Main;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.AnchorPane;
import model.Account;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Controls the profile page, where the logged-in user edits their details.
 */
public class ProfileController extends AnchorPane implements Initializable {

    @FXML
    private TextField accountType;
    @FXML
    private TextField user;
    @FXML
    private TextField fname;
    @FXML
    private TextField lname;
    @FXML
    private TextField email;
    @FXML
    private TextField phone;
    @FXML
    private TextArea address;
    @FXML
    private Label message;

    private Main application;

    /**
     * Sets up the profile page with the logged-in account's details.
     * @param application the main application
     */
    public void setApp(Main application) {
        this.application = application;
        Account loggedAccount = application.getLoggedAccount();
        accountType.setText(loggedAccount.getType().name());
        user.setText(loggedAccount.getId());
        fname.setText(loggedAccount.getFname());
        lname.setText(loggedAccount.getLname());
        email.setText(loggedAccount.getEmail());
        phone.setText(loggedAccount.getPhone());
        address.setText(loggedAccount.getAddress());
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
    }

    /**
     * Saves the profile. If any field is invalid nothing is saved and the error is shown.
     */
    @FXML
    private void savePressed() {
        try {
            application.getLoggedAccount().updateProfile(fname.getText(), lname.getText(),
                    email.getText(), phone.getText(), address.getText());
            message.setText("Profile updated");
        } catch (IllegalArgumentException e) {
            message.setText(e.getMessage());
        }
        message.setVisible(true);
    }

    /**
     * Goes back to the app view when the back button is pressed
     */
    @FXML
    private void backToRegPressed() {
        application.gotoApp();
    }

    /**
     * Goes back when ENTER is pressed on the back button
     * @param event the key event
     */
    @FXML
    private void backKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            backToRegPressed();
        }
    }

    /**
     * Saves when ENTER is pressed on the save button
     * @param event the key event
     */
    @FXML
    private void saveEntered(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            savePressed();
        }
    }
}
