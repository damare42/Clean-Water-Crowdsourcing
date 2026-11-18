package controller;

import fxapp.Main;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.VBox;
import model.WaterSourceReport;
import model.persistence.DataAccessException;
import model.service.PermissionDeniedException;

import java.net.URL;
import java.time.ZoneId;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by Sean on 10/11/2016.
 * A controller class for the Water Source Report Viewer page
 */
public class WSRViewerController extends AnchorPane implements Initializable {
    private static final Logger LOGGER = Logger.getLogger(WSRViewerController.class.getName());
    private Main application;
    @FXML
    private ScrollPane WSRList;

    /**
     * sets the Water Source Report Viewer page
     * @param application the main application
     */
    public void setApp(Main application) {
        this.application = application;
        VBox reportBox = new VBox();
        try {
            ZoneId zone = ZoneId.systemDefault();
            for (WaterSourceReport report : application.getContext().getReportService()
                    .listReports(application.getLoggedAccount())) {
                reportBox.getChildren().add(new Label(report.describe(zone)));
            }
        } catch (PermissionDeniedException e) {
            reportBox.getChildren().add(new Label(e.getMessage()));
        } catch (DataAccessException e) {
            LOGGER.log(Level.SEVERE, "Could not load reports", e);
            reportBox.getChildren().add(new Label("Could not load reports: " + e.getMessage()));
        }
        WSRList.setContent(reportBox);
    }

    @Override
    public void initialize(URL location, ResourceBundle resources) {
    }

    /**
     * Goes to the previous page when the button clicked
     */
    @FXML
    private void goBackButtonPressed() {
        application.gotoApp();
    }
}
