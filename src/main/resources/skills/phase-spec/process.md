### Phase Process – Mapping/Verarbeitung

**Ziel:** Für jedes Attribut der Entität beschreiben, wie es in das Zielmodell überführt wird.

**Untersuche insbesondere:**
- ItemProcessor, Mapper/Converter (auch Klassen in den Verzeichnissen `mapper`/`mapping`) sowie die von ihnen
  aufgerufenen Regel- und Hilfsklassen
- Wertumsetzungen über Enums, Schlüsseltabellen, Maps oder switch/if-Kaskaden
- Berechnungen und Ableitungen (Beträge, Datumswerte, Kennzeichen)
- Validierungen und Filter (z. B. Processor gibt `null` zurück, Exceptions, Fehlerprotokolle)
- Verzweigungen nach Produkt, Tarif, Status oder anderen Merkmalen; Defaultwerte

**Gliederung für diese Phase:**
1. **Attributtabelle Process (Mapping)** – eine Zeile je Attribut aus dem Attributverzeichnis, in dessen Reihenfolge,
   auch für Attribute, die nicht gemappt werden:
   | Attribut | Ziel (Klasse.Feld) | Regel/Transformation | Bedingung/Verzweigung | Default | Validierung | Umgesetzt in (Klasse.Methode) | Beleg | Hinweise |
   Nicht gemappte Attribute: „nicht gemappt“ in der Spalte „Ziel“.
2. **Abgeleitete Zielwerte** – Zielfelder ohne direktes Quellattribut (Berechnungen, Konstanten, IDs):
   | Zielfeld | Herleitung (Formel in Fachsprache) | Klasse.Methode | Beleg |
3. **Wertumsetzungen** – je Code-/Schlüsselliste: | Attribut | Altwert | Neuwert | Bedeutung | Beleg |
4. **Filter und Validierungen** – | Bedingung | Wirkung (verwerfen, Fehler, Default) | Meldung | Beleg |
