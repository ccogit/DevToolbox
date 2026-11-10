### Phase Read – Einlesen

**Ziel:** Beschreiben, woher die Daten zum Thema der Aufgabe stammen und wie sie selektiert werden.

**Untersuche insbesondere:**
- ItemReader-Implementierungen und ihre Konfiguration: SQL/Queries, RowMapper, Cursor/Paging, Dateiformate
- Selektionskriterien: WHERE-Bedingungen, Stichtage, Status-, Produkt- und Tariffilter
- Joins und Nachlesezugriffe (z. B. DAO-/Repository-Aufrufe aus dem Reader oder einem Listener)
- Sortierung und Gruppierung (z. B. mehrere Quellzeilen werden zu einem Vertrag zusammengefasst)
- Umgang mit fehlenden oder fehlerhaften Quelldaten

**Gliederung für diese Phase:**
1. **Quellen** – Tabelle: | Quelle (Tabelle/Datei/Schnittstelle) | Zugriff (Klasse.Methode) | Selektionskriterien | Beleg |
2. **Gelesene Felder** – Tabelle: | Quellfeld | Typ/Format | Fachliche Bedeutung | Ziel im Lesemodell (Klasse.Feld) | Beleg |
3. **Regeln und Sonderfälle beim Einlesen** – nummeriert, je mit Beleg
4. **Fehlerbehandlung** – was passiert bei fehlenden/ungültigen Daten (Skip, Abbruch, Defaultwert)?
