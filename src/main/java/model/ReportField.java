package model;

import java.util.Objects;

/**
 * One labelled value of a report as shown to the user, e.g. ("Condition", "Potable").
 * Used for detail views and map popups so every kind of report can be shown the
 * same way.
 *
 * @param label the name of the field
 * @param value the formatted value
 */
public record ReportField(String label, String value) {

    public ReportField {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(value, "value");
    }
}
