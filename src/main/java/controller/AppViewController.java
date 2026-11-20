package controller;

import fxapp.Main;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
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
 * The main page: a map of the reports the account may see. The navigation to the other
 * pages is in the shell ({@link ShellController}).
 */
public class AppViewController implements Initializable, Page {
    private static final Logger LOGGER = Logger.getLogger(AppViewController.class.getName());

    @FXML
    private WebView webView;
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
        map = new LeafletMap(webView, new LeafletMap.Listener() {
            @Override
            public void detailsRequested(String kind, int id) {
                openReport(kind, id);
            }
        }, application::openExternalLink);
        showReports(account);
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
}
