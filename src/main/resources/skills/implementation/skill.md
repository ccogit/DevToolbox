# Skill: Implementierung nach Spezifikation und Standards

Dieser Skill beschreibt, **wie** du eine Aufgabe vom Typ „Implementierung nach Spezifikation“ bearbeitest.
**Was** umzusetzen ist – Entität, Zielprojekt, zu erstellende Artefakte –, steht in der Aufgabe-Datei
(„Aufgabe: …“).

## Grundprinzip: Implementierung nach Beispielen

Du implementierst **nicht frei**, sondern **anhand von Beispielen**. Die Aufgabe enthält im Abschnitt „Standards“
die Klassen einer im Zielprojekt bereits umgesetzten Entität (Beispiel-Entität). Sie sind die verbindliche
**strukturelle Vorlage**:

| Was | Woher |
|---|---|
| Aufbau der Klassen, Basisklassen, Schnittstellen, Annotationen, Paketstruktur, Namens- und Kommentarkonventionen, Stil der Logik (SQL, RowMapper, Anreicherung, Mapping, Schreiben, Fehlerbehandlung) | **Standards** (Vorlagen) |
| Attribute, Datentypen, Quellen (Tabelle.Spalte), Selektion, Anreicherung, Mapping-Regeln, Wertumsetzungen, Zielfelder, Defaults, Sonderfälle | **Spezifikation** (PDF) |

Jedes neue Artefakt entsteht, indem du die passende Vorlage kopierst, die Beispiel-Entität durch die neue Entität
ersetzt und den fachlichen Inhalt gemäß Spezifikation einsetzt. Weicht der Code einer neuen Klasse strukturell von
der Vorlage ab, muss die Spezifikation das verlangen – und du begründest es im Ergebnis.

## Eingaben

- **Aufgabe** („Aufgabe: …“): Zielprojekt, Entität, zu implementierende Phasen, zu erstellende Artefakte mit
  Namen und Packages, Standards.
- **Spezifikation** (PDF): Ergebnis der Spezifikation aus dem Referenzprojekt, vom Fachbereich geprüft und
  geändert. Sie ist für den **Inhalt** maßgeblich – Änderungen des Fachbereichs haben Vorrang vor allem, was du aus
  den Vorlagen oder dem Referenzprojekt ableiten würdest. Je Phase sind relevant:

  | Phase | Abschnitte der Spezifikation | Zu implementieren |
  |---|---|---|
  | Read | Attributtabelle Read, Quellen und Selektion, Einleseschritte je Attribut, Fehlerbehandlung | Datenobjekte (TO, PO, Entity …), Repository und Einleselogik |
  | Process | Attributtabelle Process (Mapping), abgeleitete Zielwerte, Wertumsetzungen, Filter/Validierungen, Verarbeitungsschritte | Verarbeitungs-/Mapping-Logik |
  | Write | Ziele, geschriebene Felder, Schlüssel/Reihenfolge, Fehlerbehandlung, Schreibschritte | Schreiblogik |

  Phasen, die die Aufgabe nicht nennt, implementierst du nicht. Werden für Process oder Write Datenobjekte der Entität
  benötigt, die nicht Teil der Aufgabe sind, gehst du davon aus, dass sie nach den Namenskonventionen der Standards
  bereits existieren.

## Arbeitsregeln

1. **Struktur aus der Vorlage, Inhalt aus der Spezifikation.** Nichts an der Struktur „verbessern“, keine neuen
   Muster, Bibliotheken oder Hilfsklassen einführen, die in den Vorlagen nicht vorkommen.
2. **Jedes Attribut der Spezifikation wird umgesetzt** – in jedem Datenobjekt, in dem die Vorlage vergleichbare
   Attribute führt (TO, PO, Entity …), und in der Einleselogik, die es befüllt. Attribute, die die Spezifikation als
   „nicht gelesen“ ausweist, werden nicht angelegt.
3. **Datentypen wie in der Vorlage abbilden.** Leite die Typ-Zuordnung (z. B. `DECIMAL` → `BigDecimal`,
   `CHAR` → `String`, `DATE` → `LocalDate`) aus den Vorlagen ab; Spalten- und Feldnamen folgen deren Konvention.
4. **Logik wie in der Vorlage:** gleiche Klassenaufteilung (Read: Repository – eines je Entität –, Reader,
   RowMapper, Anreicherer …; Process: Processor, Mapper, Converter …; Write: Writer …), gleiche Art der SQL-Ablage und Parameter, gleiche
   Fehlerbehandlung. Selektion, Anreicherung, Mapping-Regeln und Schreiblogik gemäß Spezifikation.
5. **Basisklassen und Schnittstellen** aus dem Abschnitt „Standards“ werden verwendet, nicht neu angelegt.
6. **Vollständiger Code.** Jede Datei vollständig und kompilierbar ausgeben – keine Auslassungen wie `...` oder
   „analog zu oben“.
7. **Offenes sichtbar machen.** Was Spezifikation oder Vorlage nicht klären (z. B. offene Fragen der Spezifikation),
   setzt du nach bester Annahme um, markierst es im Code mit `// TODO [OFFEN]: …` und führst es in den offenen
   Punkten auf. Produktspezifisches aus der Spezifikation (`[PRODUKTSPEZIFISCH]`) setzt du nur um, wenn die
   Spezifikation es für das Zielprojekt bestätigt.
8. **Tabellen statt Fließtext** für alle Übersichten im Ergebnis.

## Vorgehen

1. Lies die Aufgabe und die Liste der zu erstellenden Artefakte.
2. Lies die Standards: Konventionen-Tabelle, Steckbriefe, Vorlagen, Basisklassen. Notiere die Typ-Zuordnung und das
   Muster der Einleselogik der Beispiel-Entität.
3. Lies in der Spezifikation die Abschnitte der zu implementierenden Phasen (siehe Tabelle oben) und die offenen Fragen.
4. **Read:** Erstelle die Datenobjekte (TO, PO, Entity …) – je Vorlage eine neue Klasse mit den Attributen der
   Spezifikation – und die Einleselogik, sodass jedes Attribut wie spezifiziert gelesen bzw. angereichert wird.
5. **Process:** Erstelle die Verarbeitungslogik nach der Vorlage, sodass jedes Attribut wie in der Attributtabelle
   Process gemappt, umgeschlüsselt, berechnet oder validiert wird.
6. **Write:** Erstelle die Schreiblogik nach der Vorlage für alle Ziele und Zielfelder der Spezifikation.
7. Prüfe die Abdeckung: Jedes Attribut der Spezifikation ist in allen implementierten Phasen berücksichtigt.

## Ausgabeformat

Halte dich an die Gliederung aus der Aufgabe: je Artefakt eine Überschrift mit vollständigem Pfad und darunter der
vollständige Quelltext als Codeblock; danach die Tabellen Attributabdeckung, Abweichungen von der Vorlage und offene
Punkte.
