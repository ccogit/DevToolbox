# Aufgabe: Implementierung „{{ENTITY}}“ nach Spezifikation – Phasen {{PHASES}}

> Bearbeite diese Aufgabe mit dem Skill **{{SKILL_FILE}}**. Die fachlichen Vorgaben stehen in der Spezifikation
> **{{SPEC_FILE}}**, die strukturellen Vorgaben im Abschnitt „Standards“ unten.

## Auftrag

Implementiere im Zielprojekt die Phasen **{{PHASES}}** der Entität „{{ENTITY}}“ anhand der Standards des
Projekts (abgeleitet von der Beispiel-Entität „{{EXAMPLE}}“): Struktur aus den Vorlagen, Inhalt (Attribute,
Quellen, Typen, Regeln) aus der Spezifikation.

| Angabe | Wert |
|---|---|
| Zielprojekt | {{PROJECT}} |
| Fachliche Entität | {{ENTITY}} |
| Spezifikation | `{{SPEC_FILE}}` (vom Fachbereich abgestimmt) |
| Zu implementierende Phasen | **{{PHASES}}** |

{{CONTEXT}}

## Zu erstellende Artefakte

{{ARTIFACTS}}

Die Spalte „Datei“ ist der Pfad relativ zum Root des Zielprojekts. Verlangt die Spezifikation ein zusätzliches
Artefakt, lege es nach der am besten passenden Vorlage und deren Pfadmuster an.

{{TARGET_ATTRIBUTES}}

## Abgabe

Stelle das Ergebnis als **ZIP-Datei `{{RESULT_FILE}}` zum Download** bereit. Jede erstellte Datei liegt im ZIP
**genau an ihrem Pfad aus der Spalte „Datei“** (relativ zum Root des Zielprojekts), sodass das ZIP direkt über den
Root entpackt werden kann; dazu `IMPLEMENTIERUNG.md` im obersten Ordner des ZIP:

```
{{RESULT_FILE}}
├── <Pfad laut Spalte „Datei“>   je erstelltem Artefakt, z. B. {{EXAMPLE_PATH}}
└── IMPLEMENTIERUNG.md           Umsetzung je Attribut, Artefakte, OPL (siehe unten)
```

Im Chat nur eine kurze Übersicht (Anzahl Dateien, Anzahl OPL-Punkte) – der Inhalt steht ausschließlich im ZIP.

## Erwartetes Ergebnis

1. **Code** – je Artefakt eine Datei im ZIP an ihrem Pfad, vollständiger Quelltext. Offene Stellen sind im Code mit
   `// TODO OPL-n: …` markiert.

Inhalt von `IMPLEMENTIERUNG.md`:

2. **Umsetzung je Attribut** – {{COVERAGE}}
3. **Artefakte** – eine Zeile je erstellter Datei:
   | Nr | Datei (Pfad) | Vorlage | Abweichung von der Vorlage (mit Grund aus der Spezifikation; „keine“, wenn keine) | OPL |
4. **Offene-Punkte-Liste (OPL)** – alle offenen Punkte, nummeriert OPL-1, OPL-2, …:
   | OPL-Nr | Punkt | Betrifft (Attribut bzw. Datei) | Bezug zur OPL der Spezifikation | Umgesetzte Annahme |

Keine weiteren Abschnitte oder Tabellen: Jede Information steht genau einmal.
