# VGB – angefangene Zielmigration Wohngebaeude

Dieses Modul liegt analog zu `testprojekt-ziel/` in der Repository-Wurzel. VGB ist **absichtlich nicht fertig**: Es bietet ein Java-21-/Spring-Batch-Grundgeruest, einen ersten Vertrags-Repository-Zugriff (nur Bestand), eine vereinfachte Risiko-Mappingregel und einen nicht implementierten Schreibplan.

Die 13 Zielmodell-Dateien in `zielmodell/`, die Confluence-Mapping-Vorlagen in `mapping-vorlagen/` sowie die DDL in `ddl/` dienen als **Soll- und Analyseinput** fuer die DevToolbox. Die Zielmodelle enthalten VGB-eigene neue Attribute (`Risiko.gefahrenzone`, `Objektdaten.energieeffizienzklasse`, `Deckungen.elementardeckung`) ohne vorgetaeuschte fertige Implementierung.

## Geplanter Ausbau

1. **Read:** Bestand- und Schwebe-Anreicherer je Entitaet, Register/Delegator, Quellselektion und Kardinalitaetsregeln.
2. **Process:** alle 13 Mapper plus Sub-Mapper und den zentralen Delegator; fachliche Unterschiede zur VHV-Hausratmigration dokumentieren.
3. **Write:** 13 idempotente Entitaetsschreiber mit Chunk-Transaktionen, Integrations- und Wiederanlauftests.
4. **Spezifikation:** in der DevToolbox das Projekt `VGB` waehlen; `referenz=vhv`, `root=../../vgb`, Zielmodell und DDL sind bereits eingetragen.

`mvn test` prueft lediglich die baubare Ausgangsbasis. Ein VGB-Migrationsjob ist **noch nicht vorhanden** – genau das ist der beabsichtigte Testzustand der Spezifikations- und Implementierungsfunktionen. Beispiel-Vertraege und -Risiken liegen in einer H2-Testdatenbank. Alle Daten sind synthetisch.

