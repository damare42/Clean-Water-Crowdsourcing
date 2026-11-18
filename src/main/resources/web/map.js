/*
 * The water source map: Leaflet with OpenStreetMap tiles.
 *
 * The Java side (controller.LeafletMap) calls the functions of window.cwcMap and
 * provides window.cwcBridge for calls back into Java. Marker data arrives as a JSON
 * string and is only ever put into the page with textContent, never as HTML.
 */
(function () {
    'use strict';

    var TILE_URL = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
    var ATTRIBUTION = '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors';

    function bridge() {
        return window.cwcBridge || null;
    }

    function noop() {
        return false;
    }

    // Placeholder API, so calls from Java are harmless if Leaflet failed to load.
    window.cwcMap = {
        setMarkers: noop,
        focusMarker: noop,
        enablePicking: noop,
        markerCount: function () {
            return 0;
        }
    };

    // Links (e.g. the attribution) open in the system browser, not inside the map.
    document.addEventListener('click', function (event) {
        var link = event.target && event.target.closest ? event.target.closest('a[href]') : null;
        if (!link || !/^https?:/i.test(link.getAttribute('href'))) {
            return;
        }
        event.preventDefault();
        if (bridge()) {
            bridge().openLink(link.href);
        }
    }, true);

    if (typeof L === 'undefined') {
        return;
    }

    L.Icon.Default.imagePath = 'leaflet/images/';
    var map = L.map('map', {worldCopyJump: true}).setView([20, 0], 2);
    L.tileLayer(TILE_URL, {maxZoom: 19, attribution: ATTRIBUTION}).addTo(map);

    var markerLayer = L.layerGroup().addTo(map);
    var markersByKey = {};
    var pickMarker = null;

    function key(kind, id) {
        return kind + ':' + id;
    }

    function element(tag, text) {
        var node = document.createElement(tag);
        if (text !== undefined) {
            node.textContent = text;
        }
        return node;
    }

    function popupContent(marker) {
        var root = element('div');
        root.className = 'cwc-popup';
        root.appendChild(element('h3', marker.title));
        var table = element('table');
        marker.fields.forEach(function (field) {
            var row = element('tr');
            row.appendChild(element('th', field.label));
            row.appendChild(element('td', field.value));
            table.appendChild(row);
        });
        root.appendChild(table);
        var details = element('button', 'Details');
        details.type = 'button';
        details.addEventListener('click', function () {
            if (bridge()) {
                bridge().openDetails(marker.kind, String(marker.id));
            }
        });
        root.appendChild(details);
        return root;
    }

    function setMarkers(json) {
        var markers = JSON.parse(json);
        markerLayer.clearLayers();
        markersByKey = {};
        var bounds = [];
        markers.forEach(function (m) {
            var marker = L.marker([m.lat, m.lon], {title: m.title, alt: m.title});
            marker.bindPopup(popupContent(m));
            marker.addTo(markerLayer);
            markersByKey[key(m.kind, m.id)] = marker;
            bounds.push([m.lat, m.lon]);
        });
        if (bounds.length > 0) {
            map.fitBounds(bounds, {maxZoom: 12, padding: [30, 30], animate: false});
        }
        return markers.length;
    }

    function focusMarker(kind, id) {
        var marker = markersByKey[key(kind, id)];
        if (!marker) {
            return false;
        }
        map.setView(marker.getLatLng(), Math.max(map.getZoom(), 13), {animate: false});
        marker.openPopup();
        return true;
    }

    function placePickMarker(latlng) {
        if (pickMarker) {
            pickMarker.setLatLng(latlng);
        } else {
            pickMarker = L.marker(latlng, {title: 'Chosen location', alt: 'Chosen location'}).addTo(map);
        }
    }

    function enablePicking(lat, lon) {
        L.DomUtil.addClass(map.getContainer(), 'cwc-picking');
        if (typeof lat === 'number' && typeof lon === 'number') {
            placePickMarker([lat, lon]);
            map.setView([lat, lon], 13, {animate: false});
        }
        map.on('click', function (event) {
            var latlng = event.latlng.wrap();
            placePickMarker(latlng);
            if (bridge()) {
                bridge().pickLocation(latlng.lat.toFixed(6), latlng.lng.toFixed(6));
            }
        });
        return true;
    }

    window.cwcMap = {
        setMarkers: setMarkers,
        focusMarker: focusMarker,
        enablePicking: enablePicking,
        markerCount: function () {
            return Object.keys(markersByKey).length;
        }
    };
}());
