package controller;

import fxapp.Main;
import javafx.beans.value.ChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
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
import model.geo.ApproximateLocation;
import model.geo.LocationSource;
import model.map.LocationJson;
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
    private FlowPane mapActions;
    @FXML
    private Button locateButton;
    @FXML
    private Button fitAllButton;
    @FXML
    private VBox locationChip;
    @FXML
    private Label locationLabel;
    @FXML
    private Label locationDisclosure;
    @FXML
    private Hyperlink locationChange;
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
        // The scene lives for the whole session: the listener is removed again when the
        // page leaves it, so a page that was navigated away from can be collected.
        mapPage.sceneProperty().addListener((observable, old, scene) -> {
            if (old != null) {
                old.widthProperty().removeListener(windowWidthListener);
            }
            if (scene != null) {
                setPanelShown(scene.getWidth() >= PANEL_MIN_WINDOW_WIDTH);
                scene.widthProperty().addListener(windowWidthListener);
            }
        });
    }

    /** Shows or hides the report list when the window crosses {@link #PANEL_MIN_WINDOW_WIDTH}. */
    private final ChangeListener<Number> windowWidthListener = (observable, old, width) -> {
        boolean wide = width.doubleValue() >= PANEL_MIN_WINDOW_WIDTH;
        if (wide != old.doubleValue() >= PANEL_MIN_WINDOW_WIDTH) {
            setPanelShown(wide);
        }
    };

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
        if (webView.getParent() instanceof Region mapArea) {
            mapArea.widthProperty().addListener((observable, old, width) -> fitChip());
        }
        application.mapPageShown(this);
    }

    /** The chip never gets narrower than this. */
    private static final double CHIP_MIN_WIDTH = 240;
    /** Room kept free on the left of the map for the legend and the offline notice. */
    private static final double CHIP_LEFT_CLEARANCE = 300;

    /**
     * Sizes the chip to its text, but never wider than the room right of the legend and
     * offline notice (it then wraps).
     */
    private void fitChip() {
        if (!(webView.getParent() instanceof Region mapArea)) {
            return;
        }
        locationChip.applyCss();
        Insets insets = locationChip.getInsets();
        double natural = Math.max(locationLabel.prefWidth(-1), locationChange.getParent().prefWidth(-1))
                + insets.getLeft() + insets.getRight() + 1;
        double room = Math.max(CHIP_MIN_WIDTH, mapArea.getWidth() - CHIP_LEFT_CLEARANCE);
        locationChip.setMaxWidth(Math.min(natural, room));
    }

    /**
     * Shows the approximate location of this computer: on the map (dot and accuracy
     * circle) and in the chip, as text in JavaFX labels only.
     *
     * @param location the location
     * @param recenter whether the map may move to it
     * @param providerHost the host of the IP lookup, named in the chip
     */
    public void showUserLocation(ApproximateLocation location, LeafletMap.Recenter recenter, String providerHost) {
        map.showUserLocation(LocationJson.toJson(location), recenter);
        if (location.source() == LocationSource.IP) {
            String place = location.placeLabel().isEmpty() ? "" : ": " + location.placeLabel();
            locationLabel.setText("Approximate location" + place + " (from your IP address)");
            locationDisclosure.setText("Looked up via " + providerHost + ", which receives your IP address.");
        } else {
            locationLabel.setText("Approximate location: " + location.zoneId() + " time zone");
            locationDisclosure.setText("Nothing leaves this computer.");
        }
        show(locationChip, true);
        fitChip();
        show(locateButton, true);
        updateActionsVisibility();
    }

    /**
     * Shows that locating is switched off: no location on the map, no Locate me.
     */
    public void showLocationOff() {
        map.clearUserLocation();
        locationLabel.setText("Location is off");
        locationDisclosure.setText("");
        show(locationChip, true);
        fitChip();
        show(locateButton, false);
        updateActionsVisibility();
    }

    /**
     * Shows no location (none is known yet, or none could be found).
     */
    public void hideUserLocation() {
        show(locationChip, false);
        show(locateButton, true);
        updateActionsVisibility();
    }

    /**
     * Shows that a forced lookup is running: Locate me is disabled with a spinner.
     * @param busy whether a lookup is running
     */
    public void setLocating(boolean busy) {
        locateButton.setDisable(busy);
        if (busy) {
            ProgressIndicator spinner = new ProgressIndicator();
            spinner.setPrefSize(16, 16);
            spinner.setMaxSize(16, 16);
            locateButton.setGraphic(spinner);
        } else {
            locateButton.setGraphic(new FontIcon("mdomz-my_location"));
        }
    }

    @FXML
    private void locatePressed() {
        application.locateMe();
    }

    @FXML
    private void fitAllPressed() {
        map.fitAll();
    }

    @FXML
    private void changeLocationPressed() {
        application.gotoLocationSettings();
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
        // One short line per value, so nothing has to be cut off in the narrow panel.
        List<String> details = purity
                ? List.of("Virus " + WaterPurityReport.formatPpm(((WaterPurityReport) report).getVirusPpm()) + " ppm",
                        "Contaminant " + WaterPurityReport.formatPpm(((WaterPurityReport) report).getContaminantPpm())
                                + " ppm")
                : List.of(((WaterSourceReport) report).getType().toString());
        String detail = String.join(", ", details);
        VBox what = new VBox(2);
        for (String line : details) {
            Label label = new Label(line);
            label.getStyleClass().add("report-detail");
            label.setWrapText(true);
            label.setMinWidth(0);
            label.setMinHeight(Region.USE_PREF_SIZE);
            what.getChildren().add(label);
        }
        what.setMinWidth(0);
        what.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(what, Priority.ALWAYS);
        HBox middle = new HBox(8, what, badge);
        middle.setAlignment(Pos.TOP_LEFT);
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
