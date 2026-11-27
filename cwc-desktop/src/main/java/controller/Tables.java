package controller;

import atlantafx.base.theme.Styles;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.text.Text;

import java.util.function.Function;

/**
 * Helpers for the app's tables.
 */
final class Tables {

    private Tables() {
    }

    /**
     * Makes a column show one property of the row, sorted by the property's natural
     * order (or the column's comparator) and displayed with the given formatter.
     *
     * @param column the column
     * @param value  extracts the cell value from a row
     * @param format turns a cell value into its text
     * @param <S>    the row type
     * @param <T>    the cell value type
     */
    static <S, T> void column(TableColumn<S, T> column, Function<S, T> value, Function<T, String> format) {
        column(column, value, format, false);
    }

    /**
     * Like {@link #column(TableColumn, Function, Function)}; with {@code tooltip} each
     * cell also shows its full text as a tooltip, for a column that may cut text off.
     */
    static <S, T> void column(TableColumn<S, T> column, Function<S, T> value, Function<T, String> format,
                              boolean tooltip) {
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        column.setCellFactory(c -> new TableCell<>() {
            private final Tooltip tip = new Tooltip();

            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                String text = empty || item == null ? null : format.apply(item);
                setText(text);
                if (tooltip) {
                    tip.setText(text);
                    setTooltip(text == null || text.isEmpty() ? null : tip);
                }
            }
        });
    }

    /**
     * Like {@link #column(TableColumn, Function, Function, boolean)} with a tooltip, but
     * long text wraps onto more lines (the row grows) instead of being cut off with "...".
     */
    static <S, T> void wrappingColumn(TableColumn<S, T> column, Function<S, T> value, Function<T, String> format) {
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        column.setCellFactory(c -> new TableCell<>() {
            private final Tooltip tip = new Tooltip();
            private final Text text = new Text();

            {
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
                text.getStyleClass().add("wrapping-cell-text");
                // The cell's own text colour (it changes on selected rows) and font.
                text.fillProperty().bind(textFillProperty());
                text.fontProperty().bind(fontProperty());
                text.wrappingWidthProperty().bind(Bindings.createDoubleBinding(
                        () -> Math.max(0, column.getWidth() - snappedLeftInset() - snappedRightInset() - 1),
                        column.widthProperty(), paddingProperty()));
            }

            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                String shown = empty || item == null ? null : format.apply(item);
                setText(shown);
                text.setText(shown == null ? "" : shown);
                setGraphic(shown == null ? null : text);
                tip.setText(shown);
                setTooltip(shown == null || shown.isEmpty() ? null : tip);
            }
        });
    }

    /**
     * Like {@link #column}, but shows each value as a badge. The cell's text is still the
     * formatted value (shown through the badge only).
     *
     * @param style picks the badge style class for a value
     */
    static <S, T> void badgeColumn(TableColumn<S, T> column, Function<S, T> value, Function<T, String> format,
                                   Function<T, String> style) {
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        column.setCellFactory(c -> new TableCell<>() {
            private final Label badge = Badges.badge("", Badges.NEUTRAL);

            {
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }

            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                String text = format.apply(item);
                badge.setText(text);
                badge.getStyleClass().setAll("label", Badges.BADGE, style.apply(item));
                setText(text);
                setGraphic(badge);
            }
        });
    }

    /**
     * The app's table look: columns fill the width (the last one takes the slack) and
     * rows are striped.
     */
    static void style(TableView<?> table) {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        if (!table.getStyleClass().contains(Styles.STRIPED)) {
            table.getStyleClass().add(Styles.STRIPED);
        }
    }
}
