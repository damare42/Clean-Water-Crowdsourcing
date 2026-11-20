package controller;

import fxapp.Main;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import model.ReportField;
import model.Report;
import model.WaterPurityReport;
import model.WaterSourceReport;
import org.kordamp.ikonli.javafx.FontIcon;

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
    @FXML
    private Label conditionBadge;
    @FXML
    private FontIcon kindIcon;

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
        if (report instanceof WaterPurityReport) {
            kindIcon.setIconLiteral("mdomz-science");
        }
        Object condition = report instanceof WaterSourceReport source ? source.getCondition()
                : report instanceof WaterPurityReport purity ? purity.getCondition() : null;
        if (condition != null) {
            conditionBadge.setText(condition.toString());
            conditionBadge.getStyleClass().add(Badges.styleOf(condition));
            conditionBadge.setVisible(true);
            conditionBadge.setManaged(true);
        }
        for (int row = 0; row < reportFields.size(); row++) {
            ReportField field = reportFields.get(row);
            Label key = new Label(field.label());
            key.getStyleClass().add("detail-key");
            Label value = new Label(field.value());
            value.setId("field" + row);
            value.getStyleClass().add("detail-value");
            value.setWrapText(true);
            if (row == reportFields.size() - 1) {
                key.getStyleClass().add("last");
                value.getStyleClass().add("last");
            }
            fields.addRow(row, key, value);
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
