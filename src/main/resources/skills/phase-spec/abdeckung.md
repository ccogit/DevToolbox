## Abdeckung

Die Aufgabe verlangt je Phase zusätzlich den Abschnitt **Abdeckung**. Er weist nach, dass keine Logik des Codes
fehlt, und wird vom Code aus erstellt, nicht von den Soll-Listen aus.

1. Gehe die Klassen des Code-Kontexts durch, die Logik der Phase für die Entität tragen, und darin jede Methode
   mit fachlicher Logik, in der Reihenfolge des Codes.
2. Trage **jede** Bedingung und Regel dieser Methoden als eigene Zeile ein: jedes `if`/`else`, jeden
   `switch`-Zweig, jeden Filter, jede Validierung, jede Berechnung, jeden Default, jede geworfene Exception und
   jeden Lookup. Mehrere Bedingungen einer Methode sind mehrere Zeilen.

   | Klasse.Methode | Bedingung bzw. Regel (fachlich, mit den konkreten Werten) | Beschrieben in |
   |---|---|---|

3. **Beschrieben in** nennt die Nr der Zeile oder Satzregel, in deren Regeldetails die Bedingung steht (z. B.
   `P2`, `SP3`). Steht sie noch nirgends, ergänze sie dort, bevor du die Zeile einträgst.
4. Gehört eine Bedingung zu keiner Soll-Zeile und zu keiner Satzregel, lege eine neue Satzregel an. Ist unklar,
   wozu sie fachlich dient, trage zusätzlich einen OPL-Punkt ein und nenne seine Nr in dieser Spalte.
5. Methoden ohne fachliche Logik (Logging, reine Delegation, Getter/Setter) bleiben weg.
6. Unter der Tabelle steht eine Zeile `Bedingungen im Code: <n> · beschrieben: <n> · offen (OPL): <n>`.
