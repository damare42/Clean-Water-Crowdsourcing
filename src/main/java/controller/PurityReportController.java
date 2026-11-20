package controller;

import fxapp.Main;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import model.GeoPoint;
import model.Validation;
import model.WaterPurityCondition;
import model.WaterPurityReport;
import model.WaterSourceReport;
import model.persistence.DataAccessException;
import model.service.PermissionDeniedException;

import java.net.URL;
import java.time.ZonedDateTime;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The water purity report form. The location can be typed or picked on a map; all
 * values are validated by the model and the report is stored by the purity report
 * service, which also checks that the account may file purity reports.
 */
public class PurityReportController implements Initializable, Page {
    private static final Logger LOGGER = Logger.getLogger(PurityReportController.class.getName());

    @FXML
    private Label number;
    @FXML
    private Label date;
    @FXML
    private Label time;
    @FXML
    private Label username;
    @FXML
    private TextField latitude;
    @FXML
    private TextField longitude;
    @FXML
    private ComboBox<WaterPurityCondition> condition;
    @FXML
    private TextField virusPpm;
    @FXML
    private TextField contaminantPpm;
    @FXML
    private Label errorMessage;

    private final FieldErrors fieldErrors = new FieldErrors();

    private Main application;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        condition.setItems(FXCollections.observableArrayList(WaterPurityCondition.values()));
        ZonedDateTime now = ZonedDateTime.now();
        date.setText(WaterSourceReport.DATE_FORMAT.format(now));
        time.setText(WaterSourceReport.TIME_FORMAT.format(now));
        fieldErrors.on(Validation.MSG_INVALID_LATITUDE, latitude)
                .on(Validation.MSG_INVALID_LONGITUDE, longitude)
                .on(Validation.MSG_PURITY_CONDITION_REQUIRED, condition)
                .on(Validation.MSG_INVALID_VIRUS_PPM, virusPpm)
                .on(Validation.MSG_INVALID_CONTAMINANT_PPM, contaminantPpm);
    }

    @Override
    public void setApp(Main application) {
        this.application = application;
        username.setText(String.valueOf(application.getLoggedAccount()));
        try {
            number.setText(String.valueOf(application.getContext().getPurityReportService()
                    .nextReportNumber(application.getLoggedAccount())));
        } catch (DataAccessException e) {
            LOGGER.log(Level.SEVERE, "Could not read the next purity report number", e);
            number.setText("?");
        } catch (PermissionDeniedException e) {
            LOGGER.log(Level.WARNING, "Not allowed to see the next purity report number: {0}", e.getMessage());
            number.setText("?");
        }
    }

    /**
     * Validates and stores the report, or shows the first validation error.
     */
    @FXML
    private void submitPressed() {
        try {
            GeoPoint location = GeoPoint.parse(latitude.getText(), longitude.getText());
            double virus = Validation.parseVirusPpm(virusPpm.getText());
            double contaminant = Validation.parseContaminantPpm(contaminantPpm.getText());
            WaterPurityReport report = application.getContext().getPurityReportService().submit(
                    application.getLoggedAccount(), location, condition.getValue(), virus, contaminant);
            application.gotoApp();
            application.showToast("Purity report #" + report.getReportNumber() + " submitted");
        } catch (IllegalArgumentException | PermissionDeniedException e) {
            showError(e.getMessage());
        } catch (DataAccessException e) {
            LOGGER.log(Level.SEVERE, "Could not save the purity report", e);
            showError("Could not save the report: " + e.getMessage());
        }
    }

    /**
     * Opens a map to choose the location and fills the latitude and longitude fields.
     */
    @FXML
    private void pickOnMapPressed() {
        application.pickLocation(GeoPoint.tryParse(latitude.getText(), longitude.getText()))
                .ifPresent(point -> {
                    latitude.setText(point.formattedLatitude());
                    longitude.setText(point.formattedLongitude());
                });
    }

    /**
     * Goes back to the map without saving.
     */
    @FXML
    private void backPressed() {
        application.gotoApp();
    }

    private void showError(String text) {
        Banners.show(errorMessage, text, Banners.Kind.DANGER);
        fieldErrors.show(text);
    }
}
