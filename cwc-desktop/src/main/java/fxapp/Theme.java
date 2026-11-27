package fxapp;

import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Dialog;

import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The app's look: the AtlantaFX PrimerLight theme as the user-agent stylesheet, plus the
 * app's own stylesheet ({@value #APP_STYLESHEET}) on every scene and dialog.
 */
public final class Theme {
    private static final Logger LOGGER = Logger.getLogger(Theme.class.getName());

    /** The app's stylesheet on the classpath. */
    public static final String APP_STYLESHEET = "/css/app.css";

    private Theme() {
    }

    /**
     * Sets the theme for every window of the application. Call before any window is shown.
     */
    public static void install() {
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
    }

    /**
     * Adds the app's stylesheet to a scene (once).
     * @param scene the scene
     * @return the scene
     */
    public static Scene style(Scene scene) {
        String css = stylesheet();
        if (css != null && !scene.getStylesheets().contains(css)) {
            scene.getStylesheets().add(css);
        }
        return scene;
    }

    /**
     * Adds the app's stylesheet to a dialog, such as an {@link javafx.scene.control.Alert}.
     * @param dialog the dialog
     * @param <D> the dialog's type
     * @return the dialog
     */
    public static <D extends Dialog<?>> D style(D dialog) {
        String css = stylesheet();
        if (css != null && !dialog.getDialogPane().getStylesheets().contains(css)) {
            dialog.getDialogPane().getStylesheets().add(css);
        }
        return dialog;
    }

    private static String stylesheet() {
        URL url = Theme.class.getResource(APP_STYLESHEET);
        if (url == null) {
            LOGGER.log(Level.WARNING, "Stylesheet {0} is missing", APP_STYLESHEET);
            return null;
        }
        return url.toExternalForm();
    }
}
