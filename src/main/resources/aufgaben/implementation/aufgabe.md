# Aufgabe: Implementierung „{{ENTITY}}“ nach Spezifikation – Phasen {{PHASES}}

> Bearbeite diese Aufgabe mit dem Skill **{{SKILL_FILE}}**. Die fachlichen Vorgaben stehen in der Spezifikation
> **{{SPEC_FILE}}**, die strukturellen Vorgaben im Abschnitt „Standards“ unten.

## Auftrag

Implementiere im Zielprojekt die Phasen **{{PHASES}}** der Entität „{{ENTITY}}“ anhand der Vorlagen der
Beispiel-Entität „{{EXAMPLE}}“: Struktur aus den Vorlagen, Inhalt (Attribute, Quellen, Typen, Regeln) aus der
Spezifikation.

| Angabe | Wert |
|---|---|
| Zielprojekt | {{PROJECT}} |
| Fachliche Entität | {{ENTITY}} |
| Spezifikation | `{{SPEC_FILE}}` (vom Fachbereich abgestimmt) |
| Zu implementierende Phasen | **{{PHASES}}** |

{{CONTEXT}}

## Zu erstellende Artefakte

{{ARTIFACTS}}

Verlangt die Spezifikation ein zusätzliches Artefakt, lege es nach der am besten passenden Vorlage an.

## Erwartetes Ergebnis

1. **Code** – je Artefakt eine Überschrift mit dem vollständigen Pfad, darunter der vollständige Quelltext. Offene
   Stellen sind im Code mit `// TODO OPL-n: …` markiert.
2. **Umsetzung je Attribut** – eine Zeile je Attribut der Spezifikation:
   | Attribut | je implementierter Phase eine Spalte (Read: Feld je Datenobjekt + befüllt in Klasse.Methode; Process: Zielfeld + Klasse.Methode; Write: Zielfeld + Klasse.Methode) | OPL |
3. **Artefakte** – eine Zeile je erstellter Datei:
   | Nr | Datei | Vorlage | Abweichung von der Vorlage (mit Grund aus der Spezifikation; „keine“, wenn keine) | OPL |
4. **Offene-Punkte-Liste (OPL)** – alle offenen Punkte, nummeriert OPL-1, OPL-2, …:
   | OPL-Nr | Punkt | Betrifft (Attribut bzw. Datei) | Bezug zur OPL der Spezifikation | Umgesetzte Annahme |

Keine weiteren Abschnitte oder Tabellen: Jede Information steht genau einmal.
