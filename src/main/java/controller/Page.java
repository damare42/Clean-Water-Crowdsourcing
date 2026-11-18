package controller;

import fxapp.Main;

/**
 * A controller of a full-window page. {@link Main} calls {@link #setApp} right after
 * loading the page, once the logged-in account (if any) is known.
 */
public interface Page {

    /**
     * Sets the page up.
     *
     * @param application the running application
     */
    void setApp(Main application);
}
