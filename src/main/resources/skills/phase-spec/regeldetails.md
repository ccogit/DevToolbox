## Regeldetails

Die Aufgabe verlangt je Phase zusätzlich zu den beiden Tabellen den Abschnitt **Regeldetails**. Dort steht die
vollständige Logik; die Tabellen sind die Übersicht.

- **Tabellenzelle.** Die Zelle „Einlesen“, „Mapping“ bzw. „Schreiben“ enthält eine Kurzfassung in ein bis zwei
  Sätzen und den Verweis `Details: <Nr>`. Das ersetzt für diese Zeilen die Vorgabe des Detailgrads für die Zelle:
  Die einzelnen Schritte stehen in den Regeldetails, nicht in der Tabelle. Das ist die einzige erlaubte
  Doppelung. Zeilen mit [1:1] oder [TECHNISCH] ohne Verzweigung brauchen keine Regeldetails; ihre Zelle ist dann
  vollständig.
- **Ein Unterabschnitt je Zeile mit Logik.** Jede Attribut-, Feld- und Satzregelzeile mit [PRÜFEN] bekommt eine
  Überschrift der Ebene 3 aus Nr und Schlüssel, z. B. `### P2 NachlassZuschlagArt` oder `### SP3`, in der
  Reihenfolge der Tabellen. Darunter, jeweils nur soweit der Code etwas dazu zeigt:
  1. **Zweck** – ein Satz: Was bedeutet der Wert fachlich, wofür wird er gebraucht?
  2. **Ablauf** – nummerierte Schritte in der Reihenfolge des Codes. Jeder Schritt nennt Bedingung → Ergebnis.
     Verschachtelte Fälle stehen als Unterpunkte (2.1, 2.2). Jeder Zweig steht da, auch der „sonst“-Zweig.
     Am Ende jedes Schritts steht seine Fundstelle in Klammern: `(Klasse.Methode)`.
  3. **Wertumsetzung** – Tabelle | Quellwert | Zielwert | Bedingung |, eine Zeile je Wertepaar, vollständig.
     Zeigt der Code-Kontext die Werte nicht (Schlüsseltabelle, Lookup, Service), nenne Tabelle bzw. Service,
     Schlüssel und Verwendungszweck und verweise auf den OPL-Punkt.
  4. **Sonderfälle und Fehlerverhalten** – Ausnahmen für einzelne Produkte, Tarife, Tarifgenerationen, Status,
     Stichtage oder Schlüsselwerte; was bei leerem, ungültigem oder mehrfach belegtem Wert passiert (Default,
     Überspringen, Fehler mit Meldung, Abbruch des Satzes).
  5. **Abhängigkeiten** – andere Attribute, Hilfswerte, Lookups oder vorher gelaufene Schritte, von denen das
     Ergebnis abhängt, und Attribute, die von diesem Wert abhängen.
- **Umfang.** Es gibt keine Längenvorgabe. Ein Unterabschnitt ist erst fertig, wenn ein Entwickler die Logik ohne
  Blick in den Code des Referenzprojekts nachbauen könnte. Fasse keine Zweige zusammen und lasse keinen weg, weil
  er selten ist.
- **Reicht die Ausgabe nicht,** höre nach einem vollständigen Unterabschnitt auf und melde `UNVOLLSTÄNDIG` mit der
  letzten vollständigen Nr, statt die restlichen Unterabschnitte zu kürzen.
