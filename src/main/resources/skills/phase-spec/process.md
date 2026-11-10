### Phase Process – Mapping/Verarbeitung

**Ziel:** Für jedes Zielattribut der Entität beschreiben, woher sein Wert kommt und wie er im Mapping entsteht.

**Untersuche insbesondere:**
- ItemProcessor, Mapper/Converter (auch Klassen in den Verzeichnissen `mapper`/`mapping`) sowie die von ihnen
  aufgerufenen Regel- und Hilfsklassen
- Wertumsetzungen über Enums, Schlüsseltabellen, Maps oder switch/if-Kaskaden
- Berechnungen und Ableitungen (Beträge, Datumswerte, Kennzeichen)
- Validierungen und Filter (z. B. Processor gibt `null` zurück, Exceptions, Fehlerprotokolle)
- Verzweigungen nach Produkt, Tarif, Status oder anderen Merkmalen; Defaultwerte

**Ergebnis für diese Phase:**

1. **Attributtabelle Process** – eine Zeile je **Zielattribut** aus dem Abschnitt „Zielattribute (Zielmodell)“ des
   Attributverzeichnisses, in dessen Reihenfolge:

   | Zielattribut | Herkunft (Quellattribut(e) bzw. „abgeleitet“) | Mapping | Prüfbedarf | OPL | Beleg |
   |---|---|---|---|---|---|

   - **Mapping:** bei „kompakt“ die Regel in einem Satz; bei „ausführlich“ die nummerierten Schritte in der Zelle,
     je Schritt mit Bedingung und Klasse.Methode. **Wertumsetzungen** stehen vollständig in dieser Zelle
     (z. B. „FE → FEUER, LW → LEITUNGSWASSER, …; sonst Fehler“), ebenso Defaults, Validierungen und
     Verzweigungen, die nur dieses Zielattribut betreffen.
   - Zielattribute, die im Referenzprojekt nicht befüllt werden: „nicht befüllt“ und ein OPL-Punkt.
   - Quellattribute, die in keinem Zielattribut ankommen, werden ein OPL-Punkt (keine eigene Tabelle).
   - Gibt es keine Zielattribute (kein Zielmodell zur Entität), hat die Tabelle stattdessen eine Zeile je
     Quellattribut: | Attribut | Ziel (Klasse.Feld) | Mapping | Prüfbedarf | OPL | Beleg |.

2. **Satzregeln Process** – nur Regeln, die den ganzen Satz betreffen:

   | Nr | Regel | Art (Filter, Validierung, Verzweigung) | Prüfbedarf | OPL | Beleg |
   |---|---|---|---|---|---|
