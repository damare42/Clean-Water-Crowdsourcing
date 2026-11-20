package controller;

import fxapp.Main;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import model.persistence.DataAccessException;
import model.security.SecurityEvent;
import model.security.SecurityEventType;
import model.service.PermissionDeniedException;

import java.net.URL;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.ResourceBundle;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The administrator's view of the security log, newest event first.
 */
public class SecurityLogController implements Initializable, Page {
    private static final Logger LOGGER = Logger.getLogger(SecurityLogController.class.getName());
    /** Format of the time column, e.g. 2016/10/11 09:05:07. */
    static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    private final ZoneId zone = ZoneId.systemDefault();
    private Main application;

    @FXML
    private TableView<SecurityEvent> eventTable;
    @FXML
    private TableColumn<SecurityEvent, Long> sequenceColumn;
    @FXML
    private TableColumn<SecurityEvent, Instant> timeColumn;
    @FXML
    private TableColumn<SecurityEvent, SecurityEventType> typeColumn;
    @FXML
    private TableColumn<SecurityEvent, String> actorColumn;
    @FXML
    private TableColumn<SecurityEvent, String> subjectColumn;
    @FXML
    private TableColumn<SecurityEvent, String> detailColumn;
    @FXML
    private Label message;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        Tables.column(sequenceColumn, SecurityEvent::sequence, Object::toString);
        Tables.column(timeColumn, SecurityEvent::occurredAt, t -> TIME_FORMAT.format(t.atZone(zone)));
        Tables.badgeColumn(typeColumn, SecurityEvent::type, SecurityEventType::label, Badges::styleOf);
        typeColumn.setComparator(Comparator.comparing(SecurityEventType::label));
        Tables.column(actorColumn, SecurityEvent::actor, Function.identity());
        Tables.column(subjectColumn, SecurityEvent::subject, Function.identity());
        Tables.column(detailColumn, SecurityEvent::detail, Function.identity());
        Tables.style(eventTable);
    }

    @Override
    public void setApp(Main application) {
        this.application = application;
        refreshPressed();
    }

    /**
     * Reloads the log.
     */
    @FXML
    private void refreshPressed() {
        try {
            eventTable.setItems(FXCollections.observableArrayList(
                    application.getContext().getSecurityLog().recentEvents(application.getLoggedAccount())));
            eventTable.getSortOrder().clear();
        } catch (PermissionDeniedException e) {
            showMessage(e.getMessage());
        } catch (DataAccessException e) {
            LOGGER.log(Level.SEVERE, "Could not load the security log", e);
            showMessage("Could not load the security log: " + e.getMessage());
        }
    }

    private void showMessage(String text) {
        Banners.show(message, text, Banners.Kind.DANGER);
    }
}
