### Phase Write – Schreiben

**Soll:** Für Write gibt es keine Soll-Liste. Maßgeblich sind alle Zielfelder, die der Code-Kontext nachweislich
schreibt. Nummeriere sie selbst mit `W1`, `W2`, … Ob das Zielsystem weitere Felder hat, zeigt der Code nicht; das
ist ein OPL-Punkt.

**Untersuche:** ItemWriter (auch Verzeichnisse `write`/`writer`), DAOs, Repositories, die schreiben, INSERT/UPDATE/MERGE-Statements und Exporte,
Schlüssel- und ID-Vergabe, Versionierung, technische Felder, Reihenfolge und Abhängigkeiten zwischen Zielobjekten,
Chunk- und Transaktionsverhalten, Wiederaufsetzbarkeit, Protokollierung und Fehlertabellen.

**Feldtabelle Write** – eine Zeile je geschriebenem Zielfeld:

| Nr | Zielfeld (Tabelle.Spalte) | Herkunft (Attribut, Process-Ergebnis oder Konstante) | Schreiben | Prüfbedarf | OPL | Beleg |
|---|---|---|---|---|---|---|

- **Schreiben:** Art (Insert/Update/Merge/Export), Format und Konvertierung sowie Bedingungen, die nur dieses Feld
  betreffen.

**Satzregeln Write** – Ziele, Schlüssel- und ID-Vergabe, Reihenfolge, Transaktionen, Fehlerbehandlung und
Protokollierung, Nr `SW1`, `SW2`, …:

| Nr | Regel | Art | Prüfbedarf | OPL | Beleg |
|---|---|---|---|---|---|
