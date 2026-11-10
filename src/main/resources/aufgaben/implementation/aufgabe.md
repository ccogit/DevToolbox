# Aufgabe: Implementierung „{{ENTITY}}“ nach Spezifikation – Phasen {{PHASES}}

> Bearbeite diese Aufgabe mit dem Skill **{{SKILL_FILE}}**. Die fachlichen Vorgaben stehen in der Spezifikation
> **{{SPEC_FILE}}**, die strukturellen Vorgaben im Abschnitt „Standards“ unten.

## Auftrag

Implementiere im Zielprojekt die Phasen **{{PHASES}}** der Entität „{{ENTITY}}“ – je Phase die Artefakte, für die
die Standards Vorlagen enthalten (Read: Datenobjekte wie TO, PO, Entity und die Einleselogik, die sie befüllt;
Process: Verarbeitungs-/Mapping-Logik; Write: Schreiblogik). Den Inhalt (Attribute, Quellen, Typen, Regeln) liefert
die Spezifikation, die Struktur liefern die Standards (Beispiel-Entität „{{EXAMPLE}}“). Gehe anhand dieser
Beispiele vor.

| Angabe | Wert |
|---|---|
| Zielprojekt | {{PROJECT}} |
| Fachliche Entität | {{ENTITY}} |
| Spezifikation | `{{SPEC_FILE}}` (vom Fachbereich abgestimmt) |
| Beispiel-Entität (Standards) | {{EXAMPLE}} |
| Zu implementierende Phasen | **{{PHASES}}** |

{{CONTEXT}}

## Zu erstellende Artefakte

Je Vorlage eine neue Klasse (Namen und Packages nach den Konventionen der Standards):

{{ARTIFACTS}}

Verlangt die Spezifikation zusätzliche Artefakte (z. B. eine weitere Anreicherung), lege sie nach der am besten
passenden Vorlage an und führe sie unter „Abweichungen von der Vorlage“ auf.

## Erwartetes Ergebnis

1. **Code** – je Artefakt eine Überschrift mit dem vollständigen Pfad, darunter der vollständige Quelltext.
2. **Attributabdeckung** – Tabelle | Attribut (Spezifikation) | je implementierter Phase eine Spalte (Read: Feld je Datenobjekt + befüllt in; Process: Ziel + Klasse.Methode; Write: Zielfeld + Klasse.Methode) |.
3. **Abweichungen von der Vorlage** – Tabelle | Stelle | Abweichung | Grund (Spezifikation) |; „keine“, wenn es keine gibt.
4. **Offene Punkte** – Tabelle | Nr | Punkt | Betroffene Attribute | Im Code markiert in (Klasse) |.
