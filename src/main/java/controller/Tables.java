package controller;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;

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
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        column.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : format.apply(item));
            }
        });
    }
}
