package controller;

import atlantafx.base.controls.Notification;
import atlantafx.base.theme.Styles;
import fxapp.Main;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import model.Account;
import model.Permission;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Map;

/**
 * The frame around every signed-in page: the sidebar navigation, the user menu and the
 * content area with its toast layer. Only the navigation items the account's
 * permissions allow are shown; the services check the permissions again whatever the
 * sidebar shows.
 */
public class ShellController {
    private static final Duration TOAST_DURATION = Duration.seconds(4);
    /** Marks the nav item of the current page, which stays selected when clicked again. */
    private static final String KEEP_SELECTED = "cwc.keepSelected";

    @FXML
    private ToggleGroup nav;
    @FXML
    private ToggleButton mapNav;
    @FXML
    private ToggleButton createReport;
    @FXML
    private ToggleButton reportList;
    @FXML
    private ToggleButton createPurityReport;
    @FXML
    private ToggleButton purityList;
    @FXML
    private ToggleButton purityHistory;
    @FXML
    private Label adminSection;
    @FXML
    private ToggleButton manageAccounts;
    @FXML
    private ToggleButton securityLog;
    @FXML
    private MenuButton username;
    @FXML
    private Label initials;
    @FXML
    private Label fullName;
    @FXML
    private Label roleBadge;
    @FXML
    private StackPane content;
    @FXML
    private VBox toastLayer;

    private Main application;
    private Account account;
    private Map<String, ToggleButton> itemsByPage = Map.of();

    @FXML
    private void initialize() {
        // A nav item stays selected when it is clicked again.
        nav.selectedToggleProperty().addListener((observable, old, selected) -> {
            if (selected == null && old != null && old.getProperties().containsKey(KEEP_SELECTED)) {
                old.setSelected(true);
            }
        });
        roleBadge.setMinWidth(Label.USE_PREF_SIZE);
        itemsByPage = Map.of(
                Main.PAGE_MAP, mapNav,
                Main.PAGE_SOURCE_REPORT, createReport,
                Main.PAGE_SOURCE_LIST, reportList,
                Main.PAGE_PURITY_REPORT, createPurityReport,
                Main.PAGE_PURITY_LIST, purityList,
                Main.PAGE_PURITY_HISTORY, purityHistory,
                Main.PAGE_ACCOUNTS, manageAccounts,
                Main.PAGE_SECURITY_LOG, securityLog);
    }

    /**
     * Shows the account in the user menu and only the navigation it may use.
     * @param application the running application
     * @param account the signed-in account, or null
     */
    public void setUp(Main application, Account account) {
        this.application = application;
        this.account = account;
        show(mapNav, account != null);
        show(createReport, allowed(Permission.SUBMIT_SOURCE_REPORT));
        show(reportList, allowed(Permission.VIEW_SOURCE_REPORTS));
        show(createPurityReport, allowed(Permission.SUBMIT_PURITY_REPORT));
        show(purityList, allowed(Permission.VIEW_PURITY_REPORTS));
        show(purityHistory, allowed(Permission.VIEW_PURITY_HISTORY));
        show(adminSection, allowed(Permission.MANAGE_USERS));
        show(manageAccounts, allowed(Permission.MANAGE_USERS));
        show(securityLog, allowed(Permission.MANAGE_USERS));
        showAccount(account);
    }

    /**
     * Shows the account's name and role in the user menu (again, after a profile change).
     * @param account the signed-in account, or null
     */
    public void showAccount(Account account) {
        if (account == null) {
            username.setVisible(false);
            return;
        }
        String name = (account.getFname() + " " + account.getLname()).trim();
        fullName.setText(name.isEmpty() ? account.getId() : name);
        initials.setText(initialsOf(account));
        roleBadge.setText(account.getType().name());
        roleBadge.getStyleClass().setAll("label", "badge", Badges.roleStyle(account.getType()));
        username.setAccessibleText("Account menu for " + account.getId());
        username.setTooltip(new javafx.scene.control.Tooltip(fullName.getText() + " (@" + account.getId()
                + "): profile and log out"));
    }

    private boolean allowed(Permission permission) {
        return account != null && account.hasPermission(permission);
    }

    private static void show(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    static String initialsOf(Account account) {
        StringBuilder letters = new StringBuilder();
        for (String part : new String[]{account.getFname(), account.getLname()}) {
            if (part != null && !part.isBlank()) {
                letters.appendCodePoint(Character.toUpperCase(part.strip().codePointAt(0)));
            }
        }
        if (letters.length() == 0 && !account.getId().isEmpty()) {
            letters.appendCodePoint(Character.toUpperCase(account.getId().codePointAt(0)));
        }
        return letters.toString();
    }

    /**
     * @return the account this shell was set up for
     */
    public Account getAccount() {
        return account;
    }

    /**
     * Shows a page in the content area and highlights its navigation item, if it has one.
     * @param page the page's root
     * @param pageResource the page's FXML resource, one of the {@code Main.PAGE_*} names
     */
    public void showPage(Node page, String pageResource) {
        content.getChildren().setAll(page, toastLayer);
        ToggleButton item = itemsByPage.get(pageResource);
        for (Toggle toggle : nav.getToggles()) {
            toggle.getProperties().remove(KEEP_SELECTED);
        }
        if (item == null) {
            nav.selectToggle(null);
        } else {
            item.getProperties().put(KEEP_SELECTED, Boolean.TRUE);
            nav.selectToggle(item);
        }
    }

    /**
     * Shows a short confirmation in the top-right corner for a few seconds.
     * @param message the text to show
     */
    public void showToast(String message) {
        FontIcon icon = new FontIcon("mdoal-check_circle_outline");
        Notification toast = new Notification(message, icon);
        toast.getStyleClass().addAll(Styles.SUCCESS, Styles.ELEVATED_2);
        toast.setOnClose(event -> toastLayer.getChildren().remove(toast));
        toastLayer.getChildren().add(toast);
        PauseTransition hide = new PauseTransition(TOAST_DURATION);
        hide.setOnFinished(event -> toastLayer.getChildren().remove(toast));
        hide.play();
    }

    @FXML
    private void mapClicked() {
        application.gotoApp();
    }

    @FXML
    private void createWaterSourceReportClicked() {
        application.gotoWaterSourceReport();
    }

    @FXML
    private void reportListClicked() {
        application.gotoWSRViewer();
    }

    @FXML
    private void createPurityReportClicked() {
        application.gotoPurityReport();
    }

    @FXML
    private void purityListClicked() {
        application.gotoPurityList();
    }

    @FXML
    private void purityHistoryClicked() {
        application.gotoPurityHistory();
    }

    @FXML
    private void manageAccountsClicked() {
        application.gotoAccounts();
    }

    @FXML
    private void securityLogClicked() {
        application.gotoSecurityLog();
    }

    @FXML
    private void profilePressed() {
        application.gotoProfile();
    }

    @FXML
    private void logoutPressed() {
        application.accountLogout();
    }
}
