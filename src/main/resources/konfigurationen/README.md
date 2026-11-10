# Gespeicherte Konfigurationen

„Konfiguration speichern…“ und „Konfiguration laden…“ öffnen diesen Ordner. Der vorgeschlagene Dateiname zeigt, was
die Konfiguration enthält:

```
<projekt>-<job>-<entität>-<phasen>[-kompakt].json
```

| Beispiel | Bedeutung |
|---|---|
| `vgb-spezifikation-risiko-alle-phasen.json` | Spezifikation der Entität Risiko aus dem Referenzprojekt von VGB, alle Phasen, ausführlich |
| `vgb-spezifikation-wagnis-read-kompakt.json` | dasselbe für Wagnis, nur Read, kompakt |
| `plg-implementierung-risiko-read-process.json` | Implementierung von Risiko in PLG, Phasen Read und Process |
| `ohne-projekt-…` | ohne ausgewähltes Migrationsprojekt (Pfade manuell) |

Eine Konfiguration lässt sich auch ohne Oberfläche ausführen:
`java -jar target/devtoolbox.jar src/main/resources/konfigurationen/<datei>.json`
