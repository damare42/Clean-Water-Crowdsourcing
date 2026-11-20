package controller;

import fxapp.Main;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import model.Account;
import model.Permission;
import model.Report;
import model.WaterPurityReport;
import model.WaterSourceReport;
import model.map.MapMarker;
import model.persistence.DataAccessException;
import model.service.PermissionDeniedException;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The main page: a map of the reports the account may see, a list of the same reports
 * as cards beside it, and buttons for the reports the account may file. The navigation
 * to the other pages is in the shell ({@link ShellController}); the services check the
 * permissions again whatever the page shows. Report data is shown with labels only
 * (never as HTML).
 */
public class AppViewController implements Initializable, Page {
    private static final Logger LOGGER = Logger.getLogger(AppViewController.class.getName());

    /** Below this window width the report list starts hidden. */
    static final double PANEL_MIN_WINDOW_WIDTH = 1100;

    @FXML
    private BorderPane mapPage;
    @FXML
    private WebView webView;
    @FXML
    private HBox mapActions;
    @FXML
    private Button reportSourceButton;
    @FXML
    private Button reportPurityButton;
    @FXML
    private Button showPanelButton;
    @FXML
    private VBox reportPanel;
    @FXML
    private Label reportCount;
    @FXML
    private ScrollPane reportScroll;
    @FXML
    private VBox reportCards;
    @FXML
    private VBox emptyState;
    @FXML
    private Label emptyText;
    @FXML
    private Button emptyReportButton;

    private Main application;
    private Button currentCard;
    /** Keeps the map, and with it the page's bridge object, reachable. */
    private LeafletMap map;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Start with the list hidden in a narrow window, and follow the window across the
        // threshold afterwards (the user can still open or close it at any width).
        mapPage.sceneProperty().addListener((observable, old, scene) -> {
            if (scene != null) {
                watchWidth(scene);
            }
        });
    }

    private void watchWidth(Scene scene) {
        setPanelShown(scene.getWidth() >= PANEL_MIN_WINDOW_WIDTH);
        scene.widthProperty().addListener((observable, old, width) -> {
            boolean wide = width.doubleValue() >= PANEL_MIN_WINDOW_WIDTH;
            if (wide != old.doubleValue() >= PANEL_MIN_WINDOW_WIDTH) {
                setPanelShown(wide);
            }
        });
    }

    private void setPanelShown(boolean shown) {
        reportPanel.setVisible(shown);
        reportPanel.setManaged(shown);
        showPanelButton.setVisible(!shown);
        showPanelButton.setManaged(!shown);
        updateActionsVisibility();
    }

    private void updateActionsVisibility() {
        boolean any = mapActions.getChildren().stream().anyMatch(node -> node.isManaged());
        mapActions.setVisible(any);
    }

    @FXML
    private void togglePanel() {
        setPanelShown(!reportPanel.isVisible());
        (reportPanel.isVisible() ? reportCards : webView).requestFocus();
    }

    /**
     * Sets up the app view and loads the reports onto the map.
     * @param application the main application of the app view
     */
    @Override
    public void setApp(Main application) {
        this.application = application;
        Account account = application.getLoggedAccount();
        boolean submitSource = account != null && account.hasPermission(Permission.SUBMIT_SOURCE_REPORT);
        show(reportSourceButton, submitSource);
        show(emptyReportButton, submitSource);
        show(reportPurityButton, account != null && account.hasPermission(Permission.SUBMIT_PURITY_REPORT));
        updateActionsVisibility();
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
        showCards(reports);
    }

    private static void show(Region node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }

    /** Fills the report list, newest first. */
    private void showCards(List<Report> reports) {
        long sources = reports.stream().filter(r -> r instanceof WaterSourceReport).count();
        long purity = reports.size() - sources;
        List<String> counts = new ArrayList<>();
        counts.add(sources + (sources == 1 ? " water source" : " water sources"));
        if (purity > 0 || application.getLoggedAccount() != null
                && application.getLoggedAccount().hasPermission(Permission.VIEW_PURITY_REPORTS)) {
            counts.add(purity + " purity");
        }
        reportCount.setText(String.join(" \u00b7 ", counts));
        List<Report> sorted = new ArrayList<>(reports);
        sorted.sort(Comparator.comparing(Report::getReportedAt).reversed()
                .thenComparing(Comparator.comparing(Report::getReportNumber).reversed()));
        reportCards.getChildren().clear();
        sorted.forEach(report -> reportCards.getChildren().add(card(report)));
        boolean empty = reports.isEmpty();
        show(emptyState, empty);
        show(reportScroll, !empty);
        if (empty && !emptyReportButton.isManaged()) {
            emptyText.setText("Reports you can see will appear here and on the map.");
        } else if (empty) {
            emptyText.setText("Be the first to report a water source near you.");
        }
    }

    /**
     * A card for one report. Click (or Space) shows it on the map; double-click or ENTER
     * opens its details.
     */
    private Button card(Report report) {
        ZoneId zone = ZoneId.systemDefault();
        String kind = MapMarker.kindOf(report);
        boolean purity = report instanceof WaterPurityReport;
        Object condition = purity ? ((WaterPurityReport) report).getCondition()
                : ((WaterSourceReport) report).getCondition();
        FontIcon icon = new FontIcon(purity ? "mdomz-science" : "mdomz-opacity");
        icon.getStyleClass().add("report-icon");
        Label title = new Label(report.getTitle());
        title.getStyleClass().add("report-title");
        title.setMinWidth(0);
        Label badge = Badges.badge(condition.toString(), Badges.styleOf(condition));
        HBox top = new HBox(8, icon, title);
        top.setAlignment(Pos.CENTER_LEFT);
        String detail = purity
                ? "Virus " + WaterPurityReport.formatPpm(((WaterPurityReport) report).getVirusPpm())
                + " ppm \u00b7 Contaminant "
                + WaterPurityReport.formatPpm(((WaterPurityReport) report).getContaminantPpm()) + " ppm"
                : ((WaterSourceReport) report).getType().toString();
        Label what = new Label(detail);
        what.getStyleClass().add("report-detail");
        what.setMinWidth(0);
        what.setWrapText(true);
        what.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(what, Priority.ALWAYS);
        HBox middle = new HBox(8, what, badge);
        middle.setAlignment(Pos.CENTER_LEFT);
        Label when = new Label(WaterSourceReport.DATE_FORMAT.format(report.getReportedAt().atZone(zone))
                + " " + WaterSourceReport.TIME_FORMAT.format(report.getReportedAt().atZone(zone))
                + " \u00b7 " + report.getReporter());
        when.getStyleClass().add("report-meta");
        VBox body = new VBox(4, top, middle, when);
        body.setFillWidth(true);
        Button card = new Button(report.getTitle(), body);
        card.getStyleClass().add("report-card");
        card.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setMnemonicParsing(false);
        card.setAccessibleText(report.getTitle() + ", " + condition + ", " + detail + ". Press Enter for details.");
        card.setOnAction(event -> select(card, kind, report.getReportNumber()));
        card.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                openReport(kind, report.getReportNumber());
            }
        });
        card.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                event.consume();
                select(card, kind, report.getReportNumber());
                openReport(kind, report.getReportNumber());
            }
        });
        body.prefWidthProperty().bind(card.widthProperty().subtract(26));
        return card;
    }

    private void select(Button card, String kind, int number) {
        if (currentCard != null) {
            currentCard.getStyleClass().remove("current");
        }
        currentCard = card;
        card.getStyleClass().add("current");
        map.focusMarker(kind, number);
    }

    @FXML
    private void createWaterSourceReportClicked() {
        application.gotoWaterSourceReport();
    }

    @FXML
    private void createPurityReportClicked() {
        application.gotoPurityReport();
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
