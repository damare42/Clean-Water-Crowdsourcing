/*
 * Loaded before leaflet.js. The WebKit in JavaFX does not repaint Leaflet's composited
 * (translate3d) layers when tiles arrive after a zoom, fitBounds or resize, which leaves
 * parts of the map grey. Making Leaflet position its panes and tiles with plain 2D
 * offsets avoids that.
 */
window.L_DISABLE_3D = true;
