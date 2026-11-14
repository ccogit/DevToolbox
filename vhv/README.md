# VHV – abgeschlossene Referenzmigration Hausrat

Dieses Verzeichnis liegt wie `testprojekt/` direkt in der Repository-Wurzel. Es enthaelt eine **synthetische**, in sich geschlossene Spring-Batch-Referenzmigration (Java 21, Spring Boot 3.4, Spring Batch 5, H2). Die Daten sind frei erfunden und nicht mit produktiven Bestandsdaten verbunden.

## Testumfang

- **13 fachliche Entitaeten:** Vertrag, Vorgang, Risiko, NachlassZuschlagRisikobezogen, NachlassZuschlagVertragsbezogen, Beitrag, Objektdaten, Wertermittlung, Vorversicherer, Nebenversicherer, Standort, Wertsachen, Deckungen.
- **Read:** `AnreichernderReader` liest Vertragsschluessel, `AnreicherungsDelegator` waehlt pro Entitaet genau einen von 26 Bestand-/Schwebe-Anreicherern. Gemeinsame JDBC-Template-Methoden pruefen Kardinalitaet und referenzieren explizite Altspalten.
- **Process:** `MigrationsProcessor` -> `MappingDelegator` -> 13 `*Mapper` -> 13 `*SubMapper` -> `GemeinsameSubMapper`. Normalisierung von Codes, Rundung, Prozentgrenzen, fehlenden Feldern und datenstandabhaengiger Bearbeitung.
- **Write:** `SchreibDelegator` -> 13 `*Schreiber` in fachlicher Reihenfolge, anschliessend `MIGRATION_AUDIT`; DELETE/INSERT je Vertrags-ID/Datenstand innerhalb einer Chunk-Transaktion fuer idempotente Wiederholung.
- **Testdaten:** Bestand und Schwebe, geaenderte Werte, nur einseitig vorhandene Vertraege und optionale fehlende Nebendaten. DDL unter `ddl/`, H2-Schema und Fixtures unter `src/main/resources/`.
- **DevToolbox:** 13 Zielmodell-JSONs und Confluence-Mapping-Vorlagen fuer Phase Process und Mapping-Dokumentation.

## Ausfuehren

```bash
cd vhv
mvn test
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.batch.job.enabled=true datenstand=ALLE"
```

Der Parameter `datenstand` ist `BESTAND`, `SCHWEBE` oder `ALLE` (Default). H2 ist absichtlich eine In-Memory-Datenbank; bei jedem Start werden Quelldaten neu initialisiert. Ein erfolgreicher Job schreibt je Datensatz in 13 Zieltabelle(n), sofern die jeweilige optionale Entitaet existiert, und in `MIGRATION_AUDIT`. Der Integrationstest prueft beide Datenstaende, die fehlenden optionalen Entitaeten und die idempotente Wiederholung.

## Fachliche Abgrenzung

Die Regeln und Quellen sind **realitaetsnahe Testannahmen**, keine verbindliche Versicherungsfachlichkeit: kein echter DB2-Connector, keine Tarifierung, keine revisionssichere Archivierung und keine produktiven Schnittstellen. Das Projekt soll die Kontextgewinnung, Delegation, Abhaengigkeiten, Spezifikationsvollstaendigkeit und Codegenerierung der DevToolbox belasten. In `../migrationsprojekte/vhv-test/projekt.json` (Projekt „VHV-Test“) ist es als `root` hinterlegt. Das Testprojekt „VGB-Test“ nutzt die Projekt-ID `vhv-test` als Referenz. Die echten Projekte `vhv`/`vgb` bleiben davon unberührt.

