### Phase Read – Einlesen und Anreicherung

**Soll:** die Liste „Soll Read“ der Aufgabe (Nr `R…`).

**Untersuche:** ItemReader und ihre Konfiguration (SQL, RowMapper, Cursor/Paging), Repositories, soweit sie
lesen (meist, aber nicht immer – maßgeblich ist, was der Code tut), Anreicherung und Anreicherer (Klassen und
Verzeichnisse mit „Anreicherung“ oder „Anreicherer“ im Namen) und Nachlesezugriffe auf die Altdatenbank, Selektion (WHERE, Stichtage,
Status-, Produkt- und Tariffilter), Sortierung und Gruppierung, Umgang mit fehlenden oder fehlerhaften Quelldaten.

**Attributtabelle Read** – eine Zeile je Nr der Soll-Liste:

| Nr | Attribut | Bedeutung | Typ | Einlesen | Ziel im Lesemodell (Klasse.Feld) | Prüfbedarf | OPL | Beleg |
|---|---|---|---|---|---|---|---|---|

- **Attribut:** der Schlüssel aus der Soll-Liste, wörtlich (z. B. `ALT_RISIKO.RISART`).
- **Einlesen:** wie der Wert gelesen und angereichert wird, einschließlich Defaults, Sonderfällen und
  Fehlerverhalten dieses Attributs.
- Liest der Code das Attribut nicht: `nicht gelesen`, „Beleg“ bleibt leer, und ein OPL-Punkt fragt, ob das so
  übernommen werden soll.
- **Hilfswerte:** Ein Wert außerhalb der Soll-Liste (z. B. aus einer Nebentabelle), der nur gelesen wird, um ein
  Attribut zu ermitteln, bekommt eine eigene Zeile direkt unter diesem Attribut: Nr `H1`, `H2`, …, hinter dem
  Schlüssel der Zusatz `(Hilfswert)`. Hilfswerte zählen nicht zur Sollzahl.

**Satzregeln Read** – nur Regeln, die nicht an einem einzelnen Attribut hängen, Nr `SR1`, `SR2`, …:

| Nr | Regel | Art (Selektion, Sortierung/Gruppierung, Fehlerbehandlung) | Prüfbedarf | OPL | Beleg |
|---|---|---|---|---|---|
