### Phase Write – Schreiben

**Ziel:** Beschreiben, wohin und wie die Ergebnisse zur Entität geschrieben werden.

**Untersuche insbesondere:**
- ItemWriter (auch Klassen in den Verzeichnissen `write`/`writer`), Repositories/DAOs, INSERT/UPDATE/MERGE-Statements,
  Exporte (Dateien, XML/JSON, Schnittstellen)
- Schlüssel- und ID-Vergabe, Versionierung, technische Felder (Anlagedatum, Benutzer)
- Reihenfolge und Abhängigkeiten zwischen Zielobjekten (z. B. Eltern vor Kind)
- Chunk-/Transaktionsverhalten, Wiederaufsetzbarkeit
- Protokollierung und Fehlertabellen

**Gliederung für diese Phase:**
1. **Ziele** – | Ziel (Tabelle/Datei/Schnittstelle) | Art (Insert/Update/Merge/Export) | Klasse.Methode | Beleg |
2. **Geschriebene Felder** – eine Zeile je Zielfeld:
   | Zielfeld | Herkunft (Attribut, Process-Ergebnis oder Konstante) | Format/Konvertierung | Klasse.Methode | Beleg |
3. **Schlüssel, Reihenfolge, Abhängigkeiten** – | Thema | Regel | Beleg |
4. **Fehlerbehandlung und Protokollierung** – | Situation | Verhalten | Ziel (Tabelle/Protokoll) | Beleg |
