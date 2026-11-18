package controller;

import fxapp.Main;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import model.WaterSourceReport;
import model.WaterSourceReportHolder;

import java.net.URL;
import java.util.ResourceBundle;

/**
 * Created by Sean on 10/11/2016.
 * A controller class for the Water Source Report Viewer page
 */
public class WSRViewerController extends AnchorPane implements Initializable {
    private Main application;
    @FXML
    private ScrollPane WSRList;

    /**
     * sets the Water Source Report Viewer page
     * @param application the main application
     */
    public void setApp(Main application) {
        this.application = application;
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        VBox reportBox = new VBox();
        for (WaterSourceReport report : WaterSourceReportHolder.getReports()) {
            reportBox.getChildren().add(new Label(report.toString()));
        }
        WSRList.setContent(reportBox);
    }

    /**
     * Goes to the previous page when the button clicked
     */
    @FXML
    private void goBackButtonPressed() {
        application.gotoApp();
    }
}
