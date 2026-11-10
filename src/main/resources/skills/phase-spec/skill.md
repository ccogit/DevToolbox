# Skill: Migrationsspezifikation „{{TERMS}}“ – Phasen {{PHASES}}

## Ausgangslage

Wir migrieren Versicherungsverträge in ein neues System. Für ein anderes Produkt derselben Sparte ist eine sehr
ähnliche Migration bereits umgesetzt. Deren Logik zum fachlichen Thema „{{TERMS}}“ soll in die neue
Migrationsanwendung übernommen werden.

Deine Aufgabe: Erstelle aus dem Code der bestehenden Migration eine **fachliche Spezifikation** dazu, wie das Thema
„{{TERMS}}“ verarbeitet wird. Betrachte dabei **ausschließlich die Phasen {{PHASES}}** der Batch-Verarbeitung
(Spring Batch: Read = ItemReader, Process = ItemProcessor, Write = ItemWriter), jede in einem eigenen Abschnitt.

{{CONTEXT}}

## Eingaben

- Eine oder mehrere Dateien „Code-Kontext: …“ (bei mehreren: „Teil x von n“). Jede Quelldatei steht zwischen
  `===== BEGIN FILE: <Pfad> =====` und `===== END FILE: <Pfad> =====`.
- Das Inhaltsverzeichnis am Anfang listet alle Dateien mit Phase und Grund der Aufnahme. Die Phasenzuordnung ist
  nur ein Hinweis – maßgeblich ist, was der Code tut.
- „Referenzen“ sind Klassen ohne den Suchbegriff, die von Trefferdateien verwendet werden (meist DTOs/Entities).
- Kündigt das Inhaltsverzeichnis mehr Teile an, als hochgeladen wurden: Weise zuerst darauf hin und nenne die
  fehlenden Teile. Arbeite dann mit dem Vorhandenen weiter und markiere betroffene Stellen als **[OFFEN]**.

## Arbeitsregeln

1. **Nur Belegbares.** Jede Aussage bekommt eine Quelle: `Datei` + Methode (bei SQL/XML das Statement bzw. Element).
2. **Nichts erfinden.** Was der Code nicht zeigt (Datenbankinhalte, externe Konfiguration, nicht mitgelieferte
   Klassen, Werte aus Schlüsseltabellen), kennzeichnest du mit **[OFFEN]** und nimmst es in die offenen Fragen auf.
3. **Fachlich formulieren, technisch belegen.** Die Regel steht in Fachsprache, der Beleg dahinter.
4. **Produktspezifisches kennzeichnen.** Logik, die erkennbar am bisherigen Produkt hängt (Produkt-/Tarifschlüssel,
   hart codierte Werte, Sonderfälle für einzelne Bausteine), markierst du mit **[PRODUKTSPEZIFISCH]**. Genau
   diese Stellen müssen für das neue Produkt geprüft werden.
5. **Fokus halten.** Nur die Phasen {{PHASES}} spezifizieren. Code anderer Phasen nur heranziehen, wenn er zum
   Verständnis nötig ist, und dann kurz verweisen. Technische Infrastruktur (Logging, Monitoring, allgemeine
   Hilfsklassen ohne Fachbezug) ignorieren.
6. **Vollständig statt knapp.** Lieber eine Tabellenzeile mehr. Keinen Code abschreiben – außer kurzen Ausdrücken,
   die eine Regel exakt definieren (z. B. eine Bedingung).
7. **Unsicherheit offenlegen.** Wenn eine Deutung plausibel, aber nicht sicher ist, schreibe „vermutlich“ und
   begründe kurz.

## Vorgehen

1. Verschaffe dir anhand des Inhaltsverzeichnisses und der Job-/Step-Konfiguration einen Überblick über den Ablauf.
2. Arbeite die Phasen nacheinander ab – jeweils mit dem Fokus und der Gliederung aus dem Abschnitt der Phase.
{{CROSS_PHASE}}
3. Sammle offene Fragen und produktspezifische Stellen.

## Phasen

{{PHASE_SECTIONS}}

## Ausgabeformat

Antworte mit einem Markdown-Dokument in genau dieser Gliederung:

{{OUTPUT}}

Wenn eine Phase zum Thema „{{TERMS}}“ im Code keine Logik enthält, schreibe das ausdrücklich in ihren Abschnitt,
statt ihn wegzulassen.
