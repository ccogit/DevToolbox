### Phase Process – Mapping/Verarbeitung

**Ziel:** Für jedes Attribut der Entität beschreiben, wie es in das Zielmodell überführt wird.

**Untersuche insbesondere:**
- ItemProcessor, Mapper/Converter (auch Klassen in den Verzeichnissen `mapper`/`mapping`) sowie die von ihnen
  aufgerufenen Regel- und Hilfsklassen
- Wertumsetzungen über Enums, Schlüsseltabellen, Maps oder switch/if-Kaskaden
- Berechnungen und Ableitungen (Beträge, Datumswerte, Kennzeichen)
- Validierungen und Filter (z. B. Processor gibt `null` zurück, Exceptions, Fehlerprotokolle)
- Verzweigungen nach Produkt, Tarif, Status oder anderen Merkmalen; Defaultwerte

**Ergebnis für diese Phase:**

1. **Attributtabelle Process** – eine Zeile je Attribut des Attributverzeichnisses, in dessen Reihenfolge:

   | Attribut | Ziel (Klasse.Feld) | Mapping | Prüfbedarf | OPL | Beleg |
   |---|---|---|---|---|---|

   - **Mapping:** bei „kompakt“ die Regel in einem Satz; bei „ausführlich“ die nummerierten Schritte in der Zelle,
     je Schritt mit Bedingung und Klasse.Methode. **Wertumsetzungen** stehen vollständig in dieser Zelle
     (z. B. „FE → FEUER, LW → LEITUNGSWASSER, …; sonst Fehler“), ebenso Defaults, Validierungen und
     Verzweigungen, die nur dieses Attribut betreffen.
   - Nicht gemappte Attribute: „nicht gemappt“ und ein OPL-Punkt, ob das so übernommen werden soll.
   - **Abgeleitete Zielwerte** ohne eigenes Quellattribut (Berechnungen, Konstanten, IDs) sind Zeilen derselben
     Tabelle: Spalte „Attribut“ = „(abgeleitet)“, Herleitung in „Mapping“.

2. **Satzregeln Process** – nur Regeln, die den ganzen Satz betreffen:

   | Nr | Regel | Art (Filter, Validierung, Verzweigung) | Prüfbedarf | OPL | Beleg |
   |---|---|---|---|---|---|
