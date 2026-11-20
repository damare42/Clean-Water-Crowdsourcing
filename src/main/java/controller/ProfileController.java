package controller;

import fxapp.Main;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import model.Account;
import model.Validation;
import model.persistence.DataAccessException;
import model.service.PermissionDeniedException;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Controls the profile page, where the logged-in user edits their details.
 */
public class ProfileController implements Initializable, Page {
    private static final Logger LOGGER = Logger.getLogger(ProfileController.class.getName());

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
    @FXML
    private Label initials;
    @FXML
    private Label fullName;
    @FXML
    private Label roleBadge;
    @FXML
    private Label handle;

    private final FieldErrors fieldErrors = new FieldErrors();

    private Main application;

    /**
     * Sets up the profile page with the logged-in account's details.
     * @param application the main application
     */
    @Override
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
        showIdentity(loggedAccount);
    }

    private void showIdentity(Account account) {
        initials.setText(ShellController.initialsOf(account));
        String name = (account.getFname() + " " + account.getLname()).trim();
        fullName.setText(name.isEmpty() ? account.getId() : name);
        roleBadge.setText(account.getType().name());
        roleBadge.getStyleClass().setAll("label", "badge", Badges.roleStyle(account.getType()));
        roleBadge.setMinWidth(Label.USE_PREF_SIZE);
        handle.setText("@" + account.getId());
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        fieldErrors.on(Validation.MSG_INVALID_NAME, fname, lname)
                .on(Validation.MSG_INVALID_EMAIL, email);
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
            fieldErrors.clear();
            showMessage("Profile updated", Banners.Kind.SUCCESS);
            showIdentity(self);
            application.accountChanged();
        } catch (IllegalArgumentException | PermissionDeniedException e) {
            showMessage(e.getMessage(), Banners.Kind.DANGER);
            fieldErrors.show(e.getMessage());
        } catch (DataAccessException e) {
            LOGGER.log(Level.SEVERE, "Could not save profile", e);
            showMessage("Could not save the profile: " + e.getMessage(), Banners.Kind.DANGER);
        }
    }

    private void showMessage(String text, Banners.Kind kind) {
        Banners.show(message, text, kind);
    }

    /**
     * Goes back to the map without saving (the Cancel button)
     */
    @FXML
    private void backToRegPressed() {
        application.gotoApp();
    }

    /**
     * Goes back when ENTER is pressed on the Cancel button
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
