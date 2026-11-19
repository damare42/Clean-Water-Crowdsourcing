package controller;

import fxapp.Main;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import model.ReportField;
import model.Report;

import java.net.URL;
import java.time.ZoneId;
import java.util.List;
import java.util.ResourceBundle;

/**
 * A window showing every field of one report (water source or water purity).
 */
public class ReportDetailsController implements Initializable {

    @FXML
    private Label title;
    @FXML
    private GridPane fields;

    private Main application;
    private Stage dialogStage;
    private Report report;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
    }

    /**
     * Fills the window with a report.
     * @param application the main application
     * @param dialogStage the window this controller is shown in
     * @param report the report to show
     */
    public void setUp(Main application, Stage dialogStage, Report report) {
        this.application = application;
        this.dialogStage = dialogStage;
        this.report = report;
        title.setText(report.getTitle());
        List<ReportField> reportFields = report.fields(ZoneId.systemDefault());
        for (int row = 0; row < reportFields.size(); row++) {
            ReportField field = reportFields.get(row);
            Label value = new Label(field.value());
            value.setId("field" + row);
            fields.addRow(row, new Label(field.label() + ":"), value);
        }
    }

    /**
     * Closes this window and shows the report on the main map.
     */
    @FXML
    private void showOnMapPressed() {
        dialogStage.close();
        application.showOnMap(report);
    }

    /**
     * Closes this window.
     */
    @FXML
    private void closePressed() {
        dialogStage.close();
    }
}
