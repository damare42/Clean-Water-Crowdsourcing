package controller;

import fxapp.Main;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import model.Account;
import model.Validation;
import model.geo.LocationMode;
import model.geo.LocationService;
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

    @FXML
    private ScrollPane profileScroll;
    @FXML
    private VBox privacySection;
    @FXML
    private ToggleGroup locationModeGroup;
    @FXML
    private RadioButton locationModeIp;
    @FXML
    private RadioButton locationModeTimeZone;
    @FXML
    private RadioButton locationModeOff;
    @FXML
    private Label locationIpNote;

    private final FieldErrors fieldErrors = new FieldErrors();
    /** True while the page selects the stored mode, which must not count as a change. */
    private boolean showingMode;

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
        showLocationSetting();
    }

    /**
     * Shows the location setting of this installation. A change is saved at once (it
     * is a setting of the computer, not part of the profile).
     */
    private void showLocationSetting() {
        LocationService locations = application.getContext().getLocationService();
        String host = locations.ipLookupAvailable() ? locations.providerHost() : "";
        locationModeIp.setText(host.isEmpty() ? "Look up using my internet address"
                : "Look up using my internet address \u2013 sends your IP address to " + host);
        boolean ipAvailable = locations.ipLookupAvailable();
        locationModeIp.setDisable(!ipAvailable);
        locationIpNote.setVisible(!ipAvailable);
        locationIpNote.setManaged(!ipAvailable);
        showingMode = true;
        locationModeGroup.selectToggle(switch (locations.effectiveMode()) {
            case IP_LOOKUP -> locationModeIp;
            case TIME_ZONE -> locationModeTimeZone;
            case OFF -> locationModeOff;
        });
        showingMode = false;
        locationModeGroup.selectedToggleProperty().addListener((observable, old, selected) -> {
            if (selected == null) {
                // A radio group always has a choice: keep the old one.
                old.setSelected(true);
            } else if (!showingMode) {
                saveLocationMode(selected == locationModeIp ? LocationMode.IP_LOOKUP
                        : selected == locationModeTimeZone ? LocationMode.TIME_ZONE : LocationMode.OFF);
            }
        });
    }

    private void saveLocationMode(LocationMode mode) {
        try {
            application.getContext().getLocationService().setMode(application.getLoggedAccount(), mode);
            application.locationSettingChanged();
            application.showToast("Location setting saved");
        } catch (DataAccessException e) {
            LOGGER.log(Level.SEVERE, "Could not save the location setting", e);
            showMessage("Could not save the location setting: " + e.getMessage(), Banners.Kind.DANGER);
        }
    }

    /**
     * Scrolls to the Privacy section and puts the keyboard focus on the chosen option.
     */
    public void showPrivacy() {
        profileScroll.setVvalue(1.0);
        if (locationModeGroup.getSelectedToggle() instanceof RadioButton selected) {
            selected.requestFocus();
        }
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
