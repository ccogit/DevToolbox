# Skill: Implementierung nach Spezifikation und Standards

Dieser Skill beschreibt, **wie** du eine Aufgabe vom Typ „Implementierung nach Spezifikation“ bearbeitest.
**Was** umzusetzen ist – Entität, Zielprojekt, Phasen, zu erstellende Artefakte –, steht in der Aufgabe-Datei
(„Aufgabe: …“).

## Grundprinzip: Implementierung nach Beispielen

Du implementierst **nicht frei**, sondern **anhand von Beispielen**. Die Aufgabe enthält im Abschnitt „Standards“
die Vorlagen des Projekts: abgeleitet von einer bereits umgesetzten Entität (Beispiel-Entität) und vom Team
überarbeitet. Sie sind die verbindliche **strukturelle Vorlage**:

| Was | Woher |
|---|---|
| Aufbau der Klassen, Basisklassen, Schnittstellen, Annotationen, Paketstruktur, Namens- und Kommentarkonventionen, Stil der Logik (SQL, RowMapper, Anreicherung, Mapping, Schreiben, Fehlerbehandlung) | **Standards** (Vorlagen) |
| Attribute, Datentypen, Quellen (Tabelle.Spalte), Selektion, Anreicherung, Mapping-Regeln, Wertumsetzungen, Zielfelder, Defaults, Sonderfälle | **Spezifikation** (PDF) |

Jedes neue Artefakt entsteht, indem du die Vorlage aus der Tabelle „Zu erstellende Artefakte“ kopierst, die
Platzhalter ersetzt (`{{Entitaet}}`, `{{entitaet}}`, `{{ENTITAET}}` – die Werte stehen am Anfang des Abschnitts
„Standards“) und den fachlichen Inhalt gemäß Spezifikation einsetzt. Felder und fachliche Logik der Vorlagen sind
Beispiele der Beispiel-Entität; sie werden durch die Attribute und Regeln der Spezifikation ersetzt. Die Spalte
**„Hinweise“** der Artefakttabelle enthält verbindliche Vorgaben des Teams. Weicht eine
neue Klasse strukturell von ihrer Vorlage ab, muss die Spezifikation das verlangen.

## Eingaben

- **Aufgabe** („Aufgabe: …“): Zielprojekt, Entität, Phasen, die Tabelle „Zu erstellende Artefakte“ (neue Klasse,
  Package, Vorlage, Basisklasse/Schnittstellen, Annotationen) und die Standards (Quelltext der Vorlagen und
  Basisklassen).
- **Spezifikation** (PDF): Ergebnis der Spezifikation aus dem Referenzprojekt, vom Fachbereich geprüft und
  geändert. Sie ist für den **Inhalt** maßgeblich – Änderungen des Fachbereichs haben Vorrang vor allem, was du aus
  den Vorlagen oder dem Referenzprojekt ableiten würdest. Je Phase sind relevant:

  | Phase | Abschnitte der Spezifikation | Zu implementieren |
  |---|---|---|
  | Read | Attributtabelle Read und Satzregeln Read | Datenobjekte (TO, PO, Entity …), Repository und Einleselogik |
  | Process | Attributtabelle Process und Satzregeln Process | Verarbeitungs-/Mapping-Logik |
  | Write | Feldtabelle Write und Satzregeln Write | Schreiblogik |

  Die **Zielattribute** (Abschnitt „Zielattribute (Zielmodell)“ der Aufgabe, bei Phase Process) stammen aus dem
  Zielmodell des Projekts und sind maßgeblich dafür, **welche** Zielfelder es gibt; die Spezifikation legt fest,
  **wie** sie befüllt werden.

  Die **OPL** der Spezifikation enthält offene Punkte; die Spalte „OPL“ der Regeln verweist darauf. Vom Fachbereich
  beantwortete Punkte setzt du wie beantwortet um.

  Phasen, die die Aufgabe nicht nennt, implementierst du nicht. Werden für Process oder Write Datenobjekte der Entität
  benötigt, die nicht Teil der Aufgabe sind, gehst du davon aus, dass sie nach den Namen in der Artefakttabelle bzw.
  den Konventionen der Vorlagen bereits existieren.

## Arbeitsregeln

1. **Struktur aus der Vorlage, Inhalt aus der Spezifikation.** Nichts an der Struktur „verbessern“, keine neuen
   Muster, Bibliotheken oder Hilfsklassen einführen, die in den Vorlagen nicht vorkommen.
2. **Jedes Attribut der Spezifikation wird umgesetzt** – in jedem Datenobjekt, in dem die Vorlage vergleichbare
   Attribute führt (TO, PO, Entity …), und in der Logik, die es liest, mappt oder schreibt. Attribute, die die
   Spezifikation als „nicht gelesen“ ausweist, werden nicht angelegt.
3. **Zielfelder nach Zielmodell.** Bei Process hat jedes Zielattribut des Zielmodells ein Feld im Zielobjekt und eine
   Befüllung im Mapping – nicht mehr und nicht weniger. Zielattribute ohne Regel in der Spezifikation, Regeln ohne
   Zielattribut und abweichende Namen werden OPL-Punkte; Namen folgen dem Zielmodell.
4. **Datentypen wie in der Vorlage abbilden.** Leite die Typ-Zuordnung (z. B. `DECIMAL` → `BigDecimal`,
   `CHAR` → `String`, `DATE` → `LocalDate`) aus den Vorlagen ab; Spalten- und Feldnamen folgen deren Konvention.
5. **Logik wie in der Vorlage:** gleiche Klassenaufteilung (Read: Repository – eines je Entität –, Reader,
   RowMapper, Anreicherer …; Process: Processor, Mapper, Converter …; Write: Writer …), gleiche Art der SQL-Ablage
   und Parameter, gleiche Fehlerbehandlung. Selektion, Anreicherung, Mapping-Regeln und Schreiblogik gemäß
   Spezifikation.
6. **Basisklassen und Schnittstellen** aus dem Abschnitt „Standards“ werden verwendet, nicht neu angelegt.
7. **Vollständiger Code.** Jede Datei vollständig und kompilierbar ausgeben – keine Auslassungen wie `...` oder
   „analog zu oben“.
8. **Alle offenen Punkte in die OPL.** Was Spezifikation oder Vorlage nicht klären (z. B. unbeantwortete OPL-Punkte
   der Spezifikation), setzt du nach bester Annahme um und machst es zu genau einem Punkt der **OPL** des Ergebnisses
   (OPL-1, OPL-2, …). Im Code steht an der Stelle `// TODO OPL-n: <kurz>`; in den Ergebnistabellen verweist die
   Spalte „OPL“ darauf. Offene Punkte stehen nirgends sonst.
9. **Keine Redundanz.** Jede Information steht genau einmal: der Code in den Dateien, die Zuordnung der Attribute in
   „Umsetzung je Attribut“, Abweichungen von der Vorlage in „Artefakte“, offene Punkte in der OPL. Keine weiteren
   Übersichten, die diese Inhalte wiederholen.
10. **Produktspezifisches** aus der Spezifikation (Prüfbedarf „[PRÜFEN] produktspezifisch …“) setzt du nur um, wenn
   die Spezifikation es für das Zielprojekt bestätigt; sonst wird es ein OPL-Punkt.

## Vorgehen

1. Lies die Aufgabe und die Tabelle „Zu erstellende Artefakte“.
2. Lies die Vorlagen und Basisklassen. Notiere die Typ-Zuordnung und das Muster der Logik der Beispiel-Entität.
3. Lies in der Spezifikation die Abschnitte der zu implementierenden Phasen (siehe Tabelle oben) und die OPL.
4. **Read:** Erstelle die Datenobjekte (TO, PO, Entity …) mit den Attributen der Spezifikation, das Repository und
   die Einleselogik, sodass jedes Attribut wie spezifiziert gelesen bzw. angereichert wird.
5. **Process:** Erstelle die Verarbeitungslogik, sodass jedes Zielattribut des Zielmodells wie in der
   Attributtabelle Process der Spezifikation befüllt wird (gemappt, umgeschlüsselt, berechnet oder validiert).
6. **Write:** Erstelle die Schreiblogik für alle Zielfelder und Satzregeln der Spezifikation.
7. Prüfe die Abdeckung: Jedes Attribut der Spezifikation ist in allen implementierten Phasen berücksichtigt, jede
   offene Stelle hat einen OPL-Punkt.

## Ausgabeformat

**Abgabe als ZIP:** Erzeuge eine ZIP-Datei mit dem Namen aus der Aufgabe (Abschnitt „Abgabe“) und stelle sie zum
Download bereit. Jede erstellte Datei liegt **genau an ihrem Pfad aus der Spalte „Datei“** der Artefakttabelle
(relativ zum Root des Zielprojekts, bei mehrmoduligen Projekten inkl. Modulordner), sodass das ZIP direkt über den
Root entpackt werden kann. Zusätzliche Artefakte folgen dem Pfadmuster der ähnlichsten Vorlage. Dazu
`IMPLEMENTIERUNG.md` im obersten Ordner mit „Umsetzung je Attribut“, „Artefakte“ und der OPL – sonst nichts. Im Chat steht nur eine kurze Übersicht; Code und Tabellen wiederholst du dort nicht.

Kannst du keine Datei erzeugen, gib stattdessen je Datei eine Überschrift mit dem vollständigen Pfad und darunter den
Inhalt als Codeblock aus – zuletzt `IMPLEMENTIERUNG.md`.
