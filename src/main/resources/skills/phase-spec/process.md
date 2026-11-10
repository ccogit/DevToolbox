### Phase Process – Mapping/Verarbeitung

**Ziel:** Beschreiben, wie die gelesenen Daten zu „{{TERMS}}“ in das Zielmodell überführt werden.

**Untersuche insbesondere:**
- ItemProcessor, Mapper/Converter sowie die von ihnen aufgerufenen Regel- und Hilfsklassen
- Wertumsetzungen über Enums, Schlüsseltabellen, Maps oder switch/if-Kaskaden
- Berechnungen und Ableitungen (Beträge, Datumswerte, Kennzeichen)
- Validierungen und Filter (z. B. Processor gibt `null` zurück, Exceptions, Fehlerprotokolle)
- Verzweigungen nach Produkt, Tarif, Status oder anderen Merkmalen; Defaultwerte

**Gliederung für diese Phase:**
1. **Mapping-Tabelle** – | Nr | Quelle (Klasse.Feld) | Ziel (Klasse.Feld) | Regel/Transformation | Bedingung | Beleg |
2. **Wertumsetzungen** – je Code-/Schlüsselliste eine Tabelle: | Altwert | Neuwert | Bedeutung | Beleg |
3. **Berechnungen und Ableitungen** – Formel in Fachsprache, Beleg
4. **Validierungen und Filter** – was wird verworfen oder als Fehler markiert, mit welcher Meldung?
5. **Verzweigungen** – unter welchen Bedingungen wird anders gemappt?
