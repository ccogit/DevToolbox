# Entwicklung und Clean Code

DevToolbox ist eine **Java-25-Desktopanwendung** (Swing/FlatLaf) mit einem zusätzlichen
CLI-Einstieg. Für Änderungen gilt: fachliche Ergebnisse, Dateinamen, Fehlermeldungen
und bestehende Konfigurationsformate bleiben kompatibel, sofern eine Änderung
dies ausdrücklich dokumentiert und testet.

## Verantwortlichkeiten

- `de.devtoolbox.App` startet UI oder CLI-Jobs. `CommandLine` liest die Argumente.
- `core` enthält Konfiguration, Projektmodell, Job-Verträge und wiederverwendbare Infrastruktur.
- `jobs` orchestriert Abläufe und erzeugt die Ausgabeartefakte.
- `tools` enthält Dateianalyse, Parser und wiederverwendbare Fachoperationen.
- `ui` zeigt Daten an und reagiert auf Eingaben. Größere Datei-I/O gehört in
  eigene Komponenten und **nicht** auf den Swing Event Dispatch Thread.
- `ProjectOverview` erstellt die Projektinformationen ohne Swing-Abhängigkeit.

## Regeln für Änderungen

1. **Eine Verantwortung je Methode und Klasse.** Fachliche Verarbeitung von UI und
   Ein-/Ausgabe trennen; lange Abläufe in benannte, gut testbare Schritte aufteilen.
2. **Aussagekräftige Namen statt Kommentare für offensichtlichen Code.** Kommentare
   dokumentieren Regeln, Risiken, Invarianten oder Gründe.
3. **Wiederholung reduzieren, nicht Abstraktionen erzwingen.** Erst bei wiederholter
   identischer Logik einen gemeinsamen Helfer verwenden.
4. **Records und Standard-Java bevorzugen.** Keine neue Spring-Boot- oder Lombok-
   Abhängigkeit ohne konkreten, messbaren Vorteil.
5. **Fehler an Systemgrenzen explizit machen.** Ungültiges JSON, ZIP-Pfade,
   Projekt-IDs und Dateizugriffe nicht stillschweigend falsch interpretieren.
6. **Deterministische Ergebnisse.** Reihenfolgen von Suchtreffern, Ausgaben und
   generierten Dateien stabil halten, besonders bei parallelen Scans.
7. **Seiteneffekte kapseln.** Parser, Klassifikation und Validierung möglichst
   unabhängig von Dateizugriffen und UI gestalten.
8. **Regressionsschutz pro Refactoring.** Neue reine Helfer mit JUnit 5 prüfen;
   bestehende Fixture- und Integrationstests weiter ausführen.
9. **Sicherheitsinvarianten bewahren.** Quelldateien nicht überschreiben,
   unsichere Importpfade ablehnen und vorhandene Ergebnisse nie unbemerkt löschen.
10. **Kleine, überprüfbare PRs.** Strukturänderungen von fachlichen Verhaltens-
    änderungen trennen. Der PR benennt Auswirkungen, Tests und offene Risiken.

## Qualitätsprüfung

Für Codeänderungen im Hauptprojekt:

```bash
mvn -B -ntp verify
```

Die GitHub-Actions-Konfiguration `.github/workflows/app-tests.yml` führt denselben
Build unter JDK 25 aus. Fixture-Projekte haben einen separaten Workflow.

Nach UI-Änderungen zusätzlich manuell prüfen: Fenstergrößen, Projektauswahl,
Asynchronität, Formularfelder, aufklappbare Details und Log-Ausgabe.
