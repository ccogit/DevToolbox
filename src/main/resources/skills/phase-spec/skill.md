# Skill: Spezifikation aus Referenzprojekt

Dieser Skill beschreibt, **wie** du eine Aufgabe vom Typ „Spezifikation aus Referenzprojekt“ bearbeitest.
**Was** zu tun ist – fachliche Entität, zu betrachtende Phasen, Zusatzkontext und die Gliederung des Ergebnisses –,
steht in der Aufgabe-Datei („Aufgabe: …“). Lies sie zuerst.

## Ausgangslage

Wir migrieren Versicherungsverträge in ein neues System, Produkt für Produkt (z. B. Wohngebäude, Hausrat, Privat/
Landwirtschaft/Gewerbe). Das **Referenzprojekt** ist eine bereits umgesetzte, sehr ähnliche Migration eines anderen
Produkts. Das **Zielprojekt** ist die Migration, die jetzt entsteht. Die Logik des Referenzprojekts zu einzelnen
fachlichen Entitäten (z. B. Risiko) soll ins Zielprojekt übernommen werden.

Deine Aufgabe ist jeweils eine **fachliche Spezifikation**, die aus dem Code des Referenzprojekts beschreibt,
wie die Entität der Aufgabe verarbeitet wird – getrennt nach den Phasen der Batch-Verarbeitung (Spring Batch:
Read = ItemReader inkl. Anreicherung, Process = ItemProcessor/Mapping, Write = ItemWriter), jede Phase in einem
eigenen Abschnitt.

## Eingaben

- **Aufgabe** („Aufgabe: …“): fachliche Entität, Suchbegriffe, zu betrachtende Phasen, Zusatzkontext, Gliederung.
- **Code-Kontext** („Code-Kontext: …“) mit zwei Teilen:
  - **Attributverzeichnis** – alle Attribute der Entität. Primärquelle ist die DDL der Altdatenbank (Tabellen,
    Spalten, Typen, Schlüssel, Bezeichnungen); liegt keine vor, stammen die Attribute aus den Datenstrukturen im
    Code (TOs, POs, DTOs, Entities). Das Verzeichnis ist deine **Checkliste**.
  - **Code** – jede Quelldatei steht zwischen `===== BEGIN FILE: <Pfad> =====` und `===== END FILE: <Pfad> =====`.
    Das Inhaltsverzeichnis listet alle Dateien mit Phase und Grund der Aufnahme. Die Phasenzuordnung ist nur ein
    Hinweis – maßgeblich ist, was der Code tut. „Referenzen“ sind Klassen ohne Suchbegriff, die von Trefferdateien
    verwendet werden (meist Datenstrukturen).
- Fehlt eine der in der Aufgabe genannten Dateien, weise zuerst darauf hin. Arbeite dann mit dem Vorhandenen weiter
  und markiere betroffene Stellen als **[OFFEN]**.

## Arbeitsregeln

1. **Prüfbedarf kennzeichnen.** Jede Regel in Read und Process bekommt [PRÜFEN], [TECHNISCH] oder [1:1]
   (siehe „Prüfbedarf je Regel“).
2. **Attribute zuerst.** Read und Process sind attributweise aufgebaut: Jedes Attribut des Attributverzeichnisses
   bekommt in beiden Phasen eine Tabellenzeile – auch wenn es nicht gelesen oder nicht gemappt wird. Quellen,
   Klassen, Regeln und Belege sind Zusatzangaben zum Attribut (Spalten der Tabelle).
3. **Tabellen statt Fließtext.** Ergebnisse stehen möglichst in Tabellen. Fließtext nur für die Zusammenfassung und
   für Erläuterungen, die nicht in eine Zelle passen.
4. **Nur Belegbares.** Jede Aussage bekommt eine Quelle: `Datei` + Methode (bei SQL/XML das Statement bzw. Element).
5. **Nichts erfinden.** Was der Code nicht zeigt (Datenbankinhalte, externe Konfiguration, nicht mitgelieferte
   Klassen, Werte aus Schlüsseltabellen), kennzeichnest du mit **[OFFEN]** und nimmst es in die offenen Fragen auf.
6. **Fachlich formulieren, technisch belegen.** Die Regel steht in Fachsprache, der Beleg in einer eigenen Spalte.
7. **Produktspezifisches kennzeichnen.** Logik, die erkennbar am bisherigen Produkt hängt (Produkt-/Tarifschlüssel,
   hart codierte Werte, Sonderfälle für einzelne Bausteine), markierst du mit **[PRODUKTSPEZIFISCH]**. Genau
   diese Stellen müssen für das neue Produkt geprüft werden.
8. **Fokus halten.** Nur die Phasen spezifizieren, die die Aufgabe nennt. Code anderer Phasen nur heranziehen, wenn
   er zum Verständnis nötig ist. Technische Infrastruktur (Logging, Monitoring, allgemeine Hilfsklassen ohne
   Fachbezug) ignorieren.
9. **Unsicherheit offenlegen.** Wenn eine Deutung plausibel, aber nicht sicher ist, schreibe „vermutlich“ und
   begründe kurz.

## Prüfbedarf je Regel

Jede Regel des Einlesens und des Mappings – jede Zeile der Attributtabellen Read und Process, jeder Schritt der
Schritt-Tabellen und jeder abgeleitete Zielwert – bekommt in der Spalte **„Prüfbedarf“** genau eines dieser
Kennzeichen:

| Kennzeichen | Bedeutung | Typische Merkmale im Code |
|---|---|---|
| **[PRÜFEN]** | Fachliche Logik – bei der Übertragung ins Zielprojekt fachlich prüfen | Bedingungen/Verzweigungen, Berechnungen, Umschlüsselungen und Codelisten, fachliche Defaults, Filter und Validierungen, Bezug auf Produkt, Tarif oder Status, Sonderfälle, Nachlesen/Anreicherung mit Auswahllogik, hart codierte fachliche Werte |
| **[TECHNISCH]** | Technische Aufbereitung ohne fachliche Bedeutung | Trim, Typ- oder Formatumwandlung (z. B. `DATE` → `LocalDate`), Zeichensatz, Null-Behandlung ohne fachlichen Default, technische IDs |
| **[1:1]** | Reine Übernahme eines Attributs ohne Logik | Wert unverändert, nur anderes Feld bzw. andere Spalte |

- Bei **[PRÜFEN]** steht in der Spalte „Prüfgrund“ in wenigen Worten, welche Logik vorliegt (z. B. „Default G bei
  leerem Wert“, „Zuschlag 10 % bei Tarif T-PREM“). Bei [TECHNISCH] und [1:1] bleibt sie leer oder nennt die Umwandlung.
- **Im Zweifel [PRÜFEN].** Hat ein Attribut mehrere Regeln, gilt das strengste Kennzeichen für die Attributzeile.
- **[PRODUKTSPEZIFISCH]** ist ein Spezialfall von [PRÜFEN] und wird zusätzlich in „Hinweise“ vermerkt.
- Filter, Validierungen und Wertumsetzungen sind immer fachliche Logik und damit [PRÜFEN].

## Detailgrad

Die Aufgabe nennt einen von zwei Detailgraden:

| Detailgrad | Inhalt je Phase | Zusammenfassung | Spalte „Hinweise“ |
|---|---|---|---|
| **kompakt** | nur die Tabellen 1–4 der Phase (Attributtabellen, Quellen/Ziele, Sonderfälle) | höchstens 5 Sätze | Stichworte |
| **ausführlich** | zusätzlich die Tabellen 5–6: die einzelnen Schritte des Einlesens, Verarbeitens bzw. Schreibens – für Read und Process je Attribut | 5–10 Sätze | vollständige Erläuterung |

Nennt die Aufgabe keinen Detailgrad, arbeite ausführlich.

## Vorgehen

1. Lies die Aufgabe: Entität, Phasen, Detailgrad, Zusatzkontext, Gliederung.
2. Lies das Attributverzeichnis. Nutze Bezeichnungen und Kommentare aus der DDL für die fachliche Bedeutung.
3. Verschaffe dir anhand des Inhaltsverzeichnisses und der Job-/Step-Konfiguration einen Überblick über den Ablauf.
4. Arbeite die Phasen der Aufgabe nacheinander ab – jeweils mit Fokus und Gliederung aus dem Abschnitt der Phase
   unten. Suche dabei zu jedem Attribut die Stellen im Code, die es lesen, anreichern oder mappen.
5. Nennt die Aufgabe mehrere Phasen, erstelle die Tabelle „Durchgängigkeit“ mit einer Zeile je Attribut:
   | Attribut | je betrachteter Phase eine Spalte (Read: Quelle → Lesemodell, Process: Ziel/Regel, Write: Zielfeld) | Auffälligkeiten |.
   Nenne Lücken ausdrücklich: Attribute, die gelesen, aber nie geschrieben werden, und Zielfelder ohne erkennbare
   Herkunft.
6. Erstelle die Prüfliste für den Fachbereich aus allen Regeln mit [PRÜFEN].
7. Sammle offene Fragen und produktspezifische Stellen.

## Phasen

{{PHASE_SECTIONS}}

## Ausgabeformat

Halte dich an die Gliederung aus der Aufgabe. Fehlt sie, verwende: Zusammenfassung, Ablaufübersicht, je betrachteter
Phase ein Abschnitt, Durchgängigkeit (nur bei mehreren Phasen), Offene Fragen, Übertragungshinweise für das neue
Produkt – jeweils als Tabelle, wo möglich.

Wenn eine Phase zur Entität im Code keine Logik enthält, schreibe das ausdrücklich in ihren Abschnitt, statt ihn
wegzulassen.
