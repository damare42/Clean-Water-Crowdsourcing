package controller;

import fxapp.Main;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;


import java.net.URL;
import java.util.ResourceBundle;

/**
 * Created by Sheng on 9/19/16.
 * A controller for the app view
 */
public class AppViewController implements Initializable {
    @FXML
    private WebView webView;
    @FXML
    private Label welcome;
    @FXML
    private Label username;

    private WebEngine engine;
    private Main application;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
    }

    /**
     * Sets up the up view
     * @param application the main application of th app view
     */
    public void setApp(Main application){
        this.application = application;
        engine = webView.getEngine();
        engine.load("https://maps.google.com");
        username.setText("" + application.getLoggedAccount());
    }

    /**
     * Logs out the user from the app
     */
    @FXML
    private void logoutPressed() {
        application.accountLogout();
    }

    /**
     * Activates the username is pressed
     */
    @FXML
    private void usernamePressed() {
        application.gotoProfile();
    };

    /**
     * Goes to the water source report creation pation
     */
    @FXML
    private void createWaterSourceReportClicked() {
        application.gotoWaterSourceReport();
    }

    /**
     * activates when the Reports List is clicked
     */
    @FXML
    private void reportListClicked() {
        application.gotoWSRViewer();
    }
}
