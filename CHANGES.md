# DevToolbox-Review – Änderungen vom 2026-11-11

**Basis:** main, Commit f4dccc0ef78bc9f00afd663bf07c992c951dfb2f  
**Branch:** review/devtoolbox-2026-11-11  
**Pull Request:** https://github.com/ccogit/DevToolbox/pull/1  
**Reviewbericht:** review/REVIEW-2026-11-11.md

Alle Änderungen liegen in vollständigen Dateien relativ zum Repo-Root. Das Overlay darf über die frische main-Kopie entpackt werden. Das Projekt bleibt im pom.xml auf JDK25 eingestellt; die CI führt zusätzlich die von Auftraggeber freigegebenen JDK21-Tests mit Override aus.

## Änderungen je Befund

| ID | Geänderte / neue Dateien | Kurzbeschreibung | Neue / betroffene Tests |
|---|---|---|---|
| R-01 | src/main/java/de/devtoolbox/core/OutputDirectory.java; src/main/java/de/devtoolbox/core/TaskJob.java; src/main/java/de/devtoolbox/jobs/implementation/ImplementationJob.java; src/main/java/de/devtoolbox/jobs/mappingdoc/MappingDocJob.java | Kollisionssichere getrennte Jobordner; keine Löschungen bei Wiederholung | src/test/java/de/devtoolbox/core/OutputDirectoryTest.java; src/test/java/de/devtoolbox/PhaseSpecJobTest.java |
| R-02 | src/main/java/de/devtoolbox/tools/standards/StandardsTool.java; src/main/java/de/devtoolbox/jobs/implementation/ImplementationJob.java | Pfade der Templates auf zulässige Vorlagen-Dateien eingrenzen, Traversal und Symlinks zurückweisen | src/test/java/de/devtoolbox/tools/standards/StandardsEditTest.java |
| R-03 | src/main/java/de/devtoolbox/core/OutputDirectory.java | Ausgaben innerhalb Referenz-/Migrationscode verweigern, realen Vorfahren bei Symlinks prüfen | src/test/java/de/devtoolbox/core/OutputDirectoryTest.java |
| R-04 | src/main/java/de/devtoolbox/tools/standards/StandardsTool.java | Keine ungefragte Überschreibung projektweiter Basis-Klassen | src/test/java/de/devtoolbox/tools/standards/StandardsEditTest.java |
| R-05 | src/main/java/de/devtoolbox/tools/mappingdoc/MappingTemplate.java; src/main/java/de/devtoolbox/tools/mappingdoc/MappingDocTool.java | Confluence-Vorlage nur für ausgewählte Entität vorbefüllen; Fremdzeilen und Zeilenenden erhalten | src/test/java/de/devtoolbox/tools/mappingdoc/MappingTemplateTest.java; src/test/java/de/devtoolbox/MappingDocJobTest.java |
| R-06 | src/main/java/de/devtoolbox/tools/mappingdoc/MappingJson.java; src/main/java/de/devtoolbox/tools/mappingdoc/MappingJsonPrefill.java | JSON-Properties nur direkt in passenden Attributobjekten ändern oder ergänzen | src/test/java/de/devtoolbox/tools/mappingdoc/MappingJsonPrefillTest.java |
| R-07 | src/main/java/de/devtoolbox/ui/MainWindow.java | I/O für Projektinfo in virtuellen Thread auslagern und veraltete Ergebnisse verwerfen | Bestehende Job-Tests, manuelle GUI-Prüfung offen |
| R-08 | src/main/java/de/devtoolbox/tools/codecollector/ProjectScanner.java; src/main/java/de/devtoolbox/tools/codecollector/CodeCollectorTool.java | Doppelte vollqualifizierte Klassen nicht zufällig auflösen, Warnhinweis ausgeben | src/test/java/de/devtoolbox/tools/codecollector/ProjectScannerTest.java |
| R-09 | src/main/java/de/devtoolbox/ui/StandardsDialog.java | Unter 800 px Inhaltsbreite rechte Basisdaten-Karte unter die Standards legen, vertikal scrollbar und Info-/Linkleiste getrennt | Acht GUI-Screenshots unter 1360×1000 / 980×640 über CI |\n| R-16 | README.md | Copilot-Limit und wiederholte Ablage präzise beschreiben | Bestehende Job-Testfälle |
| R-10 | src/main/java/de/devtoolbox/tools/codecollector/AttributeIndex.java | Tabelle-×-Datei-RegEx durch einmalig aufgebaute Identifier-Suche ersetzen | Bestehende DDL-/Jobtests, review/benchmark_cli.py |
| R-11 | src/main/java/de/devtoolbox/tools/mappingdoc/MappingAnalysis.java | Attribute in einem Scan pro Codezeile statt Regex je Attribut zuordnen | src/test/java/de/devtoolbox/tools/mappingdoc/MappingAnalysisTest.java |
| R-12 | src/main/java/de/devtoolbox/jobs/implementation/ImplementationJob.java | Projektweite Basisklassen nach Package plus Klassenname suchen; Lesefehler nicht ignorieren | src/test/java/de/devtoolbox/ImplementationJobTest.java |
| R-13 | src/main/java/de/devtoolbox/ui/Console.java | Log-EDT-Aufträge bündeln, Warteschlange und Dokument begrenzen | GUI-Dauerlasttest als Restprüfung im Bericht |
| R-14 | src/main/java/de/devtoolbox/core/MigrationProject.java | Windows-absolute Pfade auf Linux unverfälscht übernehmen | src/test/java/de/devtoolbox/core/ProjectLayoutTest.java |
| R-15 | src/main/java/de/devtoolbox/ui/MainWindow.java | Projekt- und Jobauswahl während Joblauf deaktivieren | Manueller Race-Test ausstehend |

**Weitere Dateien für die Abnahme:** .github/workflows/review-ci.yml (Java21/25-Matrix, Benchmark, Screenshots, Overlay-Selbstprüfung); review/benchmark_cli.py (synthetisches Testprojekt und CLI-Messung); review/UiScreenshot.java (Bildschirmaufnahme); review/REVIEW-2026-11-11.md (Befunde); CHANGES.md (dieser Nachweis).

## Zu löschende Dateien

**Keine.** Das ZIP enthält keine Löschanweisung; es müssen keine git-rm-Befehle ausgeführt werden.

## Tests

GitHub Actions „DevToolbox Review CI“ (Beispiel Run 43109774931, Java25/Java21):
- **134 JUnit-Tests bestanden; 0 Fehler, 0 fehlgeschlagene Tests, 0 übersprungen**, sowohl JDK21 als auch JDK25.
- **Maven package erfolgreich** unter beiden JDK-Versionen (bei JDK21 mit -Dmaven.compiler.release=21).
- Der Paketjob prüft zusätzlich das frisch über main gelegte Overlay mit **mvn clean test** und **mvn package** unter Java25.
- CLI-Ausführung unter Java25: je drei Upload-Dateien für phase-spec, implementation und mapping-doc.
- GUI-Screenshot-Aufnahme per Xvfb in separater CI-Stufe; Ergebnisse im CI-Artefakt, soweit erfolgreich.

## Performance vorher/nachher

**Messweg:** GitHub Actions Java25, /usr/bin/time, -Xmx512m, gemessener maximaler Prozess-RSS; reproduzierbares Skript review/benchmark_cli.py, Run https://github.com/ccogit/DevToolbox/actions/runs/43109774931.

| Szenario | main | Review | Ziel |
|---|---|---|---|
| phase-spec Testprojekt | 0,28 s / 66.128 KiB RSS | 0,25 s / 66.264 KiB | <2 s erfüllt |
| implementation Testprojekt | 0,15 s / 58.172 KiB RSS | 0,16 s / 58.280 KiB | <2 s erfüllt |
| mapping-doc Testprojekt | 0,22 s / 63.432 KiB RSS | 0,23 s / 64.336 KiB | <2 s erfüllt |
| phase-spec 5.000 Java-Dateien / 199.820.000 Byte / 500 DDL-Tabellen | **9,06 s / 230.884 KiB RSS** | **1,04 s / 230.636 KiB RSS** | <30 s, -Xmx512m erfüllt |

**Größte Verbesserung:** Das synthetische Großprojekt ist im hier protokollierten Messlauf um ca. **88,5 % schneller** (9,06 → 1,04 s); ein weiterer Benchmark-Run meldete 5,37 → 0,83 s (Run 43107944357). Upload-Dateigrößen siehe Reviewbericht. Einzelmessungen auf geteilter Hardware, keine statistische Signifikanzbehauptung.

## Übernahme

1. ZIP über das Root einer frischen Kopie von **main** entpacken.
2. Keine Dateien löschen.
3. Im Root **mvn clean test** und **mvn package** ausführen (JDK25; bei JDK21 maven.compiler.release=21 angeben).
4. ZIP darf nur mit erfolgreich abgeschlossenem CI-Paketjob als verifiziert betrachtet werden.

Der eigentliche PR bleibt separat prüf- und mergebar. **main wird durch das Erstellen dieses ZIPs oder des Pull Requests nicht verändert.**
