### Phase Read – Einlesen und Anreicherung

**Ziel:** Für jedes Attribut der Entität beschreiben, woher es stammt und wie es gelesen bzw. angereichert wird.

**Untersuche insbesondere:**
- ItemReader-Implementierungen und ihre Konfiguration: SQL/Queries, RowMapper, Cursor/Paging, Dateiformate
- Repositories (je Entität eines, sie lesen die Daten), Anreicherer (Klassen in den Verzeichnissen
  `anreicherer`/`anreicherung`) und Nachlesezugriffe auf die Altdatenbank
- Selektionskriterien: WHERE-Bedingungen, Stichtage, Status-, Produkt- und Tariffilter
- Sortierung und Gruppierung (z. B. mehrere Quellzeilen werden zu einem Vertrag zusammengefasst)
- Umgang mit fehlenden oder fehlerhaften Quelldaten

**Ergebnis für diese Phase:**

1. **Attributtabelle Read** – eine Zeile je Attribut des Attributverzeichnisses, in dessen Reihenfolge:

   | Attribut | Bedeutung | Quelle (Tabelle.Spalte) | Typ | Einlesen | Ziel im Lesemodell (Klasse.Feld) | Prüfbedarf | OPL | Beleg |
   |---|---|---|---|---|---|---|---|---|

   - **Einlesen:** bei „kompakt“ die Regel in einem Satz (z. B. „gelesen, getrimmt“; „fehlt: aus Wohnfläche
     berechnet“); bei „ausführlich“ die nummerierten Schritte in der Zelle, je Schritt mit Bedingung und
     Klasse.Methode (z. B. „1. lesen ALT_RISIKO.VS · 2. falls leer: nachlesen ALT_OBJEKT.WOHNFLAECHE
     (AltBestandDao.ermittle…) · 3. berechnen …“). Defaults, Sonderfälle und Fehlerverhalten des Attributs gehören
     hierher.
   - Nicht gelesene Attribute: „nicht gelesen“ und ein OPL-Punkt, ob das so übernommen werden soll.
   - **Hilfswerte**, die nur zum Ermitteln eines Attributs gelesen werden (z. B. aus nachgelesenen Tabellen), sind
     Zeilen derselben Tabelle mit dem Zusatz „(Hilfswert)“ hinter dem Namen – direkt unter dem Attribut, für das sie
     gebraucht werden.

2. **Satzregeln Read** – nur Regeln, die nicht an einem einzelnen Attribut hängen:

   | Nr | Regel | Art (Selektion, Sortierung/Gruppierung, Fehlerbehandlung) | Prüfbedarf | OPL | Beleg |
   |---|---|---|---|---|---|
