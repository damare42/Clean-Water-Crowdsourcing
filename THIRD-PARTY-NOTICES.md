# Third-party notices

Clean Water Crowdsourcing is released under the MIT License (see `LICENSE`).
The desktop installers also contain the third-party software listed below, each under its
own license. Licence texts that are not inside the shipped jar or runtime are reproduced
at the end of this file.

| Component | Version | License | Where it is in the installed app |
|---|---|---|---|
| Java runtime (OpenJDK, trimmed with jlink) | 21 | GPL-2.0 with Classpath Exception | `runtime/` (licences in `runtime/legal/<module>/`) |
| OpenJFX (javafx-base, javafx-graphics, javafx-controls, javafx-fxml, javafx-web, javafx-media) | 21.0.5 | GPL-2.0 with Classpath Exception | linked into `runtime/`; licences and the notices of its bundled components (WebKit, libxml2, libxslt, JPEG, Mesa, GStreamer, GLib, libffi) in `runtime/legal/openjfx/` |
| AtlantaFX (atlantafx-base) | 2.0.1 | MIT | `app/atlantafx-base-2.0.1.jar`; licence text below |
| Ikonli (ikonli-core, ikonli-javafx) | 12.4.0 | Apache-2.0 | `app/ikonli-*.jar` (licence in `META-INF/LICENSE`) |
| Ikonli Material 2 pack, with Google Material Icons fonts | 12.4.0 | Apache-2.0 | `app/ikonli-material2-pack-12.4.0.jar` (licence in `META-INF/LICENSE`) |
| SQLite JDBC driver (xerial sqlite-jdbc), with SQLite | 3.54.1.0 | Apache-2.0 (driver); SQLite is public domain | `app/sqlite-jdbc-3.54.1.0.jar` (licences in `META-INF/maven/org.xerial/sqlite-jdbc/`) |
| Leaflet | 1.9.4 | BSD-2-Clause | inside `app/cwc-desktop-*.jar` at `web/leaflet/` (licence in `web/leaflet/LICENSE`); text below |
| IANA Time Zone Database (zone coordinates) | 2026e | Public domain | `geo/zone-coordinates.tsv` inside `app/cwc-desktop-*.jar` |

Not shipped, but used at run time:

- **OpenStreetMap tiles.** The map downloads tiles from `tile.openstreetmap.org`. Map data
  © OpenStreetMap contributors, available under the Open Database License (ODbL)
  1.0, https://www.openstreetmap.org/copyright. The app shows this attribution on the map.
- **ipapi.co** (approximate location, can be switched off in Profile → Privacy): see its terms
  at https://ipapi.co/terms/.

Source code: OpenJFX, including the LGPL-licensed components it bundles, is available at
https://github.com/openjdk/jfx21u; OpenJDK at https://github.com/openjdk/jdk21u. The installers leave
out the OpenJFX media native libraries (GStreamer, FFmpeg plugins), because the app plays no
audio or video.

---

## AtlantaFX — MIT License

```
MIT License

Copyright (c) [2022] [mkpaz]

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## Leaflet — BSD 2-Clause License

```
BSD 2-Clause License

Copyright (c) 2010-2023, Volodymyr Agafonkin
Copyright (c) 2010-2011, CloudMade
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```
