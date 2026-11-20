package controller;

import fxapp.Main;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import model.GeoPoint;
import model.Permission;
import model.WaterPurityCondition;
import model.WaterPurityReport;
import model.WaterSourceReport;
import model.persistence.DataAccessException;
import model.service.PermissionDeniedException;

import java.net.URL;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.ResourceBundle;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A sortable table of all water purity reports, sorted by report number at first.
 * Double-click a row, or select it and press Details or ENTER, to see the report on
 * its own.
 */
public class PurityListController implements Initializable, Page {
    private static final Logger LOGGER = Logger.getLogger(PurityListController.class.getName());

    private final ZoneId zone = ZoneId.systemDefault();
    private Main application;

    @FXML
    private TableView<WaterPurityReport> reportTable;
    @FXML
    private TableColumn<WaterPurityReport, Integer> numberColumn;
    @FXML
    private TableColumn<WaterPurityReport, LocalDate> dateColumn;
    @FXML
    private TableColumn<WaterPurityReport, LocalTime> timeColumn;
    @FXML
    private TableColumn<WaterPurityReport, String> reporterColumn;
    @FXML
    private TableColumn<WaterPurityReport, WaterPurityCondition> conditionColumn;
    @FXML
    private TableColumn<WaterPurityReport, Double> virusColumn;
    @FXML
    private TableColumn<WaterPurityReport, Double> contaminantColumn;
    @FXML
    private TableColumn<WaterPurityReport, Double> latitudeColumn;
    @FXML
    private TableColumn<WaterPurityReport, Double> longitudeColumn;
    @FXML
    private Button detailsButton;
    @FXML
    private Label message;
    @FXML
    private Label countBadge;
    @FXML
    private Button newReportButton;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        Tables.column(numberColumn, WaterPurityReport::getReportNumber, Object::toString);
        Tables.column(dateColumn, r -> r.getReportedAt().atZone(zone).toLocalDate(),
                WaterSourceReport.DATE_FORMAT::format);
        Tables.column(timeColumn, r -> r.getReportedAt().atZone(zone).toLocalTime(),
                WaterSourceReport.TIME_FORMAT::format);
        Tables.column(reporterColumn, WaterPurityReport::getReporter, Function.identity());
        reporterColumn.setComparator(String.CASE_INSENSITIVE_ORDER);
        Tables.badgeColumn(conditionColumn, WaterPurityReport::getCondition, Enum::name, Badges::styleOf);
        conditionColumn.setComparator(Comparator.comparing(Enum::name));
        Tables.column(virusColumn, WaterPurityReport::getVirusPpm, WaterPurityReport::formatPpm);
        Tables.column(contaminantColumn, WaterPurityReport::getContaminantPpm, WaterPurityReport::formatPpm);
        Tables.column(latitudeColumn, r -> r.getLocation().latitude(), GeoPoint::format);
        Tables.column(longitudeColumn, r -> r.getLocation().longitude(), GeoPoint::format);

        reportTable.setRowFactory(table -> {
            TableRow<WaterPurityReport> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2
                        && !row.isEmpty()) {
                    application.showReportDetails(row.getItem());
                }
            });
            return row;
        });
        Tables.style(reportTable);
        detailsButton.disableProperty().bind(
                reportTable.getSelectionModel().selectedItemProperty().isNull());
    }

    /**
     * Loads the reports into the table.
     * @param application the main application
     */
    @Override
    public void setApp(Main application) {
        this.application = application;
        boolean mayReport = application.getLoggedAccount() != null
                && application.getLoggedAccount().hasPermission(Permission.SUBMIT_PURITY_REPORT);
        newReportButton.setVisible(mayReport);
        newReportButton.setManaged(mayReport);
        try {
            reportTable.setItems(FXCollections.observableArrayList(application.getContext()
                    .getPurityReportService().listReports(application.getLoggedAccount())));
            numberColumn.setSortType(TableColumn.SortType.ASCENDING);
            reportTable.getSortOrder().setAll(List.of(numberColumn));
            reportTable.sort();
            countBadge.setText(String.valueOf(reportTable.getItems().size()));
            countBadge.setVisible(true);
            countBadge.setManaged(true);
        } catch (PermissionDeniedException e) {
            showMessage(e.getMessage());
        } catch (DataAccessException e) {
            LOGGER.log(Level.SEVERE, "Could not load purity reports", e);
            showMessage("Could not load purity reports: " + e.getMessage());
        }
    }

    private void showMessage(String text) {
        Banners.show(message, text, Banners.Kind.DANGER);
    }

    /**
     * Shows the selected report on its own.
     */
    @FXML
    private void detailsPressed() {
        WaterPurityReport selected = reportTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            application.showReportDetails(selected);
        }
    }

    /**
     * Shows the selected report when ENTER is pressed in the table.
     * @param event the key event
     */
    @FXML
    private void tableKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            event.consume();
            detailsPressed();
        }
    }

    /**
     * Opens the form for a new report.
     */
    @FXML
    private void newReportPressed() {
        application.gotoPurityReport();
    }
}
