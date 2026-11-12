### Phase Process – Mapping/Verarbeitung

**Soll:** die Liste „Soll Process“ der Aufgabe (Nr `P…`).

**Untersuche:** ItemProcessor, Mapper und Converter (auch Verzeichnisse `mapper`/`mapping`) mit den von ihnen
aufgerufenen Regel- und Hilfsklassen, Wertumsetzungen (Enums, Schlüsseltabellen, Maps, switch/if-Kaskaden),
Berechnungen und Ableitungen, Validierungen und Filter (z. B. Processor gibt `null` zurück, Exceptions),
Verzweigungen nach Produkt, Tarif oder Status, Defaultwerte.

**Attributtabelle Process** – eine Zeile je Nr der Soll-Liste:

| Nr | Zielattribut | Herkunft (Quellattribut(e) bzw. „abgeleitet“) | Mapping | Prüfbedarf | OPL | Beleg |
|---|---|---|---|---|---|---|

- **Zielattribut:** der Schlüssel aus der Soll-Liste, wörtlich.
- **Mapping:** wie der Wert entsteht. Wertumsetzungen stehen vollständig in dieser Zelle (z. B. „FE → FEUER,
  LW → LEITUNGSWASSER, ST → STURM_HAGEL; sonst Fehler“), ebenso Defaults, Validierungen und Verzweigungen, die nur
  dieses Zielattribut betreffen.
- Befüllt der Code das Zielattribut nicht: `nicht befüllt` und ein OPL-Punkt.
- Quellattribute, die in keinem Zielattribut ankommen, werden ein OPL-Punkt (keine eigene Tabelle).
- Nennt die Soll-Liste Quellattribute statt Zielattributen (kein Zielmodell zur Entität), heißen die Spalten
  | Nr | Attribut | Ziel (Klasse.Feld) | Mapping | Prüfbedarf | OPL | Beleg |.

**Satzregeln Process** – nur Regeln, die den ganzen Satz betreffen, Nr `SP1`, `SP2`, …:

| Nr | Regel | Art (Filter, Validierung, Verzweigung) | Prüfbedarf | OPL | Beleg |
|---|---|---|---|---|---|
