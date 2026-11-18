package controller;

import fxapp.Main;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import model.GeoPoint;
import model.Validation;
import model.WaterSourceCondition;
import model.WaterSourceReport;
import model.WaterSourceType;
import model.persistence.DataAccessException;
import model.service.PermissionDeniedException;

import java.net.URL;
import java.time.ZonedDateTime;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by Sean on 10/6/2016.
 * A Controller class to handle the Water Source Report form
 */
public class WaterSourceReportController extends AnchorPane implements Initializable, Page {
    private static final Logger LOGGER = Logger.getLogger(WaterSourceReportController.class.getName());

    private Main application;
    @FXML
    private Label username;
    @FXML
    private Label date;
    @FXML
    private Label time;
    @FXML
    private Label number;
    @FXML
    private ComboBox<WaterSourceType> type;
    @FXML
    private ComboBox<WaterSourceCondition> condition;
    @FXML
    private TextField latitude;
    @FXML
    private TextField longitude;
    @FXML
    private Label errorMessage;

    /**
     * Setup the main application link so we can call methods there
     * @param application the reference to the FX Application instance
     */
    @Override
    public void setApp(Main application) {
        this.application = application;
        username.setText(application.getLoggedAccount().getId());
        try {
            number.setText(String.valueOf(application.getContext().getReportService().nextReportNumber()));
        } catch (DataAccessException e) {
            LOGGER.log(Level.SEVERE, "Could not read the next report number", e);
            number.setText("?");
        }
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        type.setItems(FXCollections.observableArrayList(WaterSourceType.values()));
        condition.setItems(FXCollections.observableArrayList(WaterSourceCondition.values()));
        ZonedDateTime now = ZonedDateTime.now();
        date.setText(WaterSourceReport.DATE_FORMAT.format(now));
        time.setText(WaterSourceReport.TIME_FORMAT.format(now));
        errorMessage.setText("");
    }

    /**
     * Goes to the previous page when the button clicked
     */
    @FXML
    private void backButtonPressed() {
        application.gotoApp();
    }

    /**
     * Validates and stores the report, or shows the first validation error.
     */
    @FXML
    private void submitReport() {
        try {
            double lat = Validation.parseLatitude(latitude.getText());
            double lng = Validation.parseLongitude(longitude.getText());
            application.getContext().getReportService().submit(application.getLoggedAccount(),
                    lat, lng, type.getValue(), condition.getValue());
            application.gotoApp();
        } catch (IllegalArgumentException | PermissionDeniedException e) {
            showError(e.getMessage());
        } catch (DataAccessException e) {
            LOGGER.log(Level.SEVERE, "Could not save the report", e);
            showError("Could not save the report: " + e.getMessage());
        }
    }

    /**
     * Opens a map to choose the location and fills the latitude and longitude fields
     * with the chosen point. The fields can still be typed in as well.
     */
    @FXML
    private void pickOnMapPressed() {
        application.pickLocation(GeoPoint.tryParse(latitude.getText(), longitude.getText()))
                .ifPresent(point -> {
                    latitude.setText(point.formattedLatitude());
                    longitude.setText(point.formattedLongitude());
                });
    }

    private void showError(String text) {
        errorMessage.setText(text);
        errorMessage.setVisible(true);
    }
}
