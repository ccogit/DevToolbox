### Phase Write – Schreiben

**Ziel:** Beschreiben, wohin und wie die Ergebnisse zur Entität geschrieben werden.

**Untersuche insbesondere:**
- ItemWriter (auch Klassen in den Verzeichnissen `write`/`writer`), Repositories/DAOs, INSERT/UPDATE/MERGE-Statements,
  Exporte (Dateien, XML/JSON, Schnittstellen)
- Schlüssel- und ID-Vergabe, Versionierung, technische Felder (Anlagedatum, Benutzer)
- Reihenfolge und Abhängigkeiten zwischen Zielobjekten (z. B. Eltern vor Kind)
- Chunk-/Transaktionsverhalten, Wiederaufsetzbarkeit
- Protokollierung und Fehlertabellen

**Gliederung für diese Phase (beide Detailgrade):**
1. **Ziele** – | Ziel (Tabelle/Datei/Schnittstelle) | Art (Insert/Update/Merge/Export) | Klasse.Methode | Beleg |
2. **Geschriebene Felder** – eine Zeile je Zielfeld:
   | Zielfeld | Herkunft (Attribut, Process-Ergebnis oder Konstante) | Format/Konvertierung | Klasse.Methode | Beleg |
3. **Schlüssel, Reihenfolge, Abhängigkeiten** – | Thema | Regel | Beleg |
4. **Fehlerbehandlung und Protokollierung** – | Situation | Verhalten | Ziel (Tabelle/Protokoll) | Beleg |

**Zusätzlich bei Detailgrad „ausführlich“:**
5. **Schreibschritte** – Reihenfolge der Schritte für einen Satz:
   | Nr | Schritt (prüfen, Schlüssel vergeben, schreiben, aktualisieren, protokollieren) | Ziel | Klasse.Methode | Bedingung | Beleg |
6. **Befüllung je Zielfeld** – für Zielfelder, die in mehreren Schritten befüllt oder geändert werden:
   | Zielfeld | Nr | Schritt | Klasse.Methode | Ergebnis | Beleg |
