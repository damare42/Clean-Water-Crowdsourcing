package controller;

import fxapp.Main;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.AnchorPane;
import model.Validation;
import model.WaterSourceCondition;
import model.WaterSourceReportHolder;
import model.WaterSourceType;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

/**
 * Created by Sean on 10/6/2016.
 * A Controller class to handle the Water Source Report form
 */
public class WaterSourceReportController extends AnchorPane implements Initializable {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm");

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
    public void setApp(Main application) {
        this.application = application;
        username.setText(application.getLoggedAccount().getId());
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        type.setItems(FXCollections.observableArrayList(WaterSourceType.values()));
        condition.setItems(FXCollections.observableArrayList(WaterSourceCondition.values()));
        LocalDateTime now = LocalDateTime.now();
        date.setText(DATE_FORMAT.format(now));
        time.setText(TIME_FORMAT.format(now));
        number.setText(String.valueOf(WaterSourceReportHolder.nextReportNumber()));
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
            WaterSourceReportHolder.submit(username.getText(), date.getText(), time.getText(),
                    lat, lng, type.getValue(), condition.getValue());
            application.gotoApp();
        } catch (IllegalArgumentException e) {
            errorMessage.setText(e.getMessage());
            errorMessage.setVisible(true);
        }
    }
}
