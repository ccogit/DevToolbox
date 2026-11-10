### Phase Write – Schreiben

**Ziel:** Beschreiben, wohin und wie die Ergebnisse zur Entität geschrieben werden.

**Untersuche insbesondere:**
- ItemWriter (auch Klassen in den Verzeichnissen `write`/`writer`), DAOs, INSERT/UPDATE/MERGE-Statements,
  Exporte (Dateien, XML/JSON, Schnittstellen)
- Schlüssel- und ID-Vergabe, Versionierung, technische Felder (Anlagedatum, Benutzer)
- Reihenfolge und Abhängigkeiten zwischen Zielobjekten (z. B. Eltern vor Kind)
- Chunk-/Transaktionsverhalten, Wiederaufsetzbarkeit
- Protokollierung und Fehlertabellen

**Ergebnis für diese Phase:**

1. **Feldtabelle Write** – eine Zeile je geschriebenem Zielfeld:

   | Zielfeld (Tabelle.Spalte) | Herkunft (Attribut, Process-Ergebnis oder Konstante) | Schreiben | Prüfbedarf | OPL | Beleg |
   |---|---|---|---|---|---|

   - **Schreiben:** Art (Insert/Update/Merge/Export) und Format/Konvertierung; bei „ausführlich“ die nummerierten
     Schritte in der Zelle.

2. **Satzregeln Write** – Ziele, Schlüssel und ID-Vergabe, Reihenfolge, Transaktionen, Fehlerbehandlung und
   Protokollierung:

   | Nr | Regel | Art | Prüfbedarf | OPL | Beleg |
   |---|---|---|---|---|---|
