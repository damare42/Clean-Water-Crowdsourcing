package controller;

import atlantafx.base.theme.Styles;
import fxapp.Main;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.TableCell;
import fxapp.Theme;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import model.Account;
import model.AccountSummary;
import model.AccountType;
import model.Validation;
import model.persistence.DataAccessException;
import model.service.AccountService;
import model.service.PermissionDeniedException;

import java.net.URL;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The administrator's account list: ban, unban and delete accounts. Administrators
 * cannot ban or delete themselves (the buttons are disabled for their own row, and
 * the service refuses it anyway). Deleting asks for confirmation first.
 */
public class AdminAccountsController implements Initializable, Page {
    private static final Logger LOGGER = Logger.getLogger(AdminAccountsController.class.getName());

    @FXML
    private TableView<AccountSummary> accountTable;
    @FXML
    private TableColumn<AccountSummary, String> idColumn;
    @FXML
    private TableColumn<AccountSummary, AccountType> typeColumn;
    @FXML
    private TableColumn<AccountSummary, String> firstNameColumn;
    @FXML
    private TableColumn<AccountSummary, String> lastNameColumn;
    @FXML
    private TableColumn<AccountSummary, String> emailColumn;
    @FXML
    private TableColumn<AccountSummary, String> statusColumn;
    @FXML
    private Button banButton;
    @FXML
    private Button unbanButton;
    @FXML
    private Button deleteButton;
    @FXML
    private Label message;

    private Main application;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        Tables.column(idColumn, AccountSummary::id, Function.identity());
        idColumn.setComparator(String.CASE_INSENSITIVE_ORDER);
        Tables.badgeColumn(typeColumn, AccountSummary::type, Enum::name, Badges::roleStyle);
        typeColumn.setComparator(Comparator.comparing(Enum::name));
        Tables.column(firstNameColumn, AccountSummary::firstName, Function.identity());
        Tables.column(lastNameColumn, AccountSummary::lastName, Function.identity());
        Tables.column(emailColumn, AccountSummary::email, Function.identity());
        statusColumn.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue().status()));
        statusColumn.setCellFactory(column -> new TableCell<>() {
            private final Label badge = Badges.badge("", Badges.NEUTRAL);

            {
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }

            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                AccountSummary row = empty || getTableRow() == null ? null : getTableRow().getItem();
                if (row == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                badge.setText(status);
                badge.getStyleClass().setAll("label", Badges.BADGE, Badges.statusStyle(row.banned()));
                setText(status);
                setGraphic(badge);
            }
        });
        Tables.style(accountTable);
    }

    @Override
    public void setApp(Main application) {
        this.application = application;
        var selected = accountTable.getSelectionModel().selectedItemProperty();
        banButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> selected.get() == null || isSelf(selected.get()) || selected.get().banned(), selected));
        unbanButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> selected.get() == null || isSelf(selected.get()) || !selected.get().banned(), selected));
        deleteButton.disableProperty().bind(Bindings.createBooleanBinding(
                () -> selected.get() == null || isSelf(selected.get()), selected));
        reload(null);
    }

    private boolean isSelf(AccountSummary summary) {
        Account me = application.getLoggedAccount();
        return me != null && me.getIdKey().equals(Validation.idKey(summary.id()));
    }

    /**
     * Reloads the table and selects the row with the given id, if any.
     */
    private void reload(String selectId) {
        try {
            List<AccountSummary> rows = service().listAccounts(application.getLoggedAccount());
            accountTable.setItems(FXCollections.observableArrayList(rows));
            if (selectId != null) {
                rows.stream().filter(r -> r.id().equals(selectId)).findFirst()
                        .ifPresent(r -> accountTable.getSelectionModel().select(r));
            }
        } catch (PermissionDeniedException e) {
            showMessage(e.getMessage(), Banners.Kind.DANGER);
        } catch (DataAccessException e) {
            LOGGER.log(Level.SEVERE, "Could not load accounts", e);
            showMessage("Could not load accounts: " + e.getMessage(), Banners.Kind.DANGER);
        }
    }

    private AccountService service() {
        return application.getContext().getAccountService();
    }

    /**
     * Bans the selected account.
     */
    @FXML
    private void banPressed() {
        setBanned(true);
    }

    /**
     * Lifts the ban on the selected account.
     */
    @FXML
    private void unbanPressed() {
        setBanned(false);
    }

    private void setBanned(boolean banned) {
        AccountSummary selected = accountTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        try {
            service().setBanned(application.getLoggedAccount(), selected.id(), banned);
            showMessage((banned ? "Banned " : "Unbanned ") + selected.id(), Banners.Kind.SUCCESS);
        } catch (IllegalArgumentException | PermissionDeniedException e) {
            showMessage(e.getMessage(), Banners.Kind.DANGER);
        } catch (DataAccessException e) {
            LOGGER.log(Level.SEVERE, "Could not change the account", e);
            showMessage("Could not change the account: " + e.getMessage(), Banners.Kind.DANGER);
        }
        reload(selected.id());
    }

    /**
     * Deletes the selected account after asking for confirmation.
     */
    @FXML
    private void deletePressed() {
        AccountSummary selected = accountTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete the account " + selected.id() + "? Its reports are kept.",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setTitle("Delete account");
        confirm.setHeaderText("Delete account");
        confirm.initOwner(accountTable.getScene().getWindow());
        Theme.style(confirm);
        Button ok = (Button) confirm.getDialogPane().lookupButton(ButtonType.OK);
        ok.setText("Delete");
        ok.getStyleClass().add(Styles.DANGER);
        // Nothing is the default: ENTER must not delete. ESC cancels.
        ok.setDefaultButton(false);
        Optional<ButtonType> answer = confirm.showAndWait();
        if (answer.isEmpty() || answer.get() != ButtonType.OK) {
            return;
        }
        try {
            service().deleteAccount(application.getLoggedAccount(), selected.id());
            showMessage("Deleted " + selected.id(), Banners.Kind.SUCCESS);
        } catch (IllegalArgumentException | PermissionDeniedException e) {
            showMessage(e.getMessage(), Banners.Kind.DANGER);
        } catch (DataAccessException e) {
            LOGGER.log(Level.SEVERE, "Could not delete the account", e);
            showMessage("Could not delete the account: " + e.getMessage(), Banners.Kind.DANGER);
        }
        reload(null);
    }

    private void showMessage(String text, Banners.Kind kind) {
        Banners.show(message, text, kind);
    }
}
