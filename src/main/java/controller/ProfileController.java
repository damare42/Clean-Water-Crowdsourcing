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
import javafx.scene.paint.Color;
import model.Account;
import model.persistence.DataAccessException;
import model.service.PermissionDeniedException;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controls the profile page, where the logged-in user edits their details.
 */
public class ProfileController extends AnchorPane implements Initializable {
    private static final Logger LOGGER = Logger.getLogger(ProfileController.class.getName());
    static final Color SUCCESS_COLOR = Color.web("#2e7d32");
    static final Color ERROR_COLOR = Color.web("#d32020");

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
            Account self = application.getLoggedAccount();
            application.getContext().getAccountService().updateProfile(self, self,
                    fname.getText(), lname.getText(), email.getText(), phone.getText(),
                    address.getText());
            showMessage("Profile updated", SUCCESS_COLOR);
        } catch (IllegalArgumentException | PermissionDeniedException e) {
            showMessage(e.getMessage(), ERROR_COLOR);
        } catch (DataAccessException e) {
            LOGGER.log(Level.SEVERE, "Could not save profile", e);
            showMessage("Could not save the profile: " + e.getMessage(), ERROR_COLOR);
        }
    }

    private void showMessage(String text, Color color) {
        message.setText(text);
        message.setTextFill(color);
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
            event.consume();
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
            event.consume();
            savePressed();
        }
    }
}
