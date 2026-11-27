package controller;

import atlantafx.base.theme.Styles;
import javafx.scene.control.ComboBoxBase;
import javafx.scene.control.Control;
import javafx.scene.control.TextInputControl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Highlights the form fields a validation message is about. The services report the
 * first problem as one of the {@code model.Validation.MSG_*} texts; this maps each text
 * to its field(s), marks them with the theme's {@code :danger} state and focuses the
 * first. Purely presentation: the message itself is shown unchanged.
 */
public final class FieldErrors {
    private final Map<String, List<Control>> fieldsByMessage = new LinkedHashMap<>();
    private final Set<Control> fields = new LinkedHashSet<>();

    /**
     * Registers the fields a message is about. A field loses its highlight as soon as it
     * is edited.
     * @param message a validation message
     * @param controls the fields it is about
     * @return this object
     */
    public FieldErrors on(String message, Control... controls) {
        List<Control> list = fieldsByMessage.computeIfAbsent(message, key -> new ArrayList<>());
        for (Control control : controls) {
            list.add(control);
            if (fields.add(control)) {
                if (control instanceof TextInputControl input) {
                    input.textProperty().addListener((observable, old, text) -> mark(control, false));
                } else if (control instanceof ComboBoxBase<?> box) {
                    box.valueProperty().addListener((observable, old, value) -> mark(control, false));
                }
            }
        }
        return this;
    }

    /**
     * Removes every highlight.
     */
    public void clear() {
        fields.forEach(field -> mark(field, false));
    }

    /**
     * Highlights (and focuses) the fields of a message, after removing other highlights.
     * @param message the message that is shown
     * @return true if the message is about one of the registered fields
     */
    public boolean show(String message) {
        clear();
        List<Control> list = fieldsByMessage.get(message);
        if (list == null || list.isEmpty()) {
            return false;
        }
        list.forEach(field -> mark(field, true));
        list.get(0).requestFocus();
        return true;
    }

    private static void mark(Control field, boolean error) {
        field.pseudoClassStateChanged(Styles.STATE_DANGER, error);
    }
}
