### Phase Write – Schreiben

**Ziel:** Beschreiben, wohin und wie die Ergebnisse zu „{{TERMS}}“ geschrieben werden.

**Untersuche insbesondere:**
- ItemWriter, Repositories/DAOs, INSERT/UPDATE/MERGE-Statements, Exporte (Dateien, XML/JSON, Schnittstellen)
- Schlüssel- und ID-Vergabe, Versionierung, technische Felder (Anlagedatum, Benutzer)
- Reihenfolge und Abhängigkeiten zwischen Zielobjekten (z. B. Eltern vor Kind)
- Chunk-/Transaktionsverhalten, Wiederaufsetzbarkeit
- Protokollierung und Fehlertabellen

**Gliederung für diese Phase:**
1. **Ziele** – Tabelle: | Ziel (Tabelle/Datei/Schnittstelle) | Art (Insert/Update/Merge/Export) | Klasse.Methode | Beleg |
2. **Geschriebene Felder** – Tabelle: | Zielfeld | Herkunft (Feld aus Process-Ergebnis oder Konstante) | Format/Konvertierung | Beleg |
3. **Schlüssel, Reihenfolge, Abhängigkeiten**
4. **Fehlerbehandlung und Protokollierung**
