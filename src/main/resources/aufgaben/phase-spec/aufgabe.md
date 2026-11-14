# Aufgabe: Spezifikation „{{ENTITY}}“ aus dem Referenzprojekt

> Bearbeite diese Aufgabe nach dem Skill **{{SKILL_FILE}}**. Er enthält die Regeln und die Tabellenformate.

## Auftrag

Beschreibe für das Zielprojekt, wie die Entität „{{ENTITY}}“ im Referenzprojekt verarbeitet wird – nur in den unten
genannten Phasen und Zeile für Zeile nach den Soll-Listen dieser Aufgabe.

| Angabe | Wert |
|---|---|
| Zielprojekt (wird spezifiziert) | {{PROJECT}} |
| Referenzprojekt (Code wird ausgewertet) | {{REFERENCE}} |
| Fachliche Entität | {{ENTITY}} |
| Suchbegriffe | {{TERMS}} |
| Zu betrachtende Phasen | **{{PHASES}}** |
| Nicht betrachten | {{EXCLUDED}} |
| Detailgrad | {{DETAIL}} |
| Code-Kontext | {{FILES}} |
| Ergebnisdatei | `{{RESULT_FILE}}` |

{{CONTEXT}}

## Soll-Listen

{{INVENTORY}}

## Ergebnis

Inhalt der Datei `{{RESULT_FILE}}`, in genau dieser Gliederung, Ergebnisse als Tabellen:

{{OUTPUT}}

## Abgabe

Stelle `{{RESULT_FILE}}` als **Markdown-Datei zum Download** bereit. Im Chat stehen nur der Status und die
Kennzahlen, nicht der Inhalt der Datei.

## Vor der Abgabe prüfen

1. Jede Nr der Soll-Listen steht genau einmal in der Tabelle ihrer Phase, und die Zahl der Zeilen entspricht der
   Sollzahl. Hilfswerte (`H…`) zählen nicht mit.
2. Keine Zeile ist abgekürzt oder mit anderen zusammengefasst.
3. Jede Regel hat einen Beleg, der im Code-Kontext steht. Wo es keinen gibt, steht `nicht belegt` und eine OPL-Nr.
4. Jede OPL-Nr, die in einer Tabelle steht, gibt es in der OPL.
5. Hart codierte Werte und Codelisten sind ausgeschrieben. Je Schlüsseltabelle, Lookup oder Service, dessen Werte
   der Code-Kontext nicht zeigt, gibt es einen eigenen OPL-Punkt.
{{DEPTH_CHECKS}}

Trifft einer dieser Punkte nicht zu oder fehlen Zeilen, lautet der Status `UNVOLLSTÄNDIG` mit der letzten
vollständigen Nr. Fülle nichts auf und erfinde nichts.
