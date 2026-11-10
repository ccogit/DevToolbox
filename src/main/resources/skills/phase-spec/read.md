### Phase Read – Einlesen und Anreicherung

**Ziel:** Für jedes Attribut der Entität beschreiben, woher es stammt und wie es gelesen bzw. angereichert wird.

**Untersuche insbesondere:**
- ItemReader-Implementierungen und ihre Konfiguration: SQL/Queries, RowMapper, Cursor/Paging, Dateiformate
- Anreicherer (Klassen in den Verzeichnissen `anreicherer`/`anreicherung`) und Nachlesezugriffe auf die Altdatenbank
- Selektionskriterien: WHERE-Bedingungen, Stichtage, Status-, Produkt- und Tariffilter
- Sortierung und Gruppierung (z. B. mehrere Quellzeilen werden zu einem Vertrag zusammengefasst)
- Umgang mit fehlenden oder fehlerhaften Quelldaten

**Gliederung für diese Phase:**
1. **Attributtabelle Read** – eine Zeile je Attribut aus dem Attributverzeichnis, in dessen Reihenfolge, auch für
   Attribute, die nicht gelesen werden:
   | Attribut | Fachliche Bedeutung | Quelle (Tabelle.Spalte / Datei / Schnittstelle) | Typ/Format | Selektion/Bedingung | Gelesen/angereichert in (Klasse.Methode) | Ziel im Lesemodell (Klasse.Feld) | Beleg | Hinweise |
   Nicht gelesene Attribute: „nicht gelesen“ in der Spalte „Gelesen/angereichert in“. In „Hinweise“ stehen Defaults
   bei fehlenden Werten, Sonderfälle, **[OFFEN]** und **[PRODUKTSPEZIFISCH]**.
2. **Quellen und Selektion** – | Quelle | Zugriff (Klasse.Methode) | Selektionskriterien | Sortierung/Gruppierung | Beleg |
3. **Weitere gelesene Werte** – Werte, die gelesen werden, aber nicht im Attributverzeichnis stehen (z. B. aus
   nachgelesenen Tabellen), in derselben Tabellenform wie Punkt 1.
4. **Fehlerbehandlung** – | Situation | Verhalten (Skip, Abbruch, Default) | Beleg |
