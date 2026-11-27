package controller;

import fxapp.Main;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import model.GeoPoint;

import java.net.URL;
import java.util.Optional;
import java.util.ResourceBundle;

/**
 * A window with a map where the user clicks to choose a location.
 */
public class MapPickerController implements Initializable {

    @FXML
    private WebView mapView;
    @FXML
    private Label selection;
    @FXML
    private Button useButton;

    private Stage dialogStage;
    /** Keeps the map, and with it the page's bridge object, reachable. */
    private LeafletMap map;
    private GeoPoint picked;
    private GeoPoint result;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        useButton.setDisable(true);
    }

    /**
     * Loads the map.
     * @param application the main application
     * @param dialogStage the window this controller is shown in
     * @param initial a location to show as already chosen, if any
     */
    public void setUp(Main application, Stage dialogStage, Optional<GeoPoint> initial) {
        this.dialogStage = dialogStage;
        map = new LeafletMap(mapView, new LeafletMap.Listener() {
            @Override
            public void locationPicked(GeoPoint point) {
                select(point);
            }
        }, application::openExternalLink);
        initial.ifPresent(this::select);
        map.enablePicking(initial.orElse(null));
    }

    private void select(GeoPoint point) {
        picked = point;
        selection.setText("Latitude " + point.formattedLatitude()
                + ", longitude " + point.formattedLongitude());
        useButton.setDisable(false);
    }

    /**
     * @return the location the user chose, or empty if they cancelled
     */
    public Optional<GeoPoint> getResult() {
        return Optional.ofNullable(result);
    }

    /**
     * Accepts the chosen location and closes the window.
     */
    @FXML
    private void usePressed() {
        result = picked;
        dialogStage.close();
    }

    /**
     * Closes the window without choosing a location.
     */
    @FXML
    private void cancelPressed() {
        dialogStage.close();
    }
}
