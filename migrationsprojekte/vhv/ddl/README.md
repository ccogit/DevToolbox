# Schema-Beschreibung VHV

Hier liegt die DDL der Altdatenbank von VHV: die Tabellen, aus denen die fachlichen Entitäten in der
**Read-Phase** gelesen werden. Die Jobs bilden daraus das Attributverzeichnis (Primärquelle der Attribute).

## Ablegen

In der DevToolbox das Migrationsprojekt wählen und **„Schema importieren…“** klicken. Das Tool prüft die Dateien,
meldet die erkannten Tabellen und Spalten und kopiert sie hierher. Mehrere Dateien sind möglich; Dateien direkt in
diesen Ordner zu kopieren funktioniert ebenfalls.

## Inhalt der Datei

| Anforderung | Erläuterung |
|---|---|
| Format | Textdatei `*.sql` oder `*.ddl`, Statements mit `;` getrennt, Dialekt DB2 for z/OS |
| Umfang | `CREATE TABLE` **aller Alt-Tabellen, aus denen entitätsbezogene Daten gelesen werden** – auch Tabellen, die nur zum Nachlesen/Anreichern dienen. Fremde Tabellen stören nicht: Ein Job nimmt nur Tabellen auf, deren Name einen Suchbegriff enthält oder die im Code des Referenzprojekts vorkommen |
| Schlüssel (optional) | Wenn vorhanden: Primär- und Fremdschlüssel (im `CREATE TABLE` oder per `ALTER TABLE … ADD`) und eindeutige Indizes (`CREATE UNIQUE INDEX`). Ohne sie funktioniert alles; die KI kennt dann nur nicht, welche Spalten einen Satz eindeutig machen und wie die Tabellen zusammenhängen. In Alt-Datenbanken fehlen deklarierte Fremdschlüssel oft – das ist in Ordnung |
| Fachliche Bedeutung | `LABEL ON` und `COMMENT ON` für Tabellen und Spalten – ohne sie kennt die KI nur die Spaltennamen |
| Erzeugen | z. B. `db2look -d <LOCATION> -z <SCHEMA> -t <TABELLEN> -e` oder „DDL generieren“ in IBM Data Studio / DBeaver; alternativ als Export vom DB2-Betrieb |
| Nicht nötig | Tablespaces, Grants, nicht eindeutige Indizes (werden ignoriert, stören aber nicht) |
