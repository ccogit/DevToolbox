# Skill: Spezifikation aus Referenzprojekt

Dieser Skill beschreibt, **wie** du eine Aufgabe vom Typ „Spezifikation aus Referenzprojekt“ bearbeitest.
**Was** zu tun ist – fachliche Entität, zu betrachtende Phasen, Detailgrad, Zusatzkontext und die Gliederung des
Ergebnisses –, steht in der Aufgabe-Datei („Aufgabe: …“). Lies sie zuerst.

## Ausgangslage

Wir migrieren Versicherungsverträge in ein neues System, Produkt für Produkt (z. B. Wohngebäude, Hausrat, Privat/
Landwirtschaft/Gewerbe). Das **Referenzprojekt** ist eine bereits umgesetzte, sehr ähnliche Migration eines anderen
Produkts. Das **Zielprojekt** ist die Migration, die jetzt entsteht. Die Logik des Referenzprojekts zu einzelnen
fachlichen Entitäten (z. B. Risiko) soll ins Zielprojekt übernommen werden.

Deine Aufgabe ist jeweils eine **fachliche Spezifikation**, die aus dem Code des Referenzprojekts beschreibt,
wie die Entität der Aufgabe verarbeitet wird – je Phase der Batch-Verarbeitung (Spring Batch: Read = ItemReader inkl.
Anreicherung, Process = ItemProcessor/Mapping, Write = ItemWriter) **eine** Attributtabelle. Der Fachbereich prüft
und ändert sie; danach dient sie als Vorgabe für die Implementierung.

## Eingaben

- **Aufgabe** („Aufgabe: …“): fachliche Entität, Suchbegriffe, Phasen, Detailgrad, Zusatzkontext, Gliederung.
- **Code-Kontext** („Code-Kontext: …“) mit zwei Teilen:
  - **Attributverzeichnis** – alle Attribute der Entität. Primärquelle ist die DDL der Altdatenbank (Tabellen,
    Spalten, Typen, Schlüssel, Bezeichnungen); liegt keine vor, stammen die Attribute aus den Datenstrukturen im
    Code (TOs, POs, DTOs, Entities). Das Verzeichnis ist deine **Checkliste**.
  - **Code** – jede Quelldatei steht zwischen `===== BEGIN FILE: <Pfad> =====` und `===== END FILE: <Pfad> =====`.
    Das Inhaltsverzeichnis listet alle Dateien mit Phase und Grund der Aufnahme. Die Phasenzuordnung ist nur ein
    Hinweis – maßgeblich ist, was der Code tut. „Referenzen“ sind Klassen ohne Suchbegriff, die von Trefferdateien
    verwendet werden (meist Datenstrukturen).
- Fehlt eine der in der Aufgabe genannten Dateien, weise zuerst darauf hin und arbeite mit dem Vorhandenen weiter;
  die Lücke wird ein OPL-Punkt.

## Arbeitsregeln

1. **Keine Redundanz.** Jede Information steht genau einmal. Keine Tabelle wiederholt Inhalte einer anderen; keine
   zusätzlichen Sichten (Abläufe, Prüflisten, Zusammenstellungen), die nur Zeilen anderer Tabellen neu ordnen.
2. **Eine Attributtabelle je Phase.** Jedes Attribut des Attributverzeichnisses bekommt genau eine Zeile – auch wenn
   es nicht gelesen oder nicht gemappt wird. Quellen, Regeln, Schritte, Defaults, Sonderfälle, Prüfbedarf, OPL-Bezug
   und Beleg sind **Spalten dieser Zeile**, keine eigenen Tabellen. Daneben gibt es je Phase nur die **Satzregeln**
   für Regeln, die nicht an einem Attribut hängen (z. B. Selektion, Filter, Sortierung).
3. **Flache Attributnamen.** Verschachtelte Datenstrukturen werden nicht in Untertabellen zerlegt, sondern mit
   Punkt-Schreibweise aufgeführt (z. B. `vertrag.tarif`, `lage.plz`), unmittelbar unter dem übergeordneten Attribut.
4. **Prüfbedarf kennzeichnen.** Jede Regel bekommt [PRÜFEN], [TECHNISCH] oder [1:1] (siehe „Prüfbedarf je Regel“).
5. **Alle Fragen in die OPL.** Jede offene Frage, Annahme oder Lücke wird genau ein Punkt der **Offene-Punkte-Liste
   (OPL)** am Ende, nummeriert OPL-1, OPL-2, … Die Spalte „OPL“ jeder Regel nennt die Nummern der Punkte, die diese
   Regel betreffen (leer, wenn es keine gibt). Fragen stehen nirgends sonst – auch nicht als [OFFEN] im Text.
6. **Nur Belegbares.** Jede Regel hat einen Beleg: `Klasse.Methode` (bei SQL/XML die Datei und das Statement bzw.
   Element). Der Beleg steht nur in der Spalte „Beleg“.
7. **Nichts erfinden.** Was der Code nicht zeigt (Datenbankinhalte, externe Konfiguration, nicht mitgelieferte
   Klassen, Werte aus Schlüsseltabellen), wird ein OPL-Punkt.
8. **Fachlich formulieren.** Regeln stehen in Fachsprache; Code nur in kurzen Ausdrücken, die eine Regel exakt
   definieren (z. B. eine Bedingung).
9. **Fokus halten.** Nur die Phasen der Aufgabe. Technische Infrastruktur (Logging, Monitoring, allgemeine
   Hilfsklassen ohne Fachbezug) ignorieren.

## Prüfbedarf je Regel

Jede Regel – jede Zeile der Attributtabellen und der Satzregeln – bekommt in der Spalte **„Prüfbedarf“** eines
dieser Kennzeichen, bei [PRÜFEN] gefolgt vom Grund in wenigen Worten (z. B. „[PRÜFEN] Default BAK1 bei leerem
Wert“, „[PRÜFEN] produktspezifisch: Produkt WG2010“):

| Kennzeichen | Bedeutung | Typische Merkmale im Code |
|---|---|---|
| **[PRÜFEN]** | Fachliche Logik – bei der Übertragung ins Zielprojekt fachlich prüfen | Bedingungen/Verzweigungen, Berechnungen, Umschlüsselungen und Codelisten, fachliche Defaults, Filter und Validierungen, Bezug auf Produkt, Tarif oder Status, Sonderfälle, Nachlesen/Anreicherung mit Auswahllogik, hart codierte fachliche Werte |
| **[TECHNISCH]** | Technische Aufbereitung ohne fachliche Bedeutung | Trim, Typ- oder Formatumwandlung (z. B. `DATE` → `LocalDate`), Zeichensatz, Null-Behandlung ohne fachlichen Default, technische IDs |
| **[1:1]** | Reine Übernahme eines Attributs ohne Logik | Wert unverändert, nur anderes Feld bzw. andere Spalte |

- **Im Zweifel [PRÜFEN].** Hat ein Attribut mehrere Regeln, gilt das strengste Kennzeichen für die Zeile.
- Am Produkt des Referenzprojekts hängende Logik (Produkt-/Tarifschlüssel, hart codierte Werte) ist [PRÜFEN] mit
  dem Grund „produktspezifisch: …“.
- Filter, Validierungen und Wertumsetzungen sind immer [PRÜFEN].

## Detailgrad

Die Aufgabe nennt einen von zwei Detailgraden. Die **Tabellen sind in beiden gleich**; es ändert sich nur, wie
genau die Regeln in den Zellen beschrieben sind:

| Detailgrad | Zellen „Einlesen“ / „Mapping“ / „Schreiben“ | Zusammenfassung |
|---|---|---|
| **kompakt** | die Regel in einem Satz | höchstens 5 Sätze |
| **ausführlich** | die einzelnen Schritte, nummeriert in der Zelle, je Schritt mit Bedingung und Klasse.Methode | 5–10 Sätze |

Nennt die Aufgabe keinen Detailgrad, arbeite ausführlich.

## Vorgehen

1. Lies die Aufgabe: Entität, Phasen, Detailgrad, Zusatzkontext, Gliederung.
2. Lies das Attributverzeichnis. Nutze Bezeichnungen und Kommentare aus der DDL für die fachliche Bedeutung.
3. Verschaffe dir anhand des Inhaltsverzeichnisses und der Job-/Step-Konfiguration einen Überblick über den Ablauf.
4. Arbeite die Phasen der Aufgabe nacheinander ab: Suche zu jedem Attribut die Stellen im Code, die es lesen,
   anreichern, mappen oder schreiben, und fülle seine Zeile. Notiere jede Frage sofort als OPL-Punkt und trage die
   Nummer in der Zeile ein.
5. Nennt die Aufgabe mehrere Phasen, prüfe die Durchgängigkeit: Attribute, die gelesen, aber nie geschrieben werden,
   und Zielfelder ohne erkennbare Herkunft werden OPL-Punkte (keine eigene Tabelle).
6. Prüfe vor der Ausgabe, dass keine Information doppelt vorkommt.

## Phasen

{{PHASE_SECTIONS}}

## Ausgabeformat

Halte dich an die Gliederung aus der Aufgabe. Fehlt sie, verwende: Zusammenfassung, Ablaufübersicht, je betrachteter
Phase die Attributtabelle und die Satzregeln, OPL.

Die **OPL** ist eine Tabelle: | OPL-Nr | Frage | Betrifft (Phase · Attribut bzw. Satzregel) | Warum offen (was der
Code nicht zeigt) | Annahme bis zur Klärung |.

Wenn eine Phase zur Entität im Code keine Logik enthält, schreibe das ausdrücklich in ihren Abschnitt, statt ihn
wegzulassen.
