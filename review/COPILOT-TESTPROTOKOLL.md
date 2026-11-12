# Testprotokoll: Copilot-Aufträge der DevToolbox

Dieses Protokoll führen Sie selbst mit Microsoft 365 Copilot Chat am echten Fall durch (Entität Nachlass/Zuschlag,
drei Teiltabellen). Es vergleicht vier Varianten des Auftrags (A bis D) und liefert die Grundlage für die
Entscheidung, ob der neue Auftrag freigegeben wird. Alle Größenangaben der DevToolbox (Limit 30.000 Tokens,
80 % Anteil der Code-Datei, 60 Soll-Zeilen) sind Startwerte und Annahmen. Dieser Test kalibriert sie.

**Gilt für jede Variante und jeden Prompt: Höchstens 3 Dateien je Prompt, auch bei jedem Folgeprompt.** Der
Fortsetzungs- und der Nachbesserungsprompt werden ohne Datei gesendet. Wer in einer Variante mehr als drei Dateien
hochlädt, verfälscht den Vergleich; tragen Sie solche Fälle als Abweichung im Bewertungsbogen ein.

Die drei Prompts (Startprompt, Fortsetzungsprompt, Nachbesserungsprompt) werden nicht hier wiederholt. Der
Startprompt und der Fortsetzungsprompt stehen im Protokoll des Jobs `phase-spec`, der Nachbesserungsprompt im
Protokoll des Jobs `spec-check`. Kopieren Sie sie von dort unverändert.

---

## 1 Vorbereitung

Führen Sie diese Schritte vor dem ersten Lauf aus und tragen Sie die Werte in die Felder ein.

1. Legen Sie den Stand fest, der getestet wird, und halten Sie ihn fest.

   | Angabe | Wert |
   |---|---|
   | Datum des Tests | |
   | Name des Testers | |
   | Stand Referenzprojekt (Commit oder Datum) | |
   | Stand Zielprojekt (Commit oder Datum) | |
   | Stand DDL-Dateien (Dateiname, Datum) | |
   | Stand Zielmodell (Dateiname, Datum) | |
   | Stand der DevToolbox, Variante A (Commit von `main` vor der Umsetzung) | |
   | Stand der DevToolbox, Varianten B bis D (Commit oder ZIP-Datum) | |
   | Copilot-Oberfläche (Browser oder Teams) und Datum der Prüfung | |

2. Legen Sie die gemeinsamen Einstellungen fest. Sie sind in allen vier Varianten **identisch**; nur die in
   Abschnitt 3 genannten Parameter unterscheiden sich.

   | Einstellung | Wert |
   |---|---|
   | Projekt (Zielprojekt) | |
   | Referenzprojekt | |
   | Entität | Nachlass/Zuschlag |
   | Suchbegriffe | |
   | Phasen | |
   | Detailgrad | |
   | Weitere Einstellungen, die nicht Teil von Abschnitt 3 sind | |

3. Legen Sie den **Goldstandard** fest, bevor Sie die erste Variante starten. Er ist die Messlatte für alle
   Varianten und wird danach nicht mehr geändert.

   | Teiltabelle | Anzahl Soll-Schlüssel (Read) | Ablage der Liste der Soll-Schlüssel |
   |---|---|---|
   | Teiltabelle 1: | | |
   | Teiltabelle 2: | | |
   | Teiltabelle 3: | | |
   | Summe | | |

   | Phase | Anzahl Soll-Zeilen | Ablage der bekannten Regeln (Liste oder Datei) |
   |---|---|---|
   | Read | | |
   | Process | | |
   | Write (Zahl der Zielfelder, soweit bekannt) | | |

   Der Goldstandard enthält je Teiltabelle die Liste der Soll-Schlüssel im Format `Tabelle.Spalte` und die
   bekannten Regeln mit ihrer Fundstelle. Aus ihm beurteilen Sie später, ob eine Regel stimmt, ob sie erfunden ist
   oder ob sie nicht entscheidbar ist.

4. Legen Sie für jede Variante einen Ordner an, zum Beispiel `test/A-1`, `test/A-2`, `test/B-1` bis `test/D-2`.
   Darin liegen die drei hochgeladenen Dateien, alle Ergebnisdateien von Copilot und, ab Variante B, der
   Prüfbericht von `spec-check`.

5. Beachten Sie die Regeln für alle Läufe:
   - Je Lauf ein **neuer Chat**. Kein Chat wird für zwei Varianten oder zwei Durchgänge wiederverwendet.
   - Jede Variante wird **zweimal** durchgeführt (Durchgang 1 und Durchgang 2), jeweils in einem neuen Chat.
   - Die Dateien werden nach dem Start der DevToolbox nicht von Hand verändert.
   - Bei jedem Prompt gilt: höchstens 3 Dateien.
   - Notieren Sie die Zeit ab dem Start des Jobs bis zu dem Zeitpunkt, an dem Sie das Ergebnis als abnahmefähig
     ansehen (Minuten). Schwierigkeiten und Wiederholungen zählen mit.

6. Legen Sie fest, ob die Läufe hintereinander am selben Tag stattfinden. Notieren Sie Abweichungen im
   Bewertungsbogen (Abschnitt 5).

   | Angabe | Wert |
   |---|---|
   | Läufe am selben Tag? | |
   | Bemerkungen | |

---

## 2 Kalibrierung

Ziel: Sie ermitteln, bis zu welcher Größe Copilot im Mandanten Anfang, Mitte, 90 % und Ende einer Datei findet.
Daraus setzen Sie `copilotLimit`. Verwenden Sie die Dateien in `copilot-testdateien` mit 10k, 50k und 100k Tokens.
Die Lösungswerte stehen in `copilot-testdateien/LOESUNG.md`. **Laden Sie `LOESUNG.md` nie zu Copilot hoch.**
Öffnen Sie die großen Testdateien nicht von Hand.

Lösungswerte aus `LOESUNG.md`:

| Datei | Kennwort Abschnitt 1 | Kennwort Mitte | Kennwort 90 % | Schlusswort | Abschnitte |
|---|---|---|---|---|---|
| `copilot-test-010k-tokens.md` | Tukan1 | Nr. 22: Quarz22 | Nr. 39: Igel39 | Nachtfalter-ENDE-857 | 44 |
| `copilot-test-050k-tokens.md` | Saphir1 | Nr. 109: Saphir109 | Nr. 197: Uhu197 | Bernstein-ENDE-952 | 219 |
| `copilot-test-100k-tokens.md` | Hafen1 | Nr. 219: Tukan219 | Nr. 394: Eisvogel394 | Bernstein-ENDE-681 | 438 |

Gehen Sie so vor, mit der kleinsten Datei beginnend:

1. Öffnen Sie einen neuen Chat und laden Sie **eine** Testdatei hoch (eine Datei je Prompt).
2. Fragen Sie nacheinander:
   1. „Wie lautet das Kennwort von Abschnitt 1?“ Antwortet Copilot, wurde die Datei angenommen.
   2. „Wie lautet das Kennwort von Abschnitt <Nr. Mitte>?“
   3. „Wie lautet das Kennwort von Abschnitt <Nr. 90 %>?“
   4. „Wie lautet das Schlusswort am Ende der Datei?“ Nur wenn es stimmt, wurde die Datei bis zum Ende verarbeitet.
   5. „Wie viele Abschnitte enthält die Datei?“ Eine zu kleine Zahl zeigt, wo Copilot abgeschnitten hat.
3. Vergleichen Sie jede Antwort mit der Tabelle oben. Stimmt ein Kennwort nicht, hat Copilot die Stelle nicht
   gelesen oder die Antwort erfunden. Tragen Sie das in die Tabelle unten ein.
4. Wiederholen Sie die Schritte 1 bis 3 mit der nächstgrößeren Datei, jeweils in einem neuen Chat.
5. Hört die Antwort auf eine Frage auf, richtig zu sein, ist die Grenze für diese Dateigröße erreicht. Testen Sie
   bei Bedarf die größere Datei trotzdem, um zu sehen, ob die Grenze eindeutig ist.

Ergebnistabelle (tragen Sie je Frage „richtig“, „falsch“ oder „keine Antwort“ ein):

| Datei | Angenommen? | Abschnitt 1 | Mitte | 90 % | Schlusswort | Zahl der Abschnitte (Antwort) | Alle vier richtig? |
|---|---|---|---|---|---|---|---|
| 10k | | | | | | | |
| 50k | | | | | | | |
| 100k | | | | | | | |
| Weitere Datei: | | | | | | | |

Auswertung:

| Angabe | Wert |
|---|---|
| Größte Datei, bei der alle vier Antworten richtig sind (Tokens) | |
| Daraus `copilotLimit` (80 % dieses Werts) | |
| Bisherige Vorgabe der DevToolbox (30.000 geschätzte Tokens) beibehalten oder ersetzen? | |
| Bemerkungen (z. B. wo Copilot abschneidet, ob die Grenze schwankt) | |

Hinweis: Dass Copilot ein Kennwort an einer Stelle findet, beweist nicht, dass Copilot den ganzen Inhalt dort
auswertet. Die Kalibrierung liefert deshalb nur eine Obergrenze für das Limit. Ob die Spezifikation vollständig
ist, zeigen erst die Messgrößen aus Abschnitt 4. Beachten Sie außerdem, dass die Testdateien Textabschnitte sind
und kein Code. Die DevToolbox schätzt Tokens als Zeichen geteilt durch 4; Abweichungen im Mandanten tragen Sie
in die Bemerkungen ein.

Setzen Sie danach `copilotLimit` in der DevToolbox auf den ermittelten Wert (für die Varianten C und D). Sie
verwenden dort das Feld „Copilot-Limit (Tokens)“; der Wert gilt für alle drei Dateien zusammen.

---

## 3 Varianten A bis D

Die Varianten unterscheiden sich nur in den hier genannten Einstellungen. Alle anderen Einstellungen sind die
gemeinsamen Einstellungen aus Abschnitt 1. **Bei jedem Prompt jeder Variante: höchstens 3 Dateien.**

| Kürzel | Auftrag | Zweck |
|---|---|---|
| A | heutiger Stand von `main` | Vergleichsbasis |
| B | neue Texte und Soll-Listen, Filter aus | Wirkung der Texte allein |
| C | Zielauftrag mit allen Vorgaben, `tables` mit den drei Teiltabellen | Gesamtwirkung |
| D | wie C, aber ein Lauf je Phase, zusammengeführt mit `spec-check` | Vergleich mit der Alternative aus 5.4 des Umsetzungsauftrags |

Parametereinstellungen im Überblick:

| Parameter (Bezeichnung im Formular) | A | B | C | D |
|---|---|---|---|---|
| DevToolbox-Stand | `main` vor der Umsetzung | neuer Stand | neuer Stand | neuer Stand |
| Kommentare ignorieren (`ignoreComments`) | gibt es nicht | aus | Vorgabe (an) | Vorgabe (an) |
| Dateien ausschließen (`excludeFiles`) | gibt es nicht | leer | nach Bedarf, siehe C | wie C |
| Copilot-Limit (`copilotLimit`) | Vorgabe des alten Stands | `0` | Wert aus der Kalibrierung | wie C |
| Kompakt (`compact`) | Vorgabe des alten Stands | aus (`false`) | Vorgabe (an) | Vorgabe (an) |
| Tabellen der Entität (`tables`) | gibt es nicht | leer | die drei Teiltabellen | die drei Teiltabellen |
| Phasen je Lauf | wie gemeinsam | wie gemeinsam | wie gemeinsam | eine Phase je Lauf |

### Variante A – heutiger Stand von `main`

1. Bauen Sie die DevToolbox mit dem Commit von `main` vor der Umsetzung (siehe Abschnitt 1) und starten Sie
   den Job `phase-spec` mit den gemeinsamen Einstellungen. Lassen Sie alle weiteren Parameter auf ihrer
   Vorgabe des alten Stands.
2. Notieren Sie im Bewertungsbogen die Größe der drei Dateien aus dem Protokoll (Zeichen, Zeilen, Tokens, soweit
   der alte Stand sie ausgibt).
3. Öffnen Sie einen neuen Chat. Laden Sie die drei Dateien hoch (höchstens 3 Dateien) und fügen Sie den im
   Protokoll genannten Prompt ein.
4. Laden Sie das Ergebnis herunter, oder kopieren Sie den Codeblock in eine Datei, und legen Sie es im Ordner des
   Durchgangs ab. Notieren Sie, ob eine Datei geliefert wurde oder nur ein Codeblock.
5. Der alte Stand erzeugt keine Nummern und keine Soll-Listen in der Aufgabe. Zählen Sie die Messgrößen deshalb
   anhand des Goldstandards **von Hand** und vermerken Sie das im Bewertungsbogen. Der Job `spec-check` ist für
   Variante A nicht anwendbar.
6. Falls Copilot abbricht, notieren Sie die Stelle. Senden Sie danach, wenn Sie das möchten, einen
   Folgeprompt ohne Datei, und notieren Sie ihn als zusätzlichen Prompt.
7. Wiederholen Sie die Schritte 3 bis 6 in einem neuen Chat (Durchgang 2).

### Variante B – neue Texte und Soll-Listen, Filter aus

1. Starten Sie den Job `phase-spec` im neuen Stand mit den gemeinsamen Einstellungen und diesen Werten:
   `ignoreComments=false`, `excludeFiles` leer, `copilotLimit=0`, `compact=false`. `tables` bleibt leer.
2. Lesen Sie das Protokoll: Größe der drei Dateien, Sollzahlen je Phase. Notieren Sie beides.
3. Öffnen Sie einen neuen Chat, laden Sie die drei Dateien hoch (höchstens 3 Dateien) und fügen Sie den
   **Startprompt** aus dem Protokoll ein.
4. Laden Sie die Ergebnisdatei herunter und legen Sie sie im Ordner des Durchgangs ab.
5. Meldet Copilot `UNVOLLSTÄNDIG`: Senden Sie im selben Chat den **Fortsetzungsprompt** (ohne Datei) und legen Sie
   die Teildatei im selben Ordner ab. Wiederholen Sie das, bis Copilot meldet, dass alle Soll-Zeilen vorliegen, oder
   bis Sie abbrechen. Notieren Sie jeden Prompt.
6. Starten Sie den Job `spec-check` mit dem Ordner des Durchgangs. Legen Sie den Prüfbericht ab.
7. Enthält das Protokoll von `spec-check` einen **Nachbesserungsprompt**, senden Sie ihn im selben Chat (ohne
   Datei), legen Sie die Korrekturdatei im Ordner ab und starten Sie `spec-check` erneut. Notieren Sie die Zahl der
   Nachbesserungsrunden.
8. Wiederholen Sie die Schritte 3 bis 7 in einem neuen Chat (Durchgang 2).

### Variante C – Zielauftrag mit allen Vorgaben

1. Setzen Sie `tables` auf die drei Teiltabellen der Entität (Namen mit oder ohne Schema, durch Kommas getrennt).
   Tragen Sie die Namen hier ein: `______________________`.
2. Lassen Sie `ignoreComments` (Kommentare ignorieren) und `compact` (Kompakt) auf ihrer Vorgabe. Setzen Sie
   `copilotLimit` auf den Wert aus der Kalibrierung (Abschnitt 2).
3. Starten Sie den Job `phase-spec` und lesen Sie das Protokoll. Beachten Sie Warnungen und Empfehlungen.
   Notieren Sie Warnungen im Bewertungsbogen.
   - Nennt das Protokoll eine Überschreitung des Limits, setzen Sie nur die dort genannte Empfehlung um, falls Sie
     das wünschen: `excludeFiles` mit den vorgeschlagenen Dateien, eine Phase, weniger Tabellen. Notieren Sie jede
     Anpassung mit Grund. Starten Sie den Job danach neu.
   - Notieren Sie, wie viele Dateien durch Kommentarfilter, `excludeFiles` und priorisiertes Weglassen entfallen sind
     und welche Dateien im Abschnitt „Nicht enthaltene Dateien“ stehen.
4. Führen Sie die Schritte 3 bis 8 von Variante B aus (neuer Chat, drei Dateien, Startprompt, Ergebnisdatei,
   gegebenenfalls Fortsetzungsprompt, `spec-check`, gegebenenfalls Nachbesserungsprompt, zweiter Durchgang).
5. Prüfen Sie nach `spec-check`, welche der weggelassenen Dateien fachlich nötig gewesen wären (zum Beispiel
   Konstanten- oder Enum-Klassen), und notieren Sie sie im Fehlerprotokoll (Abschnitt 5.2).

### Variante D – ein Lauf je Phase, zusammengeführt mit `spec-check`

1. Verwenden Sie dieselben Einstellungen wie in Variante C, wählen Sie aber für jeden Lauf **nur eine Phase**
   (Read, Process, Write). Das Protokoll kann bei mehr als 60 Soll-Zeilen außerdem einen Lauf je Tabelle
   empfehlen; ist das der Fall, notieren Sie es und führen Sie die Läufe wie unten beschrieben je Tabelle durch.
2. Führen Sie je Phase (und gegebenenfalls je Tabelle über das Feld „Tabellen der Entität (DDL)“) einen eigenen
   Lauf durch. Jeder Lauf hat einen **neuen Chat**, hochgeladen werden höchstens 3 Dateien, und der Startprompt
   stammt aus dem Protokoll des jeweiligen Laufs.
3. Legen Sie die Ergebnisdateien aller Läufe eines Durchgangs in **einen** Ordner. Der Job `spec-check` führt
   sie zusammen (Datei `spezifikation-<e>-gesamt.md`) und prüft den zusammengeführten Stand.
4. Legen Sie die Aufgabe-, Code- und Ergebnisdateien der Läufe so im Ordner ab, wie es der Job `spec-check`
   verlangt. Notieren Sie, falls der Job wegen mehrerer Aufgabendateien eine Meldung ausgibt, den Wortlaut und
   Ihre Lösung.
5. Senden Sie Fortsetzungs- und Nachbesserungsprompts je Lauf im jeweiligen Chat, falls nötig (ohne Datei).
6. Wiederholen Sie alles in neuen Chats als Durchgang 2.
7. Zählen Sie Prompts, Anhänge und Zeit über **alle** Läufe des Durchgangs zusammen.

---

## 4 Messgrößen

Erfassen Sie diese Werte je Variante, je Durchgang und, soweit angegeben, je Phase.

| Nr | Messgröße | Bestimmung |
|---|---|---|
| M1 | Anteil vollständig beschriebener Soll-Attribute | Zahl der Soll-Attribute mit vollständig ausgefüllter Zeile geteilt durch die Zahl der Soll-Attribute des Goldstandards. Eine Zeile ist vollständig, wenn alle Pflichtzellen ausgefüllt sind und keine Auslassung enthalten ist. |
| M2 | Fehlende Schlüssel | Soll-Schlüssel aus dem Goldstandard, die im Ergebnis fehlen. Ab Variante B aus dem Prüfbericht von `spec-check`, in Variante A von Hand gezählt. |
| M3 | Doppelte Schlüssel | Schlüssel, die mehr als einmal vorkommen (Prüfbericht bzw. von Hand). |
| M4 | Unbekannte Schlüssel | Schlüssel im Ergebnis, die nicht im Goldstandard stehen (Prüfbericht bzw. von Hand). |
| M5 | Regeln ohne Beleg | Regeln, in denen die Spalte „Beleg“ leer ist, ohne dass `nicht gelesen`, `nicht befüllt` oder `nicht belegt` dasteht. |
| M6 | Regeln mit falschem Beleg | Die Klasse oder die Methode gibt es im Code-Kontext nicht, oder die Fundstelle belegt die Aussage nicht. Die ersten beiden Fälle liefert `spec-check` (Prüfung D4). Ob die Fundstelle die Aussage trägt, prüfen Sie als Mensch. |
| M7 | Erfundene Regeln | Regeln, die der Goldstandard bzw. der Code widerlegen. Zählen Sie diese **getrennt** von „nicht entscheidbar“. |
| M8 | Nicht entscheidbare Regeln | Regeln, die Sie mit Goldstandard und Code weder bestätigen noch widerlegen können. |
| M9 | OPL-Punkte gesamt | Zahl der Punkte der Offene-Punkte-Liste. |
| M10 | OPL-Punkte doppelt | Punkte mit gleichem Inhalt, mehrfach aufgeführt. |
| M11 | OPL-Punkte mit ungültigem Verweis | Verweise in Tabellen auf eine OPL-Nr, die es nicht gibt (Prüfung D5), und Punkte, deren „Betrifft“ auf eine nicht vorhandene Zeile zeigt. |
| M12 | Abbruch oder Kürzung | Ja/Nein; wenn ja, die letzte gelieferte Nr und ob Copilot den Abbruch gemeldet hat (Status `UNVOLLSTÄNDIG`) oder nicht (unmarkierter Abbruch). |
| M13 | Datei oder Codeblock | Hat Copilot eine Markdown-Datei zum Download bereitgestellt, oder nur einen Codeblock im Chat? |
| M14 | Prompts | Zahl der gesendeten Prompts bis zum abnahmefähigen Ergebnis. |
| M15 | Anhänge je Prompt | Zahl der Dateien je Prompt. **Höchstens 3** je Prompt; ein höherer Wert ist eine Abweichung. |
| M16 | Minuten bis zum abnahmefähigen Ergebnis | Vom Start des Jobs bis zum Zeitpunkt, an dem Sie das Ergebnis als abnahmefähig ansehen. |
| M17 | Größe der drei Dateien | Je Datei Zeichen, Zeilen, geschätzte Tokens (aus dem Protokoll). |
| M18 | Stichprobe fachliche Richtigkeit | Mindestens 20 Zeilen je Phase, von einem Menschen gegen Code und Goldstandard geprüft: Zahl der richtigen Zeilen und Zahl der Zeilen mit fachlichem Fehler. |

Hinweise zur Messung:

- Für die Varianten B bis D liefert der Prüfbericht von `spec-check` die Zählwerte zu M2 bis M6, M9 und M11. Er
  prüft nur Form und Auffindbarkeit der Belege, nicht die fachliche Richtigkeit.
- Die fachliche Richtigkeit der Regeln (M6, soweit die Fundstelle die Aussage nicht trägt, M7, M8, M18) prüft ein
  Mensch an einer Stichprobe von mindestens 20 Zeilen je Phase. Wählen Sie die Stichprobe nach einem festen
  Verfahren, zum Beispiel jede n-te Zeile, und verwenden Sie in allen Varianten dasselbe Verfahren.
  Beschreibung des Verfahrens: `______________________`.
- Für Variante A zählen Sie M2 bis M5 und M9 bis M11 von Hand. Vermerken Sie das im Bewertungsbogen.
- Ein Ergebnis gilt als „abnahmefähig“ nach den Maßstäben, die Sie vor dem Test in Abschnitt 6 festlegen.

---

## 5 Bewertungsbogen

### 5.1 Bogen je Variante und Durchgang

Kopieren Sie die folgende Tabelle für jede der acht Kombinationen (A-1, A-2, B-1, B-2, C-1, C-2, D-1, D-2).
Bei Variante D tragen Sie die Werte je Phase ein, bei den anderen Varianten je Phase der gemeinsamen Einstellung.
Zeilen für nicht gewählte Phasen bleiben leer.

**Variante: ___ · Durchgang: ___ · Datum: ___ · Chat-Kennung oder Link: ___**

| Messgröße | Read | Process | Write | Bemerkung |
|---|---|---|---|---|
| M1 Anteil vollständig beschriebener Soll-Attribute | | | | |
| M2 Fehlende Schlüssel | | | | |
| M3 Doppelte Schlüssel | | | | |
| M4 Unbekannte Schlüssel | | | | |
| M5 Regeln ohne Beleg | | | | |
| M6 Regeln mit falschem Beleg | | | | |
| M7 Erfundene Regeln | | | | |
| M8 Nicht entscheidbare Regeln | | | | |
| M9 OPL-Punkte gesamt | | | | |
| M10 OPL-Punkte doppelt | | | | |
| M11 OPL-Punkte mit ungültigem Verweis | | | | |
| M12 Abbruch oder Kürzung (Ja/Nein, letzte Nr, markiert/unmarkiert) | | | | |
| M13 Datei oder Codeblock | | | | |
| M18 Stichprobe (richtig / geprüft) | | | | |

| Messgröße (gesamt für den Durchgang) | Wert | Bemerkung |
|---|---|---|
| M14 Prompts bis zum abnahmefähigen Ergebnis | | |
| M15 Anhänge je Prompt (höchstens 3) | | |
| M16 Minuten bis zum abnahmefähigen Ergebnis | | |
| M17 Größe Skill-Datei (Zeichen / Zeilen / Tokens) | | |
| M17 Größe Aufgabe-Datei (Zeichen / Zeilen / Tokens) | | |
| M17 Größe Code-Datei (Zeichen / Zeilen / Tokens) | | |
| M17 Summe der drei Dateien (Tokens) | | |
| Warnungen und Empfehlungen aus dem Protokoll | | |
| Zahl der Fortsetzungsprompts | | |
| Zahl der Nachbesserungsprompts | | |
| Abweichungen vom Protokoll (z. B. mehr als 3 Dateien, geänderte Einstellungen) | | |

### 5.2 Fehlerprotokoll

Tragen Sie jeden festgestellten Fehler einzeln ein, auch wenn `spec-check` ihn schon zählt. Fehlerarten:
fehlend, doppelt, unbekannt, ohne Beleg, falscher Beleg, erfunden, nicht entscheidbar, OPL doppelt, OPL-Verweis
ungültig, Auslassung, Abbruch, Sonstiges.

| Lfd. Nr | Variante / Durchgang | Phase | Nr und Schlüssel | Fehlerart | Beleg (Klasse.Methode oder Dateiname, Zitat aus dem Ergebnis) | Bemerkung |
|---|---|---|---|---|---|---|
| 1 | | | | | | |
| 2 | | | | | | |
| 3 | | | | | | |
| 4 | | | | | | |
| 5 | | | | | | |
| 6 | | | | | | |
| 7 | | | | | | |
| 8 | | | | | | |
| 9 | | | | | | |
| 10 | | | | | | |
| 11 | | | | | | |
| 12 | | | | | | |
| 13 | | | | | | |
| 14 | | | | | | |
| 15 | | | | | | |
| 16 | | | | | | |
| 17 | | | | | | |
| 18 | | | | | | |
| 19 | | | | | | |
| 20 | | | | | | |

Fügen Sie bei Bedarf weitere Zeilen hinzu.

### 5.3 Vergleichstabelle A bis D

Tragen Sie je Messgröße die Werte beider Durchgänge ein (Durchgang 1 / Durchgang 2) oder, wo sinnvoll, die Summe
über die Phasen. Legen Sie vorher fest, welche der beiden Darstellungen Sie verwenden, und verwenden Sie in allen
Spalten dieselbe: `______________________`.

| Messgröße | A | B | C | D |
|---|---|---|---|---|
| M1 Anteil vollständig beschriebener Soll-Attribute | | | | |
| M2 Fehlende Schlüssel | | | | |
| M3 Doppelte Schlüssel | | | | |
| M4 Unbekannte Schlüssel | | | | |
| M5 Regeln ohne Beleg | | | | |
| M6 Regeln mit falschem Beleg | | | | |
| M7 Erfundene Regeln | | | | |
| M8 Nicht entscheidbare Regeln | | | | |
| M9 OPL-Punkte gesamt | | | | |
| M10 OPL-Punkte doppelt | | | | |
| M11 OPL-Punkte mit ungültigem Verweis | | | | |
| M12 Abbruch oder Kürzung (markiert / unmarkiert) | | | | |
| M13 Datei oder Codeblock | | | | |
| M14 Prompts | | | | |
| M15 Anhänge je Prompt (Höchstwert) | | | | |
| M16 Minuten | | | | |
| M17 Summe der drei Dateien (Tokens) | | | | |
| M18 Stichprobe (richtig / geprüft) | | | | |
| Streuung zwischen Durchgang 1 und 2 (Bemerkung) | | | | |

---

## 6 Entscheidung

### 6.1 Schwellen vor dem Test festlegen

Legen Sie die Schwellen **vor dem ersten Lauf** fest und tragen Sie Datum und Namen ein. Sie werden danach nicht
mehr geändert.

| Schwelle | Wert |
|---|---|
| Maximal zulässige Zahl fehlender Schlüssel (absolut oder in Prozent) | |
| Maximal zulässige Zahl falscher Regeln (M6 + M7) | |
| Mindestanteil vollständig beschriebener Soll-Attribute (M1) | |
| Zulässige Zahl unmarkierter Abbrüche | |
| Höchstwert Prompts bis zum abnahmefähigen Ergebnis | |
| Höchstwert Minuten | |
| Mindestunterschied zwischen C und A, ab dem ein Unterschied als bedeutsam gilt | |
| Festgelegt am / von | |

### 6.2 Kriterien

Der Zielauftrag (Variante C) wird freigegeben, wenn alle folgenden Punkte erfüllt sind:

| Kriterium | Erfüllt? (ja/nein) | Begründung mit Verweis auf die Vergleichstabelle |
|---|---|---|
| C hat gegenüber A weniger fehlende Schlüssel (M2). | | |
| C hat gegenüber A weniger falsche Regeln (M6) und weniger erfundene Regeln (M7). | | |
| In C tritt kein unmarkierter Abbruch auf (M12). | | |
| Alle Prompts in C hatten höchstens 3 Anhänge (M15). | | |
| C erfüllt die Schwellen aus 6.1. | | |
| Beide Durchgänge von C zeigen dieselbe Tendenz (keine Schwankung, die das Ergebnis umkehrt). | | |

Die Varianten B und D dienen der Einordnung:

| Frage | Antwort |
|---|---|
| Wirkung der Texte allein: Ist B besser als A? In welchen Messgrößen? | |
| Wirkung von Filter, Limit und Tabellenangabe: Ist C besser als B? In welchen Messgrößen? | |
| Alternative aus 5.4 des Umsetzungsauftrags: Ist D besser als C? Rechtfertigt das den Mehraufwand an Prompts und Zeit? | |
| Funktionieren Fortsetzungs- und Nachbesserungsprompt im selben Chat (Zugriff auf hochgeladene Dateien)? | |
| Welche Dateien hat das priorisierte Weglassen entfernt, die fachlich nötig waren? | |

### 6.3 Ergebnis

| Angabe | Wert |
|---|---|
| Entscheidung (Freigabe / Freigabe mit Auflagen / keine Freigabe) | |
| Gewählte Variante für den Regelbetrieb | |
| Auflagen | |
| Rückfallweg bei Scheitern von Fortsetzungs- oder Nachbesserungsprompt (neuer Lauf mit einer Phase bzw. weniger Tabellen, Zusammenführung durch `spec-check`): angewendet? | |
| Datum / Entscheider | |

### 6.4 Kalibrierte Startwerte

Halten Sie fest, welche Startwerte nach dem Test gelten.

| Startwert | Wert im Auftrag (Annahme) | Wert nach dem Test | Begründung |
|---|---|---|---|
| `copilotLimit` (geschätzte Tokens, alle drei Dateien) | 30.000 | | |
| Anteil der Code-Datei am Limit | 80 % | | |
| Soll-Zeilen je Lauf, ab denen das Protokoll einen Lauf je Phase bzw. je Tabelle empfiehlt | 60 | | |
| Länge jedes Prompts (Zeichen) | höchstens 1.500 | | |
| Kompakt (Vorgabe) | an | | |
| Kommentare ignorieren (Vorgabe) | an | | |

Leiten Sie die Werte aus der Kalibrierung (Abschnitt 2) und aus den Läufen ab: Ab welcher Zahl von Soll-Zeilen
wurden Ergebnisse unvollständig? Ab welcher Dateigröße traten Lücken auf? Tragen Sie die Werte hier ein und
geben Sie sie an die Entwicklung zurück, damit die Vorgaben in der DevToolbox angepasst werden.

### 6.5 Offene Beobachtungen

| Beobachtung | Folgerung |
|---|---|
| | |
| | |
| | |
