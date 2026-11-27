/*
 * The water source map: Leaflet with OpenStreetMap tiles.
 *
 * The Java side (controller.LeafletMap) calls the functions of window.cwcMap and
 * provides window.cwcBridge for calls back into Java. Marker data arrives as a JSON
 * string and is only ever put into the page with textContent, never as HTML; its
 * status only picks a class name from a fixed list.
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
        invalidateSize: noop,
        setUserLocation: noop,
        clearUserLocation: noop,
        fitAll: noop,
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
    // Animations off: JavaFX's WebKit does not reliably repaint animated zooms and fades
    // (see map-config.js), and they add nothing to a desktop map.
    var map = L.map('map', {
        worldCopyJump: true,
        fadeAnimation: false,
        zoomAnimation: false,
        markerZoomAnimation: false,
        minZoom: 2
    }).setView([20, 0], 2);
    var tiles = L.tileLayer(TILE_URL, {maxZoom: 19, attribution: ATTRIBUTION}).addTo(map);

    // Without a network connection no tile ever loads: say so instead of a silent grey map.
    // Bottom left (above the legend): the top of the map holds the zoom buttons and the
    // app's floating report buttons, the bottom right the attribution.
    var tilesLoaded = 0;
    var offlineNotice = L.control({position: 'bottomleft'});
    offlineNotice.onAdd = function () {
        var box = L.DomUtil.create('div', 'cwc-offline');
        box.setAttribute('role', 'status');
        box.textContent = 'Map tiles unavailable \u2013 check your internet connection';
        return box;
    };
    var offlineShown = false;
    tiles.on('tileload', function () {
        tilesLoaded++;
        if (offlineShown) {
            offlineNotice.remove();
            offlineShown = false;
        }
    });
    tiles.on('tileerror', function () {
        if (tilesLoaded === 0 && !offlineShown) {
            offlineNotice.addTo(map);
            offlineShown = true;
        }
    });

    var markerLayer = L.layerGroup().addTo(map);
    // The approximate location of this computer: its own layer, never counted as a marker.
    var userLayer = L.layerGroup().addTo(map);
    // The overview of all reports (the default view when no location is known).
    var reportBounds = [];
    // Set once the user moves or zooms the map, or a report is shown: from then on an
    // automatic location result never moves the map.
    var userInteracted = false;
    var centredOnUser = false;
    ['mousedown', 'wheel', 'keydown', 'touchstart', 'dblclick'].forEach(function (type) {
        map.getContainer().addEventListener(type, function () {
            userInteracted = true;
        }, true);
    });
    var markersByKey = {};
    var pickMarker = null;
    var picking = false;

    // Marker colours by condition. The page only ever sets class names from this fixed
    // list; the status word from Java picks one, and anything else counts as unknown.
    var STATUS_CLASSES = {
        success: 'cwc-status-success',
        info: 'cwc-status-info',
        warning: 'cwc-status-warning',
        danger: 'cwc-status-danger',
        unknown: 'cwc-status-unknown'
    };

    function statusClass(status) {
        return typeof status === 'string' && Object.prototype.hasOwnProperty.call(STATUS_CLASSES, status)
            ? STATUS_CLASSES[status] : STATUS_CLASSES.unknown;
    }

    // Water source reports are pins, purity reports round dots, both in the colour of
    // their condition. The icons are empty boxes drawn by map.css (no HTML content).
    var markerIcons = {};

    function markerIcon(kind, status) {
        var purity = kind === 'purity';
        var className = (purity ? 'cwc-purity-icon ' : 'cwc-source-icon ') + statusClass(status);
        if (!markerIcons[className]) {
            markerIcons[className] = purity
                ? L.divIcon({className: className, iconSize: [16, 16], iconAnchor: [8, 8], popupAnchor: [0, -8]})
                : L.divIcon({className: className, iconSize: [24, 30], iconAnchor: [12, 28], popupAnchor: [0, -26]});
        }
        return markerIcons[className];
    }

    // Legend rows for the colours; the purity names are added when purity reports are shown.
    var LEGEND_STATUSES = [
        {status: 'success', source: 'Potable', both: 'Potable / Safe'},
        {status: 'info', source: 'Clear', both: 'Clear'},
        {status: 'warning', source: 'Muddy', both: 'Muddy / Treatable'},
        {status: 'danger', source: 'Waste', both: 'Waste / Unsafe'}
    ];
    var legendWithPurity = false;

    function legendRow(box, glyphClass, text) {
        var row = L.DomUtil.create('div', 'cwc-legend-row', box);
        L.DomUtil.create('span', glyphClass, row);
        row.appendChild(document.createTextNode(text));
    }

    var legend = L.control({position: 'bottomleft'});
    legend.onAdd = function () {
        var box = L.DomUtil.create('div', 'cwc-legend');
        legendRow(box, 'cwc-legend-source', 'Water source report');
        if (legendWithPurity) {
            legendRow(box, 'cwc-legend-purity', 'Water purity report');
        }
        var heading = L.DomUtil.create('div', 'cwc-legend-heading', box);
        heading.textContent = 'Condition';
        LEGEND_STATUSES.forEach(function (entry) {
            legendRow(box, 'cwc-legend-swatch ' + STATUS_CLASSES[entry.status],
                legendWithPurity ? entry.both : entry.source);
        });
        return box;
    };
    var legendShown = false;

    function updateLegend(markerTotal, withPurity) {
        if (markerTotal === 0) {
            if (legendShown) {
                legend.remove();
                legendShown = false;
            }
            return;
        }
        if (legendShown && withPurity === legendWithPurity) {
            return;
        }
        if (legendShown) {
            legend.remove();
        }
        legendWithPurity = withPurity;
        legend.addTo(map);
        legendShown = true;
    }

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

    // Condition values that get a coloured badge in the popup; anything else is plain text.
    var CONDITION_BADGES = {
        Potable: 'ok', Safe: 'ok',
        Clear: 'info',
        Muddy: 'warn', Treatable: 'warn',
        Waste: 'bad', Unsafe: 'bad'
    };

    function valueCell(field) {
        var cell = element('td');
        var tone = field.label === 'Condition' && Object.prototype.hasOwnProperty.call(CONDITION_BADGES, field.value)
            ? CONDITION_BADGES[field.value] : null;
        if (tone) {
            var badge = element('span', field.value);
            badge.className = 'cwc-badge cwc-badge-' + tone;
            cell.appendChild(badge);
        } else {
            cell.textContent = field.value;
        }
        return cell;
    }

    function popupContent(marker) {
        var root = element('div');
        root.className = 'cwc-popup';
        root.appendChild(element('h3', marker.title));
        var table = element('table');
        marker.fields.forEach(function (field) {
            var row = element('tr');
            row.appendChild(element('th', field.label));
            row.appendChild(valueCell(field));
            table.appendChild(row);
        });
        root.appendChild(table);
        var details = element('button', 'Details');
        details.type = 'button';
        details.className = 'cwc-details';
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
        var purityCount = 0;
        markers.forEach(function (m) {
            var options = {title: m.title, alt: m.title, icon: markerIcon(m.kind, m.status)};
            if (m.kind === 'purity') {
                // Keep the small purity dots above the larger source pins.
                options.zIndexOffset = 1000;
                purityCount++;
            }
            var marker = L.marker([m.lat, m.lon], options);
            marker.bindPopup(popupContent(m));
            marker.addTo(markerLayer);
            markersByKey[key(m.kind, m.id)] = marker;
            bounds.push([m.lat, m.lon]);
        });
        updateLegend(markers.length, purityCount > 0);
        reportBounds = bounds;
        if (!centredOnUser) {
            fitReports();
        }
        return markers.length;
    }

    function fitReports() {
        if (reportBounds.length > 0) {
            map.fitBounds(reportBounds, {maxZoom: 12, padding: [30, 30], animate: false});
        }
    }

    function fitAll() {
        userInteracted = true;
        fitReports();
        return true;
    }

    function finiteIn(value, min, max) {
        return typeof value === 'number' && isFinite(value) && value >= min && value <= max;
    }

    // json: {"lat", "lon", "accuracyM", "kind": "ip" | "timezone"}; mode: "auto" (only
    // if the user has not moved the map yet), "force" or "none". Returns whether the
    // map was recentred.
    function setUserLocation(json, mode) {
        var where;
        try {
            where = JSON.parse(json);
        } catch (e) {
            return false;
        }
        if (!where || !finiteIn(where.lat, -90, 90) || !finiteIn(where.lon, -180, 180)
                || !finiteIn(where.accuracyM, 1, 20000000)
                || (where.kind !== 'ip' && where.kind !== 'timezone')) {
            return false;
        }
        var timeZone = where.kind === 'timezone';
        userLayer.clearLayers();
        L.circle([where.lat, where.lon], {
            radius: where.accuracyM,
            className: 'cwc-user-accuracy' + (timeZone ? ' cwc-user-accuracy-tz' : ''),
            interactive: false,
            color: '#0e7490',
            weight: 1.5,
            opacity: 0.8,
            dashArray: timeZone ? '6 6' : null,
            fillColor: '#06b6d4',
            fillOpacity: 0.10
        }).addTo(userLayer);
        L.circleMarker([where.lat, where.lon], {
            radius: 7,
            className: 'cwc-user-dot',
            interactive: false,
            color: '#ffffff',
            weight: 3,
            fillColor: '#0e7490',
            fillOpacity: 1
        }).addTo(userLayer);
        var recenter = mode === 'force' || (mode === 'auto' && !userInteracted);
        if (recenter) {
            map.setView([where.lat, where.lon], timeZone ? 5 : 10, {animate: false});
            centredOnUser = true;
        }
        return recenter;
    }

    function clearUserLocation() {
        userLayer.clearLayers();
        return true;
    }

    function focusMarker(kind, id) {
        var marker = markersByKey[key(kind, id)];
        if (!marker) {
            return false;
        }
        userInteracted = true;
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

    function pickAt(event) {
        var latlng = event.latlng.wrap();
        placePickMarker(latlng);
        if (bridge()) {
            bridge().pickLocation(latlng.lat.toFixed(6), latlng.lng.toFixed(6));
        }
    }

    // Safe to call more than once: the click handler is only registered the first time.
    function enablePicking(lat, lon) {
        if (!picking) {
            picking = true;
            L.DomUtil.addClass(map.getContainer(), 'cwc-picking');
            map.on('click', pickAt);
        }
        if (typeof lat === 'number' && typeof lon === 'number') {
            placePickMarker([lat, lon]);
            map.setView([lat, lon], 13, {animate: false});
        }
        return true;
    }

    window.cwcMap = {
        setMarkers: setMarkers,
        focusMarker: focusMarker,
        setUserLocation: setUserLocation,
        clearUserLocation: clearUserLocation,
        fitAll: fitAll,
        enablePicking: enablePicking,
        invalidateSize: function () {
            map.invalidateSize({pan: false});
            return true;
        },
        markerCount: function () {
            return Object.keys(markersByKey).length;
        }
    };
}());
