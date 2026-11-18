package controller;

import fxapp.Main;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.web.WebView;
import model.Account;
import model.Permission;
import model.WaterSourceReport;
import model.map.MapMarker;
import model.persistence.DataAccessException;
import model.service.PermissionDeniedException;

import java.net.URL;
import java.time.ZoneId;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The main page: a map of all water source reports and links to the other pages.
 */
public class AppViewController implements Initializable {
    private static final Logger LOGGER = Logger.getLogger(AppViewController.class.getName());

    @FXML
    private WebView webView;
    @FXML
    private Label welcome;
    @FXML
    private Label username;
    @FXML
    private Label createReport;

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
    public void setApp(Main application){
        this.application = application;
        Account account = application.getLoggedAccount();
        username.setText(String.valueOf(account));
        boolean maySubmit = account != null && account.hasPermission(Permission.SUBMIT_SOURCE_REPORT);
        createReport.setVisible(maySubmit);
        createReport.setManaged(maySubmit);
        map = new LeafletMap(webView, new LeafletMap.Listener() {
            @Override
            public void detailsRequested(String kind, int id) {
                openReport(kind, id);
            }
        }, application::openExternalLink);
        showReports();
    }

    private void showReports() {
        try {
            List<WaterSourceReport> reports = application.getContext().getReportService()
                    .listReports(application.getLoggedAccount());
            map.showMarkers(MapMarker.forSourceReports(reports, ZoneId.systemDefault()));
        } catch (PermissionDeniedException e) {
            LOGGER.log(Level.INFO, "Not showing reports on the map: {0}", e.getMessage());
        } catch (DataAccessException e) {
            LOGGER.log(Level.SEVERE, "Could not load reports for the map", e);
        }
    }

    private void openReport(String kind, int number) {
        if (!MapMarker.KIND_SOURCE.equals(kind)) {
            return;
        }
        try {
            application.getContext().getReportService()
                    .findReport(application.getLoggedAccount(), number)
                    .ifPresent(application::showReportDetails);
        } catch (PermissionDeniedException | DataAccessException e) {
            LOGGER.log(Level.WARNING, "Could not open report " + number, e);
        }
    }

    /**
     * Centres the map on a report and opens its popup.
     * @param report the report to show
     */
    public void focusReport(WaterSourceReport report) {
        map.focusMarker(MapMarker.KIND_SOURCE, report.getReportNumber());
    }

    /**
     * Logs out the user from the app
     */
    @FXML
    private void logoutPressed() {
        application.accountLogout();
    }

    /**
     * Activates the username is pressed
     */
    @FXML
    private void usernamePressed() {
        application.gotoProfile();
    }

    /**
     * Goes to the water source report creation pation
     */
    @FXML
    private void createWaterSourceReportClicked() {
        application.gotoWaterSourceReport();
    }

    /**
     * activates when the Reports List is clicked
     */
    @FXML
    private void reportListClicked() {
        application.gotoWSRViewer();
    }
}
