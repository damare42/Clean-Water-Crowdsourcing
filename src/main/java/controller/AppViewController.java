package controller;

import fxapp.Main;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.web.WebView;
import model.Account;
import model.Permission;
import model.Report;
import model.map.MapMarker;
import model.persistence.DataAccessException;
import model.service.PermissionDeniedException;

import java.net.URL;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The main page: a map of the reports the account may see and links to the other
 * pages. Only the links the account's permissions allow are shown; the services check
 * the permissions again whatever the page shows.
 */
public class AppViewController implements Initializable, Page {
    private static final Logger LOGGER = Logger.getLogger(AppViewController.class.getName());

    @FXML
    private WebView webView;
    @FXML
    private Label welcome;
    @FXML
    private Hyperlink username;
    @FXML
    private Hyperlink createReport;
    @FXML
    private Hyperlink reportList;
    @FXML
    private Hyperlink createPurityReport;
    @FXML
    private Hyperlink purityList;
    @FXML
    private Hyperlink purityHistory;
    @FXML
    private Hyperlink manageAccounts;
    @FXML
    private Hyperlink securityLog;

    private Main application;
    /** Keeps the map, and with it the page's bridge object, reachable. */
    private LeafletMap map;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
    }

    /**
     * Sets up the app view and loads the reports onto the map.
     * @param application the main application of the app view
     */
    @Override
    public void setApp(Main application) {
        this.application = application;
        Account account = application.getLoggedAccount();
        username.setText(String.valueOf(account));
        show(createReport, account, Permission.SUBMIT_SOURCE_REPORT);
        show(reportList, account, Permission.VIEW_SOURCE_REPORTS);
        show(createPurityReport, account, Permission.SUBMIT_PURITY_REPORT);
        show(purityList, account, Permission.VIEW_PURITY_REPORTS);
        show(purityHistory, account, Permission.VIEW_PURITY_HISTORY);
        show(manageAccounts, account, Permission.MANAGE_USERS);
        show(securityLog, account, Permission.MANAGE_USERS);
        map = new LeafletMap(webView, new LeafletMap.Listener() {
            @Override
            public void detailsRequested(String kind, int id) {
                openReport(kind, id);
            }
        }, application::openExternalLink);
        showReports(account);
    }

    private static void show(Node link, Account account, Permission permission) {
        boolean allowed = account != null && account.hasPermission(permission);
        link.setVisible(allowed);
        link.setManaged(allowed);
    }

    private void showReports(Account account) {
        List<Report> reports = new ArrayList<>();
        try {
            if (account != null && account.hasPermission(Permission.VIEW_SOURCE_REPORTS)) {
                reports.addAll(application.getContext().getReportService().listReports(account));
            }
            if (account != null && account.hasPermission(Permission.VIEW_PURITY_REPORTS)) {
                reports.addAll(application.getContext().getPurityReportService().listReports(account));
            }
        } catch (PermissionDeniedException e) {
            LOGGER.log(Level.INFO, "Not showing some reports on the map: {0}", e.getMessage());
        } catch (DataAccessException e) {
            LOGGER.log(Level.SEVERE, "Could not load reports for the map", e);
        }
        map.showMarkers(MapMarker.forReports(reports, ZoneId.systemDefault()));
    }

    private void openReport(String kind, int number) {
        Account account = application.getLoggedAccount();
        try {
            if (MapMarker.KIND_SOURCE.equals(kind)) {
                application.getContext().getReportService().findReport(account, number)
                        .ifPresent(application::showReportDetails);
            } else if (MapMarker.KIND_PURITY.equals(kind)) {
                application.getContext().getPurityReportService().findReport(account, number)
                        .ifPresent(application::showReportDetails);
            }
        } catch (PermissionDeniedException | DataAccessException e) {
            LOGGER.log(Level.WARNING, "Could not open " + kind + " report " + number, e);
        }
    }

    /**
     * Centres the map on a report and opens its popup.
     * @param report the report to show
     */
    public void focusReport(Report report) {
        map.focusMarker(MapMarker.kindOf(report), report.getReportNumber());
    }

    /**
     * Logs out the user from the app
     */
    @FXML
    private void logoutPressed() {
        application.accountLogout();
    }

    /**
     * Opens the profile page
     */
    @FXML
    private void usernamePressed() {
        application.gotoProfile();
    }

    /**
     * Goes to the water source report form
     */
    @FXML
    private void createWaterSourceReportClicked() {
        application.gotoWaterSourceReport();
    }

    /**
     * Goes to the water source report list
     */
    @FXML
    private void reportListClicked() {
        application.gotoWSRViewer();
    }

    /**
     * Goes to the water purity report form
     */
    @FXML
    private void createPurityReportClicked() {
        application.gotoPurityReport();
    }

    /**
     * Goes to the water purity report list
     */
    @FXML
    private void purityListClicked() {
        application.gotoPurityList();
    }

    /**
     * Goes to the purity history chart
     */
    @FXML
    private void purityHistoryClicked() {
        application.gotoPurityHistory();
    }

    /**
     * Goes to the account management page
     */
    @FXML
    private void manageAccountsClicked() {
        application.gotoAccounts();
    }

    /**
     * Goes to the security log
     */
    @FXML
    private void securityLogClicked() {
        application.gotoSecurityLog();
    }
}
