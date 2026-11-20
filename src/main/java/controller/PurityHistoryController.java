package controller;

import fxapp.Main;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import model.GeoPoint;
import model.MonthlyAverage;
import model.PurityHistory;
import model.PurityMetric;
import model.Validation;
import model.persistence.DataAccessException;
import model.service.PermissionDeniedException;

import java.net.URL;
import java.time.Month;
import java.time.Year;
import java.time.format.TextStyle;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The manager's purity history: the monthly average virus or contaminant PPM of the
 * purity reports within a radius of a location, for one year, as a line chart. The
 * month axis always shows all twelve months; months without reports have no point.
 */
public class PurityHistoryController implements Initializable, Page {
    private static final Logger LOGGER = Logger.getLogger(PurityHistoryController.class.getName());

    @FXML
    private TextField latitude;
    @FXML
    private TextField longitude;
    @FXML
    private TextField radius;
    @FXML
    private TextField year;
    @FXML
    private ComboBox<PurityMetric> metric;
    @FXML
    private Label message;
    @FXML
    private LineChart<String, Number> chart;
    @FXML
    private CategoryAxis monthAxis;
    @FXML
    private VBox emptyChart;
    @FXML
    private Label emptyTitle;
    @FXML
    private Label emptyText;

    private final FieldErrors fieldErrors = new FieldErrors();

    private Main application;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        metric.setItems(FXCollections.observableArrayList(PurityMetric.values()));
        metric.setValue(PurityMetric.Virus);
        monthAxis.setAutoRanging(false);
        monthAxis.setCategories(FXCollections.observableArrayList(
                Arrays.stream(Month.values()).map(PurityHistoryController::monthName).toList()));
        chart.setVisible(false);
        fieldErrors.on(Validation.MSG_INVALID_LATITUDE, latitude)
                .on(Validation.MSG_INVALID_LONGITUDE, longitude)
                .on(Validation.MSG_INVALID_RADIUS, radius)
                .on(Validation.MSG_INVALID_YEAR, year);
    }

    @Override
    public void setApp(Main application) {
        this.application = application;
        year.setText(String.valueOf(Year.now(application.getContext().getPurityReportService().getZone())));
    }

    /**
     * @return the short English name of a month, e.g. "Jan"; the chart's categories
     */
    static String monthName(Month month) {
        return month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
    }

    /**
     * Validates the inputs and draws the chart, or shows the first error.
     */
    @FXML
    private void showPressed() {
        try {
            GeoPoint center = GeoPoint.parse(latitude.getText(), longitude.getText());
            double radiusKm = Validation.parseRadiusKm(radius.getText());
            int chosenYear = Validation.parseYear(year.getText());
            PurityHistory history = application.getContext().getPurityReportService()
                    .history(application.getLoggedAccount(), center, radiusKm, chosenYear, metric.getValue());
            fieldErrors.clear();
            draw(history);
        } catch (IllegalArgumentException | PermissionDeniedException e) {
            showError(e.getMessage());
            fieldErrors.show(e.getMessage());
        } catch (DataAccessException e) {
            LOGGER.log(Level.SEVERE, "Could not load the purity history", e);
            showError("Could not load the purity history: " + e.getMessage());
        }
    }

    private void showError(String text) {
        chart.getData().clear();
        Banners.show(message, text, Banners.Kind.DANGER);
        showEmpty("No chart", "Correct the highlighted field and press Show again.");
    }

    private void showEmpty(String title, String text) {
        emptyTitle.setText(title);
        emptyText.setText(text);
        emptyChart.setVisible(true);
        chart.setVisible(false);
    }

    private void draw(PurityHistory history) {
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName(history.metric().label() + " " + history.year());
        int reports = 0;
        for (MonthlyAverage average : history.months()) {
            series.getData().add(new XYChart.Data<>(monthName(average.month()), average.average()));
            reports += average.reportCount();
        }
        chart.getData().setAll(List.of(series));
        chart.setTitle("Monthly average " + history.metric().label() + " in " + history.year());
        String where = " within " + formatKm(history.radiusKm()) + " km of "
                + history.center().formattedLatitude() + ", " + history.center().formattedLongitude();
        Banners.show(message, history.isEmpty()
                ? "No purity reports" + where + " in " + history.year() + "."
                : reports + (reports == 1 ? " report" : " reports") + where + " in "
                        + history.months().size() + (history.months().size() == 1 ? " month." : " months."),
                history.isEmpty() ? Banners.Kind.WARNING : Banners.Kind.INFO);
        if (history.isEmpty()) {
            showEmpty("No purity reports", "Try a larger radius or another year.");
        } else {
            emptyChart.setVisible(false);
            chart.setVisible(true);
        }
    }

    private static String formatKm(double km) {
        return GeoPoint.format(km);
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
}
