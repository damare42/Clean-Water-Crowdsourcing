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
import javafx.scene.layout.AnchorPane;
import model.GeoPoint;
import model.WaterSourceCondition;
import model.WaterSourceReport;
import model.WaterSourceType;
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
 * Created by Sean on 10/11/2016.
 * The report list: a sortable table of all water source reports, sorted by report
 * number at first. Double-click a row, or select it and press Details, to see the
 * report on its own.
 */
public class WSRViewerController extends AnchorPane implements Initializable, Page {
    private static final Logger LOGGER = Logger.getLogger(WSRViewerController.class.getName());

    private Main application;
    private final ZoneId zone = ZoneId.systemDefault();

    @FXML
    private TableView<WaterSourceReport> reportTable;
    @FXML
    private TableColumn<WaterSourceReport, Integer> numberColumn;
    @FXML
    private TableColumn<WaterSourceReport, LocalDate> dateColumn;
    @FXML
    private TableColumn<WaterSourceReport, LocalTime> timeColumn;
    @FXML
    private TableColumn<WaterSourceReport, String> reporterColumn;
    @FXML
    private TableColumn<WaterSourceReport, WaterSourceType> typeColumn;
    @FXML
    private TableColumn<WaterSourceReport, WaterSourceCondition> conditionColumn;
    @FXML
    private TableColumn<WaterSourceReport, Double> latitudeColumn;
    @FXML
    private TableColumn<WaterSourceReport, Double> longitudeColumn;
    @FXML
    private Button detailsButton;
    @FXML
    private Label message;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        Tables.column(numberColumn, WaterSourceReport::getReportNumber, Object::toString);
        Tables.column(dateColumn, r -> r.getReportedAt().atZone(zone).toLocalDate(),
                WaterSourceReport.DATE_FORMAT::format);
        Tables.column(timeColumn, r -> r.getReportedAt().atZone(zone).toLocalTime(),
                WaterSourceReport.TIME_FORMAT::format);
        Tables.column(reporterColumn, WaterSourceReport::getReporter, Function.identity());
        reporterColumn.setComparator(String.CASE_INSENSITIVE_ORDER);
        Tables.column(typeColumn, WaterSourceReport::getType, Enum::name);
        typeColumn.setComparator(Comparator.comparing(Enum::name));
        Tables.column(conditionColumn, WaterSourceReport::getCondition, Enum::name);
        conditionColumn.setComparator(Comparator.comparing(Enum::name));
        Tables.column(latitudeColumn, WaterSourceReport::getLatitude, GeoPoint::format);
        Tables.column(longitudeColumn, WaterSourceReport::getLongitude, GeoPoint::format);

        reportTable.setRowFactory(table -> {
            TableRow<WaterSourceReport> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2
                        && !row.isEmpty()) {
                    application.showReportDetails(row.getItem());
                }
            });
            return row;
        });
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
        try {
            reportTable.setItems(FXCollections.observableArrayList(application.getContext()
                    .getReportService().listReports(application.getLoggedAccount())));
            // Replacing the items clears the sort order, so set the default here.
            numberColumn.setSortType(TableColumn.SortType.ASCENDING);
            reportTable.getSortOrder().setAll(List.of(numberColumn));
            reportTable.sort();
        } catch (PermissionDeniedException e) {
            showMessage(e.getMessage());
        } catch (DataAccessException e) {
            LOGGER.log(Level.SEVERE, "Could not load reports", e);
            showMessage("Could not load reports: " + e.getMessage());
        }
    }

    private void showMessage(String text) {
        message.setText(text);
        message.setVisible(true);
        message.setManaged(true);
    }

    /**
     * Shows the selected report on its own.
     */
    @FXML
    private void detailsPressed() {
        WaterSourceReport selected = reportTable.getSelectionModel().getSelectedItem();
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
     * Goes to the previous page when the button clicked
     */
    @FXML
    private void goBackButtonPressed() {
        application.gotoApp();
    }
}
