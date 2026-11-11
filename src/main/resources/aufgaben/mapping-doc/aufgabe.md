# Aufgabe: Mapping-Dokumentation „{{ENTITY}}“

> Bearbeite diese Aufgabe mit dem Skill **{{SKILL_FILE}}**. Die auszufüllenden Dokumente stehen in
> **{{DOCUMENTS_FILE}}**.

## Auftrag

Dokumentiere das im Migrationsprojekt **implementierte** Mapping der Entität „{{ENTITY}}“: Trage für jedes der
{{ATTRIBUTE_COUNT}} Attribute die Mapping-Regel für den **Bestand** und für **Schwebe** anhand des Codes unten ein –
in beide Dokumente, mit identischem Inhalt:

{{DOCUMENTS}}

Spezifikation (`{{JIRA}}`) und Status (FERTIG, grün) sind bereits eingetragen; alles andere bleibt unverändert.

| Angabe | Wert |
|---|---|
| Migrationsprojekt | {{PROJECT}} |
| Fachliche Entität | {{ENTITY}} (Suchbegriffe: {{TERMS}}) |
| Spezifikations-Ticket | `{{JIRA}}` |

{{REFERENCE}}

{{CONTEXT}}

## Abgabe

Stelle {{RESULTS}} als **Dateien zum Download** bereit – jeweils der vollständige Inhalt des Dokuments mit den
eingetragenen Mapping-Regeln. Im Chat nur die kurze Übersicht laut Skill.

## Voranalyse

{{ANALYSIS}}

## Code

Process-Phase (Mapping/Verarbeitung), Read-Phase und Datenzugriff (für die Quellspalten: RowMapper, SQL) und die
Datenstrukturen der Entität aus dem Migrationsprojekt. Maßgeblich für die Dokumentation.
