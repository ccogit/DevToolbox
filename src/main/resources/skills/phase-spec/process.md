### Phase Process – Mapping/Verarbeitung

**Ziel:** Für jedes Attribut der Entität beschreiben, wie es in das Zielmodell überführt wird.

**Untersuche insbesondere:**
- ItemProcessor, Mapper/Converter (auch Klassen in den Verzeichnissen `mapper`/`mapping`) sowie die von ihnen
  aufgerufenen Regel- und Hilfsklassen
- Wertumsetzungen über Enums, Schlüsseltabellen, Maps oder switch/if-Kaskaden
- Berechnungen und Ableitungen (Beträge, Datumswerte, Kennzeichen)
- Validierungen und Filter (z. B. Processor gibt `null` zurück, Exceptions, Fehlerprotokolle)
- Verzweigungen nach Produkt, Tarif, Status oder anderen Merkmalen; Defaultwerte

**Gliederung für diese Phase (beide Detailgrade):**
1. **Attributtabelle Process (Mapping)** – eine Zeile je Attribut aus dem Attributverzeichnis, in dessen Reihenfolge,
   auch für Attribute, die nicht gemappt werden:
   | Attribut | Prüfbedarf | Prüfgrund | Ziel (Klasse.Feld) | Regel/Transformation | Bedingung/Verzweigung | Default | Validierung | Umgesetzt in (Klasse.Methode) | Beleg | Hinweise |
   Prüfbedarf: [PRÜFEN], [TECHNISCH] oder [1:1] (siehe „Prüfbedarf je Regel“). Nicht gemappte Attribute:
   „nicht gemappt“ in der Spalte „Ziel“, Prüfbedarf [PRÜFEN] mit Grund „wird nicht übernommen“.
2. **Abgeleitete Zielwerte** – Zielfelder ohne direktes Quellattribut (Berechnungen, Konstanten, IDs):
   | Zielfeld | Herleitung (Formel in Fachsprache) | Prüfbedarf | Prüfgrund | Klasse.Methode | Beleg |
3. **Wertumsetzungen** (immer [PRÜFEN]) – je Code-/Schlüsselliste: | Attribut | Altwert | Neuwert | Bedeutung | Beleg |
4. **Filter und Validierungen** (immer [PRÜFEN]) – | Bedingung | Wirkung (verwerfen, Fehler, Default) | Meldung | Beleg |

**Zusätzlich bei Detailgrad „ausführlich“:**
5. **Verarbeitungsschritte je Attribut** – die Kette jedes Attributs vom Lesemodell bis zum Zielfeld, Schritt für
   Schritt; ein Attribut mit mehreren Schritten bekommt mehrere Zeilen (Nr 1, 2, …):
   | Attribut | Nr | Schritt (übernehmen, umschlüsseln, berechnen, ableiten, validieren, Default setzen) | Prüfbedarf | Klasse.Methode | Bedingung | Ergebnis/Zwischenwert | Beleg |
6. **Ablauf der Verarbeitung** – Reihenfolge der Mapping- und Regelschritte für einen Satz:
   | Nr | Schritt | Klasse.Methode | Betroffene Attribute | Bedingung | Beleg |
