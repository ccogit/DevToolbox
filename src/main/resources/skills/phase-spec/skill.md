# Skill: Migrationsspezifikation je Phase

Dieser Skill beschreibt, **wie** du eine Aufgabe vom Typ „Migrationsspezifikation je Phase“ bearbeitest.
**Was** zu tun ist – Fachbegriff, zu betrachtende Phasen, Zusatzkontext und die Gliederung des Ergebnisses –,
steht in der Aufgabe-Datei („Aufgabe: …“). Lies sie zuerst.

## Ausgangslage

Wir migrieren Versicherungsverträge in ein neues System. Für ein anderes Produkt derselben Sparte ist eine sehr
ähnliche Migration bereits umgesetzt. Deren Logik zu einzelnen fachlichen Themen (z. B. Risiken) soll in die neue
Migrationsanwendung übernommen werden.

Deine Aufgabe ist jeweils eine **fachliche Spezifikation**, die aus dem Code der bestehenden Migration beschreibt,
wie das Thema der Aufgabe verarbeitet wird – getrennt nach den Phasen der Batch-Verarbeitung (Spring Batch:
Read = ItemReader, Process = ItemProcessor, Write = ItemWriter), jede Phase in einem eigenen Abschnitt.

## Eingaben

- **Aufgabe** („Aufgabe: …“): Fachbegriff, zu betrachtende Phasen, Zusatzkontext, erwartete Gliederung.
- **Code-Kontext** („Code-Kontext: …“): Jede Quelldatei steht zwischen `===== BEGIN FILE: <Pfad> =====` und
  `===== END FILE: <Pfad> =====`. Das Inhaltsverzeichnis am Anfang listet alle Dateien mit Phase und Grund der
  Aufnahme. Die Phasenzuordnung ist nur ein Hinweis – maßgeblich ist, was der Code tut.
- „Referenzen“ im Code-Kontext sind Klassen ohne den Suchbegriff, die von Trefferdateien verwendet werden
  (meist Datenstrukturen wie TOs, POs, DTOs, Entities).
- Fehlt eine der in der Aufgabe genannten Dateien, weise zuerst darauf hin. Arbeite dann mit dem Vorhandenen weiter
  und markiere betroffene Stellen als **[OFFEN]**.

## Arbeitsregeln

1. **Nur Belegbares.** Jede Aussage bekommt eine Quelle: `Datei` + Methode (bei SQL/XML das Statement bzw. Element).
2. **Nichts erfinden.** Was der Code nicht zeigt (Datenbankinhalte, externe Konfiguration, nicht mitgelieferte
   Klassen, Werte aus Schlüsseltabellen), kennzeichnest du mit **[OFFEN]** und nimmst es in die offenen Fragen auf.
3. **Fachlich formulieren, technisch belegen.** Die Regel steht in Fachsprache, der Beleg dahinter.
4. **Produktspezifisches kennzeichnen.** Logik, die erkennbar am bisherigen Produkt hängt (Produkt-/Tarifschlüssel,
   hart codierte Werte, Sonderfälle für einzelne Bausteine), markierst du mit **[PRODUKTSPEZIFISCH]**. Genau
   diese Stellen müssen für das neue Produkt geprüft werden.
5. **Fokus halten.** Nur die Phasen spezifizieren, die die Aufgabe nennt. Code anderer Phasen nur heranziehen, wenn
   er zum Verständnis nötig ist, und dann kurz verweisen. Technische Infrastruktur (Logging, Monitoring, allgemeine
   Hilfsklassen ohne Fachbezug) ignorieren.
6. **Vollständig statt knapp.** Lieber eine Tabellenzeile mehr. Keinen Code abschreiben – außer kurzen Ausdrücken,
   die eine Regel exakt definieren (z. B. eine Bedingung).
7. **Unsicherheit offenlegen.** Wenn eine Deutung plausibel, aber nicht sicher ist, schreibe „vermutlich“ und
   begründe kurz.

## Vorgehen

1. Lies die Aufgabe: Fachbegriff, Phasen, Zusatzkontext, Gliederung.
2. Verschaffe dir anhand des Inhaltsverzeichnisses und der Job-/Step-Konfiguration einen Überblick über den Ablauf.
3. Arbeite die Phasen der Aufgabe nacheinander ab – jeweils mit Fokus und Gliederung aus dem Abschnitt der Phase
   unten. Phasen, die die Aufgabe nicht nennt, überspringst du.
4. Nennt die Aufgabe mehrere Phasen, verfolge die zentralen Felder des Themas phasenübergreifend: Woher kommt ein
   Wert, wie wird er umgesetzt, wohin wird er geschrieben? Ergebnis als Tabelle im Abschnitt „Durchgängigkeit“ mit
   den Spalten: Fachliches Feld, je betrachteter Phase eine Spalte (Klasse.Feld bzw. Regel), Auffälligkeiten.
   Nenne Lücken ausdrücklich: Felder, die gelesen, aber nie geschrieben werden, und Zielfelder ohne erkennbare Herkunft.
5. Sammle offene Fragen und produktspezifische Stellen.

## Phasen

{{PHASE_SECTIONS}}

## Ausgabeformat

Halte dich an die Gliederung aus der Aufgabe. Fehlt sie, verwende: Zusammenfassung, Ablaufübersicht, je betrachteter
Phase ein Abschnitt, Durchgängigkeit (nur bei mehreren Phasen), Offene Fragen, Übertragungshinweise für das neue
Produkt.

Wenn eine Phase zum Thema der Aufgabe im Code keine Logik enthält, schreibe das ausdrücklich in ihren Abschnitt,
statt ihn wegzulassen.
