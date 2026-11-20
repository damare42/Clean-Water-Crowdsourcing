package controller;

import javafx.scene.control.Label;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Inline messages ("banners", styled in css/app.css) shown at the top of a page, card or
 * dialog. A banner is a {@link Label}, so its text stays plain text.
 */
public final class Banners {

    /** The kind of a banner: its colour and icon. */
    public enum Kind {
        SUCCESS("banner-success", "mdoal-check_circle_outline"),
        DANGER("banner-danger", "mdoal-error_outline"),
        WARNING("banner-warning", "mdomz-warning"),
        INFO("banner-info", "mdoal-info");

        private final String style;
        private final String icon;

        Kind(String style, String icon) {
            this.style = style;
            this.icon = icon;
        }
    }

    private Banners() {
    }

    /**
     * Shows a message in a banner label.
     * @param banner the label
     * @param text the message
     * @param kind its kind
     */
    public static void show(Label banner, String text, Kind kind) {
        banner.getStyleClass().removeIf(style -> style.startsWith("banner-"));
        banner.getStyleClass().add(kind.style);
        if (!banner.getStyleClass().contains("banner")) {
            banner.getStyleClass().add("banner");
        }
        banner.setGraphic(new FontIcon(kind.icon));
        banner.setText(text);
        banner.setVisible(true);
        banner.setManaged(true);
        banner.applyCss();
    }

    /**
     * Hides a banner label.
     * @param banner the label
     */
    public static void hide(Label banner) {
        banner.setVisible(false);
        banner.setManaged(false);
    }
}
