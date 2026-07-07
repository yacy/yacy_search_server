# YaCy-Lokalisierung: Systematik & Syntax der `.lng`-Dateien

Dieses Verzeichnis enthält die Übersetzungsdateien für die YaCy-Weboberfläche.
Dieses Dokument beschreibt, wie das Übersetzungssystem funktioniert und wie
`.lng`-Dateien aufgebaut sein müssen, damit Übersetzungen zur Laufzeit greifen.

---

## 1. Überblick

- Die Oberfläche (die Dateien unter `htroot/`) ist **auf Englisch** geschrieben.
  Das Englische ist die Referenz/Quelle.
- Pro Sprache gibt es eine Datei `locales/<code>.lng` (z. B. `de.lng`), die
  englische Textstellen auf die Zielsprache abbildet.
- YaCy übersetzt **nicht** live beim Ausliefern jeder Anfrage, sondern erzeugt
  beim Aktivieren einer Sprache **übersetzte Kopien** der `htroot`-Dateien nach
  `DATA/LOCALE/htroot/<code>/` und liefert diese aus.
- Die Übersetzung selbst ist **reine Textersetzung** (Substring-Replace),
  **kein** Template-System. Das hat wichtige Konsequenzen für die Syntax der
  Schlüssel (siehe Abschnitt 6).

Vorhandene Sprachdateien:

```
de.lng el.lng es.lng fr.lng hi.lng it.lng ja.lng ru.lng sk.lng tr.lng uk.lng zh.lng
```

`de.lng` ist üblicherweise die vollständigste Übersetzung und dient als
Referenz. Zusätzlich existiert `master.lng.xlf` (siehe Abschnitt 8).

---

## 2. Grundaufbau einer `.lng`-Datei

Eine `.lng`-Datei besteht aus einem Kopf-Kommentar, gefolgt von **Abschnitten
pro Quelldatei**:

```
#File: ConfigBasic.html
#---------------------------
Access Configuration==Zugangseinstellungen
Basic Configuration==Grundkonfiguration
Set Configuration==Konfiguration speichern
#-----------------------------

#File: env/templates/submenuMaintenance.template
#---------------------------
Performance==Performance
Web Cache==Web-Cache
#-----------------------------
```

Bestandteile:

| Element | Bedeutung |
|---|---|
| `#File: <pfad>` | Beginn eines Abschnitts. `<pfad>` ist der **htroot-relative** Pfad der Quelldatei (z. B. `ConfigBasic.html`, `env/templates/header.template`, `Settings_Proxy.inc`). |
| `#---------------------------` | Trenner nach der Kopfzeile (dekorativ, üblich aber nicht zwingend geparst). |
| `original==übersetzung` | Ein Übersetzungseintrag (siehe Abschnitt 3). |
| `#-----------------------------` | Abschluss des Abschnitts (dekorativ). |
| `# ...` | Kommentarzeile (Zeilen, die mit `#` beginnen, sind keine Einträge). |
| Leerzeile | Trennt Abschnitte optisch. |

Wichtig:
- **Kodierung: UTF-8.**
- **Ein Abschnitt = eine Quelldatei.** Der `#File:`-Pfad muss exakt einer real
  existierenden Datei unter `htroot/` entsprechen. Abschnitte für gelöschte
  Dateien sind tote Einträge und sollten entfernt werden.
- Die Übersetzung gilt **nur** für die im `#File:` genannte Datei. Wenn derselbe
  englische Text auf mehreren Seiten vorkommt, braucht **jede** Seite ihren
  eigenen Abschnitt mit diesem Eintrag (Übersetzung ist pro Datei-Abschnitt).

### Abschnitts-Header: nur `#File:`
Der Runtime-Loader (`Translator.loadTranslationsLists`) erkennt derzeit **nur**
`#File:` als Abschnitts-Header. Zeilen mit anderen Präfixen, z. B. `#Dosya:`,
werden wie normale Kommentarzeilen behandelt; die folgenden Einträge landen dann
nicht im beabsichtigten Dateiabschnitt und greifen zur Laufzeit nicht korrekt.

Hinweis: Ältere oder importierte Dateien können lokalisierte Header wie
`#Dosya:` enthalten. Solche Header müssen vor einem Runtime-Test nach `#File:`
normalisiert werden (oder der Java-Loader muss explizit erweitert werden).
Andere `#Xxx:`-Präfixe (`#YaCy:`, `#Subject:`, `#URL:` …) sind ebenfalls keine
Header. Da alle Zeilen mit `#` ignoriert werden, sind auch `#key==wert`-Zeilen
auskommentierte Einträge und keine aktiven Übersetzungen.

---

## 3. Eintrags-Syntax: `original==übersetzung`

```
Basic Configuration==Grundkonfiguration
```

- Trennzeichen ist **`==`** (doppeltes Gleichheitszeichen). Alles **links** vom
  ersten `==` ist der **Schlüssel** (der zu suchende englische Text), alles
  **rechts** die Übersetzung.
- Der **Schlüssel darf kein `==` enthalten** (der Split erfolgt am ersten `==`).
- **Genau eine Zeile pro Eintrag.** Schlüssel und Übersetzung dürfen **keine
  Zeilenumbrüche** enthalten (das Format ist zeilenbasiert).
- HTML-Entities werden **wörtlich** übernommen (z. B. `Fran&ccedil;ais`,
  `&amp;`, `&nbsp;`), da sie auch so in der Quelle stehen.
- Eine leere Übersetzung (`key==`) bzw. eine identische (`Chat==Chat`) ist
  technisch erlaubt, aber vermeide **überflüssige** Einträge – lege keine
  Abschnitte oder Einträge an, die nichts übersetzen.

### Sonder-Einträge im Abschnitt `ConfigLanguage_p.html`
Diese steuern die Anzeige der Sprache in der Sprachauswahl:

```
<!-- lang -->default(english)==Deutsch
<!-- author -->==Roland Ramthun, Oliver Wunder, ...
<!-- maintainer -->==&lt;webmaster@daburna.de&gt;
```

- `<!-- lang -->…` — der **Anzeigename** der Sprache.
- `<!-- author -->…` — Beitragende.
- `<!-- maintainer -->…` — Pflege-Kontakt.

---

## 4. Wie die Übersetzung technisch funktioniert

Relevante Klassen:
`net.yacy.data.Translator`,
`net.yacy.utils.translation.TranslationManager` / `TranslatorXliff` / `TranslatorUtil`.

Ablauf (`Translator.translate` / `translateFilesRecursive`):

1. Beim Aktivieren einer Sprache werden alle Quelldateien mit den Endungen
   **`html`, `template`, `inc`** rekursiv durch `htroot/` verarbeitet und als
   übersetzte Kopien nach `DATA/LOCALE/htroot/<code>/` geschrieben.
2. Für jede Datei wird der zugehörige `#File:`-Abschnitt geladen. Für **jeden**
   Eintrag `source==target` wird im Dateiinhalt **jedes Vorkommen** von `source`
   gesucht (`indexOf`) und durch `target` ersetzt (`replace`).
3. Vor jeder Ersetzung greift eine **Wortgrenzen-Prüfung**: das Zeichen direkt
   vor und nach dem Treffer muss eine „Grenze“ sein (Satzzeichen oder
   unsichtbares Zeichen — dazu zählen u. a. Leerzeichen sowie `<` und `>`).
   Dadurch wird verhindert, dass `bug` in `mybugfix` ersetzt wird, während
   `>English<` (umschlossen von `>`/`<`) korrekt getroffen wird.

**Kernaussage:** Ein Schlüssel wird genau dann übersetzt, wenn er als
**exakter Teilstring** im Dateiinhalt vorkommt und an Wortgrenzen liegt.
Es gibt **keinen** automatischen Extraktor, der „übersetzbare Strings“
erkennt — die Schlüssel werden von Hand gepflegt.

Wichtig: Beim Erzeugen der lokalisierten Dateien werden nur Quelldateien
geschrieben, für die in der Sprachdatei ein passender `#File:`-Abschnitt
existiert. Fehlt der Abschnitt, wird diese Datei nicht als lokalisierte Kopie
erzeugt.

Die erzeugten Seiten liegen zur Laufzeit unter `DATA/LOCALE/htroot/<sprache>`.
Beim Umschalten oder automatischen Refresh einer Sprache muss dieser Ordner
vorher gelöscht werden; sonst können nicht mehr erzeugte Altdateien weiterhin
ausgeliefert werden. Aktuelle YaCy-Versionen erledigen das beim Sprachwechsel
und beim versionsbedingten Startup-Refresh automatisch.

---

## 5. Template-Markup in den Quelldateien

Die `htroot`-Dateien enthalten Server-Template-Markup, das **vor** oder
**unabhängig von** der Übersetzung durch die Servlet-Engine ersetzt wird.
Ein Übersetzungsschlüssel darf dieses Markup **nicht überspannen**:

| Form | Bedeutung |
|---|---|
| `#[name]#` | Einzelwert (Platzhalter), wird durch einen Laufzeitwert ersetzt. |
| `#(name)#A::B::…#(/name)#` | Fallunterscheidung/Alternativen (A für Fall 0, B für Fall 1 …). |
| `#{name}#…#{/name}#` | Wiederholung/Aufzählung (Schleife). |
| `#%pfad%#` | Einbindung eines anderen Templates (z. B. `#%env/templates/header.template%#`). |

Konsequenz für Schlüssel: Übersetzbarer Text endet **an** solchen Markup-Grenzen.
Beispiel — die Quelle enthält:

```html
This path can be accessed at #[path]#
```

Der brauchbare Schlüssel ist daher `This path can be accessed at ` (mit dem
Leerzeichen, bis zum `#[path]#`), **nicht** die ganze Zeile inkl. `#[path]#`.

---

## 6. Regeln für gute Schlüssel (Authoring)

1. **Exakter Teilstring.** Der Schlüssel muss **zeichengenau** im Roh-Quelltext
   der Datei vorkommen (inkl. HTML-Entities, Groß-/Kleinschreibung,
   Interpunktion). Am einfachsten prüfbar mit `content.indexOf(key) >= 0`.
2. **Einzeilig.** Keine Zeilenumbrüche im Schlüssel.
3. **Inline-Tags bleiben im Schlüssel.** Für zusammenhängende Sätze mit
   Inline-Auszeichnung wird der ganze Satz **inklusive** der Inline-Tags zu
   einem Schlüssel, damit die deutsche Wortstellung passt:
   ```
   ... edited in the <a href="IndexSchema_p.html">Schema Editor</a>.==... im <a href="IndexSchema_p.html">Schema-Editor</a> bearbeitet werden.
   ```
   Als Inline gelten u. a. `a, em, strong, b, i, code, kbd, abbr, span, sup,
   sub, small, var, samp, br`. An **Block-Tags** (`p, div, li, td, h1..h6,
   option, label, fieldset, …`) und an Template-Markup wird getrennt.
4. **Menü-Einträge als Klartext.** Menü-Links wie
   `<a href="X.html" ...>LLM Selection</a>` werden als reiner Text
   `LLM Selection==LLM-Auswahl` gepflegt (der `<a>`-Wrapper enthält oft
   Template-Markup und gehört nicht in den Schlüssel).
5. **Reihenfolge: länger/spezifischer zuerst.** Die Einträge werden in
   Datei-Reihenfolge angewandt. Ist ein kurzer Schlüssel Teilstring eines
   längeren desselben Abschnitts, muss der **längere zuerst** stehen — sonst
   „zerschießt“ die kurze Ersetzung den längeren Treffer.
   Beispiel: `Index Export` **vor** `Export` einordnen.
6. **Keine Zerlegung über Markup hinweg** (siehe Abschnitt 5).

---

## 7. Was übersetzt wird — und was nicht

**Übersetzen:** Seiten mit sichtbarem Oberflächentext (`*_p.html`, Konfig-Seiten,
`*.inc`-Includes, `env/templates/submenu*.template` und andere UI-Templates).

**Nicht übersetzen** (keine leeren Abschnitte anlegen):
- **Daten-/Protokoll-Endpunkte:** `yacy/*.html`, `api/push_p.html`,
  `api/share.html` u. ä. (liefern XML/JSON, keine UI).
- **Inhaltsleere Templates:** `env/templates/footer.template`,
  `…/simplefooter.template`, `…/embedded*.template` usw.
- **Reine Code-/Beispielblöcke** innerhalb einer Seite (z. B. `curl`-Befehle,
  JSON-Snippets) — nur die umgebende Prosa übersetzen.
- **Test-/Demo-Dateien** und rein technische Bezeichner (Feldnamen wie
  `num_ctx`, `max_tokens`, Rollennamen wie `search-query`).
- **Technische Link-Ziele, Pfade, Servlets und URLs** bleiben literal. Nicht
  übersetzen oder durch Leerzeichen beschädigen: `Network.html` bleibt
  `Network.html`, `sharedBlacklist.html` bleibt `sharedBlacklist.html`,
  `share.json` bleibt `share.json`, `styles/prosilver/template/overall_header.html`
  bleibt unverändert und URLs wie `http://localhost:8090/proxy.html?...`
  dürfen nicht lokalisiert werden.

Prüfung aus dem Repository-Root:

```bash
python3 locales/validate-locale-links.py --exclude pl.lng
```

`--exclude` ist nützlich, wenn eine Sprache parallel in einem anderen Arbeitszweig
bearbeitet wird. Für einzelne Sprachen kann `--include de.lng --include fr.lng`
verwendet werden. Ein sauberer Lauf endet mit `OK: ... no link target issues found.`

---

## 8. `master.lng.xlf`

`master.lng.xlf` ist die source-basierte XLIFF-Referenz der übersetzbaren
Strings pro Datei. Die Wahrheit für diesen Master liegt in den Quellen unter
`htroot`, nicht in bereits vorhandenen `.lng`-Dateien.

- Sie wird mit `GenerateSourceMasterXliff` aus sichtbaren Textknoten und
  ausgewählten UI-Attributen (`alt`, `title`, `placeholder`, `aria-label`,
  Button-`value`) erzeugt.
- Jeder Kandidat wird gegen den Roh-Quelltext und die Runtime-Wortgrenzen der
  Übersetzung geprüft. Nicht darstellbare `.lng`-Keys, z. B. Keys mit `==` oder
  einem abschließenden `=`, werden verworfen.
- **Nicht** von Hand mit Hash-/Zeilen-IDs pflegen — nach Änderungen an Quellen
  über das YaCy-Tooling neu erzeugen und das Delta prüfen.
- Beim Refresh wird die Zieldatei ersetzt. Stale Master-Einträge fallen dadurch
  weg, auch wenn sie noch in alten `.lng`-Dateien stehen.

Refresh aus dem Repository-Root:

```bash
java -cp 'build/classes/java/main:lib/*' \
  net.yacy.utils.translation.GenerateSourceMasterXliff \
  htroot locales/master.lng.xlf
```

Falls die Klassen noch nicht kompiliert sind, vorher `ant compile` ausführen.
Das zweite Argument ist wichtig: ohne `locales/master.lng.xlf` schreibt das Tool
standardmäßig nach `./source-master.lng.xlf` im Repository-Root. Existiert die
Zieldatei bereits, wird sie ersetzt.

### Legacy: bestandbasierter Master

`GenerateMasterXliff` erzeugt nur einen bestandbasierten Master aus vorhandenen
`.lng`-Schlüsseln, gefiltert danach, ob sie noch als Teilstring in der
jeweiligen Quelldatei vorkommen (`content.indexOf >= 0`):

```bash
java -cp 'build/classes/java/main:lib/*' \
  net.yacy.utils.translation.GenerateMasterXliff \
  locales /tmp/master-from-lng.lng.xlf
```

Dieses Tool ist nützlich zur Diagnose von Altbestand, aber nicht als
Vollständigkeitsreferenz: englische UI-Texte, die noch in keiner `.lng`-Datei als
Schlüssel vorkommen, erscheinen dort nicht.

---

## 9. Arbeitsablauf: eine Sprache vervollständigen

1. **Fehlende Seiten ermitteln:** alle UI-Dateien unter `htroot/`
   (`*.html`, `*.inc`, `*.template`) mit den `#File:`-Abschnitten der `.lng`
   abgleichen; Differenz bilden. Daten-Endpunkte/leere Templates (Abschnitt 7)
   herausfiltern.
2. **Schlüssel extrahieren:** pro Datei die sichtbaren, einzeiligen Textstellen
   gemäß den Regeln in Abschnitt 6 gewinnen (Inline-Tags behalten, an Block-Tags
   und Template-Markup trennen, Rand-Tags entfernen).
3. **Übersetzen** und Einträge `key==übersetzung` bilden.
4. **Verifizieren (Pflicht):** für **jeden** Schlüssel prüfen, dass
   `key in <roher Dateiinhalt>` gilt. Schlägt das fehl, greift die Übersetzung
   zur Laufzeit **nicht**.
5. **Einordnen:** neuen `#File:`-Abschnitt anlegen; Einträge längster-zuerst
   sortieren (Abschnitt 6, Regel 5). Neue Abschnitte können am Dateiende
   angehängt werden (die Datei ist nicht streng sortiert).
6. **Vollständigkeit beidseitig prüfen:**
   - `master.lng.xlf -> <sprache>.lng`: fehlende Source-Schlüssel ergänzen.
   - `<sprache>.lng -> master.lng.xlf`: Extras prüfen und in der Regel entfernen;
     sie sind stale oder stammen aus einem nicht frisch generierten Master.
   - Für jeden aktiven Sprach-Key prüfen: `key in htroot/<#File>`.
   - Doppelte Schlüssel und auskommentierte `#...==...`-Einträge entfernen oder
     bewusst reaktivieren.
7. **Zeilenenden beachten** (Abschnitt 10).

---

## 10. Fallstricke

- **Zeilenenden (CRLF):** Einige Dateien verwenden CRLF (`\r\n`), u. a.
  `it.lng`, `sk.lng` sowie einige `htroot`-Templates. Werkzeuge, die im
  Textmodus lesen und neu schreiben, normalisieren CRLF→LF und erzeugen einen
  Diff über die **ganze** Datei. Verwende `perl -i -pe` o. ä. bzw. arbeite
  byte-erhaltend; kontrolliere mit `git diff --numstat` (ein `+N -N` in Höhe der
  Zeilenzahl deutet auf ungewollte Newline-Normalisierung hin).
- **Synchronität über alle Sprachen:** Wird ein englischer Text in der Quelle
  korrigiert, muss der **Schlüssel** in **allen** `.lng`-Dateien analog
  angepasst werden (linke Seite von `==`), sonst passt er nicht mehr und die
  Übersetzung greift nicht.
- **Stale Keys:** Über die Zeit driften Schlüssel von der Quelle ab (Text in der
  Quelle geändert, `.lng` nicht) → der Eintrag greift nie mehr. Solche Einträge
  sollten aktualisiert oder entfernt werden.
- **Duplikate:** Doppelte `#File:`-Abschnitte oder doppelte Schlüssel innerhalb
  eines Abschnitts vermeiden.
- **`==` im Text:** Ein englischer Text mit `==` lässt sich nicht als Schlüssel
  abbilden (der Split bricht am ersten `==`).

---

## 11. Kurz-Checkliste für einen neuen Eintrag

- [ ] Schlüssel ist **exakter, einzeiliger Teilstring** der Quelldatei.
- [ ] Markup-Grenzen (`#[..]#`, `#(..)#`, `#{..}#`, `#%..%#`) nicht überspannt.
- [ ] Inline-Tags im Schlüssel belassen, an Block-Tags getrennt.
- [ ] Eintrag steht im **richtigen** `#File:`-Abschnitt.
- [ ] Längere Schlüssel stehen vor ihren kürzeren Teilstrings.
- [ ] UTF-8, korrektes Zeilenende, kein `==` im Schlüssel.
- [ ] Kein überflüssiger/leerer Eintrag.
