# Lösungsschlüssel – NICHT zu Copilot hochladen

| Datei | Größe | Abschnitte | Kennwort Abschnitt 1 | Kennwort Mitte | Kennwort 90 % | Schlusswort |
|---|---|---|---|---|---|---|
| `copilot-test-010k-tokens.md` | 40 KB | 44 | Tukan1 | Nr. 22: Quarz22 | Nr. 39: Igel39 | Nachtfalter-ENDE-857 |
| `copilot-test-050k-tokens.md` | 197 KB | 219 | Saphir1 | Nr. 109: Saphir109 | Nr. 197: Uhu197 | Bernstein-ENDE-952 |
| `copilot-test-100k-tokens.md` | 393 KB | 438 | Hafen1 | Nr. 219: Tukan219 | Nr. 394: Eisvogel394 | Bernstein-ENDE-681 |
| `copilot-test-250k-tokens.md` | 981 KB | 1092 | Igel1 | Nr. 546: Taiga546 | Nr. 982: Fjord982 | Kranich-ENDE-731 |
| `copilot-test-500k-tokens.md` | 1,960 KB | 2180 | Hafen1 | Nr. 1090: Bernstein1090 | Nr. 1962: Wanderfalke1962 | Feldlerche-ENDE-450 |

## So testest du

Lade **eine** Testdatei hoch (klein anfangen) und frage nacheinander:

1. „Wie lautet das Kennwort von Abschnitt 1?“ – beantwortet Copilot das, wurde die Datei angenommen.
2. „Wie lautet das Kennwort von Abschnitt <Nr. Mitte>?“ und „… von Abschnitt <Nr. 90 %>?“ – zeigt, wie weit Copilot tatsächlich liest.
3. „Wie lautet das Schlusswort am Ende der Datei?“ – nur wenn das stimmt, wurde die Datei vollständig verarbeitet.
4. „Wie viele Abschnitte enthält die Datei?“ – eine zu kleine Zahl verrät, wo Copilot abgeschnitten hat.

Achte auf erfundene Antworten: Stimmt ein Kennwort nicht mit der Tabelle überein, hat Copilot die Stelle nicht gelesen.
Die größte Datei, bei der alle vier Antworten stimmen, ergibt den Wert für **„Copilot-Limit (Tokens)“** in der
DevToolbox; der Parameter gilt für alle drei Dateien zusammen (mit etwas Sicherheitsabstand, z. B. 80 % davon).
