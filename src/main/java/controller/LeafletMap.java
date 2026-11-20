package controller;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.concurrent.Worker;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.util.Duration;
import model.GeoPoint;
import model.map.MapMarker;
import model.map.MarkerJson;
import netscape.javascript.JSException;
import netscape.javascript.JSObject;

import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shows the bundled Leaflet map page ({@value #PAGE}) in a {@link WebView} and talks
 * to it. Leaflet and the page come from the app's resources; only the
 * OpenStreetMap tiles need the network, so without a connection the map is blank
 * but everything else works.
 * <p>
 * Calls made before the page has loaded are queued and run once it has. Data goes
 * to the page as JSON strings passed as call arguments, never pasted into script
 * source. The page calls back through a {@link Bridge}, which this object keeps a
 * strong reference to: the WebView itself only holds it weakly, so without that
 * reference it could be garbage-collected and clicks would silently stop working.
 * <p>
 * Only the bundled page ever gets the bridge. Pop-up windows are blocked, and if the
 * view starts loading any other page it is sent back to the bundled one (whose state
 * is then restored), so a foreign page can never call into Java.
 */
public final class LeafletMap {

    private static final Logger LOGGER = Logger.getLogger(LeafletMap.class.getName());

    /** The map page on the classpath. */
    public static final String PAGE = "/web/map.html";
    /** Added to the WebView's user agent so tile servers can identify the app. */
    static final String USER_AGENT_SUFFIX = " CleanWaterCrowdsourcing/0.1 (JavaFX desktop app)";
    private static final Duration RESIZE_DEBOUNCE = Duration.millis(120);
    private static final String SET_USER_LOCATION = "setUserLocation";
    /** State key of the location drawn by {@link #showUserLocation}. */
    private static final String USER_LOCATION = "userLocation";

    /** Receives events from the map, on the FX thread. */
    public interface Listener {
        /**
         * The user asked for the details of a marker.
         * @param kind the marker's kind, e.g. {@link MapMarker#KIND_SOURCE}
         * @param id the marker's report number
         */
        default void detailsRequested(String kind, int id) {
        }

        /**
         * The user clicked the map while picking was enabled.
         * @param point the clicked location
         */
        default void locationPicked(GeoPoint point) {
        }
    }

    private final WebEngine engine;
    private final Bridge bridge;
    /** The bundled page's URL; the only page that gets the bridge. */
    private final String pageUrl;
    private final List<Consumer<JSObject>> pending = new ArrayList<>();
    /**
     * Calls that set up the page's state (markers, picking), replayed if the page has
     * to be reloaded after an attempt to navigate away.
     */
    private final Map<String, Consumer<JSObject>> state = new LinkedHashMap<>();
    private JSObject api;
    private boolean unavailable;
    /** Tells Leaflet about a new view size once the size has stopped changing for a moment. */
    private final PauseTransition resizeDebounce = new PauseTransition(RESIZE_DEBOUNCE);

    /**
     * Starts loading the map page into the view.
     *
     * @param view the view to show the map in
     * @param listener gets the map's events
     * @param linkOpener opens web links (such as the attribution) outside the app
     */
    public LeafletMap(WebView view, Listener listener, Consumer<String> linkOpener) {
        this.bridge = new Bridge(Objects.requireNonNull(listener, "listener"),
                Objects.requireNonNull(linkOpener, "linkOpener"));
        view.setContextMenuEnabled(false);
        engine = view.getEngine();
        engine.setUserAgent(engine.getUserAgent() + USER_AGENT_SUFFIX);
        // window.open and target="_blank" would otherwise load into this same engine.
        engine.setCreatePopupHandler(features -> null);
        URL page = LeafletMap.class.getResource(PAGE);
        pageUrl = page == null ? null : page.toExternalForm();
        engine.getLoadWorker().stateProperty().addListener((observable, old, loadState) -> {
            if (loadState == Worker.State.SUCCEEDED) {
                pageLoaded();
            } else if (loadState == Worker.State.FAILED && isBundledPage(engine.getLocation())) {
                LOGGER.log(Level.WARNING, "The map page did not load",
                        engine.getLoadWorker().getException());
                giveUp();
            }
        });
        engine.locationProperty().addListener((observable, old, location) -> {
            if (pageUrl != null && !unavailable && !isBundledPage(location)) {
                leftBundledPage(location);
            }
        });
        // Leaflet tracks window resizes itself; this is a second, explicit nudge after the
        // view has been resized, so the map always matches the view.
        resizeDebounce.setOnFinished(event -> invalidateSize());
        InvalidationListener resized = observable -> {
            if (api != null) {
                resizeDebounce.playFromStart();
            }
        };
        view.widthProperty().addListener(resized);
        view.heightProperty().addListener(resized);
        if (pageUrl == null) {
            LOGGER.log(Level.WARNING, "Map page {0} is missing", PAGE);
            giveUp();
        } else {
            engine.load(pageUrl);
        }
    }

    private boolean isBundledPage(String location) {
        return pageUrl != null && location != null && canonical(pageUrl).equals(canonical(location));
    }

    /**
     * WebKit reports {@code file:/x} (as returned by {@link Class#getResource}) as
     * {@code file:///x}, also inside {@code jar:} URLs; this writes both the same way.
     */
    static String canonical(String url) {
        return url.replace("file:///", "file:/");
    }

    /**
     * Something tried to navigate the map view away from the bundled page. That page
     * never links anywhere inside the view, so this is unexpected: take the bridge
     * away from whatever is loading and go back to the map.
     */
    private void leftBundledPage(String location) {
        LOGGER.log(Level.WARNING, "The map view tried to navigate to {0}; reloading the map", location);
        api = null;
        pending.clear();
        pending.addAll(state.values());
        // Not from inside the location listener: start the reload once this event is done.
        Platform.runLater(() -> {
            if (!unavailable && !isBundledPage(engine.getLocation())) {
                engine.load(pageUrl);
            }
        });
    }

    private void pageLoaded() {
        if (!isBundledPage(engine.getLocation())) {
            // Never expose the bridge to any other page; the location listener reloads the map.
            LOGGER.log(Level.WARNING, "Not connecting the map to unexpected page {0}", engine.getLocation());
            return;
        }
        try {
            JSObject window = (JSObject) engine.executeScript("window");
            window.setMember("cwcBridge", bridge);
            Object map = window.getMember("cwcMap");
            if (!(map instanceof JSObject)) {
                LOGGER.log(Level.WARNING, "The map page has no map API");
                giveUp();
                return;
            }
            api = (JSObject) map;
        } catch (JSException e) {
            LOGGER.log(Level.WARNING, "Could not connect to the map page", e);
            giveUp();
            return;
        }
        List<Consumer<JSObject>> calls = List.copyOf(pending);
        pending.clear();
        calls.forEach(call -> call.accept(api));
        resizeDebounce.playFromStart();
    }

    private void giveUp() {
        unavailable = true;
        pending.clear();
    }

    /**
     * Calls a function of the page's API, now or once the page has loaded.
     *
     * @param stateKey if not null, the call sets up page state and is remembered under
     *                 this key (replacing an earlier call with the same key), so it can
     *                 be replayed if the page is reloaded
     */
    private void call(String stateKey, String function, Object... args) {
        call(stateKey, invocation(function, args));
    }

    /**
     * Runs a call against the page's API now, or once the page has loaded.
     *
     * @param stateKey as for {@link #call(String, String, Object...)}
     */
    private void call(String stateKey, Consumer<JSObject> call) {
        if (stateKey != null) {
            state.put(stateKey, call);
        }
        if (api != null) {
            call.accept(api);
        } else if (!unavailable) {
            pending.add(call);
        }
    }

    /** A call of one function of the page's API; a failure is logged, not thrown. */
    private static Consumer<JSObject> invocation(String function, Object... args) {
        return target -> {
            try {
                target.call(function, args);
            } catch (JSException e) {
                LOGGER.log(Level.WARNING, "Map call " + function + " failed", e);
            }
        };
    }

    /**
     * Replaces the markers on the map and zooms to show them all.
     * @param markers the markers to show
     */
    public void showMarkers(List<MapMarker> markers) {
        call("markers", "setMarkers", MarkerJson.toJson(markers));
    }

    /**
     * Centres the map on a marker shown by {@link #showMarkers} and opens its popup.
     * Does nothing if there is no such marker.
     * @param kind the marker's kind
     * @param id the marker's id
     */
    public void focusMarker(String kind, int id) {
        call(null, "focusMarker", kind, id);
    }

    /** How {@link #showUserLocation} may move the map. */
    public enum Recenter {
        /** Centre on the location unless the user has already moved the map or shown a report. */
        AUTO("auto"),
        /** Always centre on the location ("Locate me"). */
        FORCE("force"),
        /** Only draw the location. */
        NONE("none");

        private final String word;

        Recenter(String word) {
            this.word = word;
        }
    }

    /**
     * Draws the approximate location of this computer (a dot and an accuracy circle, in
     * a layer of their own: they are not report markers) and maybe centres the map on it.
     * <p>
     * If the page has to be reloaded, the location is drawn again. It only recentres the
     * reloaded map if this call never reached the page (and was not {@link Recenter#FORCE}):
     * once it has been applied, the map either was centred on the location already or the
     * user had moved it, so the replay only draws ({@link Recenter#NONE}).
     *
     * @param json the location as written by {@link model.map.LocationJson}
     * @param recenter whether to centre the map on it
     */
    public void showUserLocation(String json, Recenter recenter) {
        Consumer<JSObject> drawOnly = invocation(SET_USER_LOCATION, json, Recenter.NONE.word);
        // Replayed after a reload until applied once; from then on only drawn.
        UserLocationCall replay = new UserLocationCall(json,
                recenter == Recenter.FORCE ? Recenter.AUTO : recenter, drawOnly);
        replay.replayEntry = replay;
        state.put(USER_LOCATION, replay);
        UserLocationCall live = recenter == Recenter.FORCE
                ? new UserLocationCall(json, recenter, drawOnly) : replay;
        live.replayEntry = replay;
        call(null, live);
    }

    /**
     * Calls {@code setUserLocation} with a recentre mode; once that has reached the page,
     * the location's replay entry (if it is still the current one) is replaced by a call
     * that only draws.
     */
    private final class UserLocationCall implements Consumer<JSObject> {
        private final Consumer<JSObject> invocation;
        private final boolean mayRecenter;
        private final Consumer<JSObject> drawOnly;
        /** The replay entry this call belongs to. */
        private Consumer<JSObject> replayEntry;

        UserLocationCall(String json, Recenter recenter, Consumer<JSObject> drawOnly) {
            this.invocation = invocation(SET_USER_LOCATION, json, recenter.word);
            this.mayRecenter = recenter != Recenter.NONE;
            this.drawOnly = drawOnly;
        }

        @Override
        public void accept(JSObject target) {
            invocation.accept(target);
            if (mayRecenter) {
                state.replace(USER_LOCATION, replayEntry, drawOnly);
            }
        }
    }

    /**
     * Removes the location drawn by {@link #showUserLocation}.
     */
    public void clearUserLocation() {
        state.remove(USER_LOCATION);
        call(null, "clearUserLocation");
    }

    /**
     * Shows all report markers (the overview the map starts with when no location is known).
     */
    public void fitAll() {
        call(null, "fitAll");
    }

    /**
     * Makes the map re-read the size of its view (it keeps its centre).
     */
    private void invalidateSize() {
        call(null, "invalidateSize");
    }

    /**
     * Lets the user pick a location by clicking the map; each click is reported to
     * {@link Listener#locationPicked}.
     * @param initial a location to mark and centre on first, or null
     */
    public void enablePicking(GeoPoint initial) {
        if (initial == null) {
            call("picking", "enablePicking");
        } else {
            call("picking", "enablePicking", initial.latitude(), initial.longitude());
        }
    }

    /**
     * Called by the map page. Public only because the page calls it by reflection;
     * arguments arrive as strings and are validated here.
     */
    public static final class Bridge {
        private final Listener listener;
        private final Consumer<String> linkOpener;

        Bridge(Listener listener, Consumer<String> linkOpener) {
            this.listener = listener;
            this.linkOpener = linkOpener;
        }

        /**
         * @param kind the marker's kind
         * @param id the marker's id, as a decimal string
         */
        public void openDetails(String kind, String id) {
            try {
                listener.detailsRequested(kind, Integer.parseInt(id));
            } catch (NumberFormatException e) {
                LOGGER.log(Level.WARNING, "Ignoring details request for bad id {0}", id);
            }
        }

        /**
         * @param latitude the clicked latitude, as a decimal string
         * @param longitude the clicked longitude, as a decimal string
         */
        public void pickLocation(String latitude, String longitude) {
            try {
                listener.locationPicked(GeoPoint.parse(latitude, longitude));
            } catch (IllegalArgumentException e) {
                LOGGER.log(Level.WARNING, "Ignoring picked location {0}, {1}",
                        new Object[]{latitude, longitude});
            }
        }

        /**
         * @param url a link the user clicked on the map
         */
        public void openLink(String url) {
            linkOpener.accept(url);
        }
    }
}
