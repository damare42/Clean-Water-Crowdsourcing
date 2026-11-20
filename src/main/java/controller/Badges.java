package controller;

import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.util.Callback;
import model.AccountType;
import model.WaterPurityCondition;
import model.WaterSourceCondition;
import model.security.SecurityEventType;

import java.util.function.Function;

/**
 * Pill-shaped status labels ("badges", styled in css/app.css): which style each
 * condition, role, account status and security event gets, and table cells that show
 * a value as a badge.
 */
public final class Badges {
    static final String BADGE = "badge";
    static final String SUCCESS = "badge-success";
    static final String WARNING = "badge-warning";
    static final String DANGER = "badge-danger";
    static final String INFO = "badge-info";
    static final String NEUTRAL = "badge-neutral";

    private Badges() {
    }

    /**
     * @param text the badge's text
     * @param style one of the badge style classes, e.g. from {@link #styleOf(Object)}
     * @return a label styled as a badge that is never shortened
     */
    public static Label badge(String text, String style) {
        Label label = new Label(text);
        label.getStyleClass().addAll(BADGE, style);
        label.setMinWidth(Label.USE_PREF_SIZE);
        return label;
    }

    /**
     * @param value a condition, account type or security event type
     * @return the badge style for it
     */
    public static String styleOf(Object value) {
        if (value instanceof WaterSourceCondition condition) {
            return switch (condition) {
                case Potable -> SUCCESS;
                case Clear -> INFO;
                case Muddy -> WARNING;
                case Waste -> DANGER;
            };
        }
        if (value instanceof WaterPurityCondition condition) {
            return switch (condition) {
                case Safe -> SUCCESS;
                case Treatable -> WARNING;
                case Unsafe -> DANGER;
            };
        }
        if (value instanceof AccountType type) {
            return roleStyle(type);
        }
        if (value instanceof SecurityEventType type) {
            return switch (type) {
                case LOGIN_FAILURE, LOGIN_LOCKED, PERMISSION_DENIED -> WARNING;
                case ACCOUNT_BANNED, ACCOUNT_DELETED -> DANGER;
                case REGISTRATION, ADMIN_CREATED, ACCOUNT_UNBANNED -> SUCCESS;
                case LOGIN_SUCCESS -> NEUTRAL;
            };
        }
        return NEUTRAL;
    }

    /**
     * @param type an account type
     * @return the badge style of its role
     */
    public static String roleStyle(AccountType type) {
        return switch (type) {
            case User -> "badge-role-user";
            case Worker -> "badge-role-worker";
            case Manager -> "badge-role-manager";
            case Admin -> "badge-role-admin";
        };
    }

    /**
     * @param banned whether the account is banned
     * @return the badge style of the account status
     */
    public static String statusStyle(boolean banned) {
        return banned ? DANGER : SUCCESS;
    }

    /**
     * A table cell factory that shows each value as a badge. The cell's text stays the
     * value's text (so it can still be read and searched), shown through the badge.
     *
     * @param style picks the badge style for a value
     * @param <S> the row type
     * @param <T> the value type
     * @return the cell factory
     */
    public static <S, T> Callback<TableColumn<S, T>, TableCell<S, T>> cells(Function<T, String> style) {
        return column -> new TableCell<>() {
            private final Label badge = badge("", NEUTRAL);

            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                badge.setText(item.toString());
                badge.getStyleClass().setAll(BADGE, style.apply(item));
                setText(item.toString());
                setGraphic(badge);
                setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
            }
        };
    }
}
