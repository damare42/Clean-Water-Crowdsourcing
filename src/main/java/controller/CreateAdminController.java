package controller;

import fxapp.Main;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import model.Account;
import model.service.AuthService;

import java.net.URL;
import java.util.Arrays;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The start-up dialog that creates the first administrator account. It is only shown
 * when the app was started with the {@code cwc.bootstrapAdmin} system property and no
 * administrator exists (see {@link Main}); the id comes from that property and
 * cannot be changed here. The password is hashed on a background thread.
 */
public class CreateAdminController implements Initializable {
    private static final Logger LOGGER = Logger.getLogger(CreateAdminController.class.getName());

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
    private Button createButton;
    @FXML
    private Button cancelButton;

    private Main application;
    private Stage dialogStage;
    private Account created;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
    }

    /**
     * @param application the running application
     * @param dialogStage the window this controller is shown in
     * @param adminId the id of the administrator to create
     */
    public void setUp(Main application, Stage dialogStage, String adminId) {
        this.application = application;
        this.dialogStage = dialogStage;
        id.setText(adminId);
    }

    /**
     * @return the created administrator, or empty if the dialog was cancelled
     */
    public Optional<Account> getCreated() {
        return Optional.ofNullable(created);
    }

    /**
     * Creates the administrator, or shows the first validation error.
     */
    @FXML
    private void createPressed() {
        if (createButton.isDisabled()) {
            return;
        }
        String adminId = id.getText();
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
                return auth.createFirstAdministrator(adminId, first, last, mail, mailConfirm,
                        secret, secretConfirm);
            } finally {
                Arrays.fill(secret, '\0');
                Arrays.fill(secretConfirm, '\0');
            }
        }, this::createdAdmin, this::creationFailed);
    }

    private void createdAdmin(Account admin) {
        setBusy(false);
        created = admin;
        dialogStage.close();
    }

    private void creationFailed(Throwable error) {
        setBusy(false);
        password.clear();
        passwordConfirm.clear();
        if (error instanceof IllegalArgumentException) {
            showError(error.getMessage());
            return;
        }
        LOGGER.log(Level.SEVERE, "Could not create the administrator", error);
        showError("Could not create the administrator: " + error.getMessage());
    }

    private void setBusy(boolean busy) {
        createButton.setDisable(busy);
        cancelButton.setDisable(busy);
    }

    private void showError(String text) {
        errorMessage.setText(text);
        errorMessage.setVisible(true);
    }

    /**
     * Closes the dialog without creating an administrator.
     */
    @FXML
    private void cancelPressed() {
        if (!cancelButton.isDisabled()) {
            dialogStage.close();
        }
    }
}
