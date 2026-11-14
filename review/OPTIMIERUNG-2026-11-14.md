# DevToolbox – Codevereinfachung (14.11.2026)

## Ausgangslage

Die Hauptanwendung ist eine Java-25-Desktopanwendung mit Swing/FlatLaf, Maven und JUnit 5.
Ausgangspunkt ist `main` auf Commit `67ac96146f3e3022aab16303f96b99f06e1a422e`.

## Refactoring

- `Ui.onDocumentChange` ersetzt drei wiederholte Swing-`DocumentListener`-Implementierungen.
  An den Aufrufstellen bleibt das Verhalten erhalten: direkte Filterung, verzögerte
  Benachrichtigung über Formularänderungen und Aktivierung der Speichern-Schaltfläche.
- `Ui.disclosure` vereinheitlicht die Auf-/Zuklapp-Logik für Job-Details und erweiterte
  Einstellungen; die Ansicht wird beim Umschalten neu layoutet.
- `JobView` verwendet die vorhandene `Ui.card`-Komponente für den Kopf der
  Job-Konfiguration statt den gleichen Aufbau nochmals zu erstellen.
- Die Änderungen reduzieren die vier Produktionsdateien um insgesamt **41 Zeilen**
  (57 hinzugefügte gegenüber 98 entfernten Zeilen).
- Neue Swing-Regressionstests prüfen Dokumentänderungen und das Auf-/Zuklappen
  einschließlich Beschriftung und Layout-Callback.
- Ein GitHub-Actions-Workflow führt `mvn -B -ntp verify` für das Hauptprojekt mit JDK 25
  bei Änderungen an `src/**`, `pom.xml` oder am Workflow aus.

## Bewertung von Spring Boot und Lombok

**Spring Boot derzeit nicht einführen.** DevToolbox ist eine lokale Desktop-Anwendung,
kein Webservice. Ein Anwendungscontainer würde eine zusätzliche Laufzeit- und
Konfigurationsschicht einführen, ohne den Swing-Aufbau oder die Job-Ausführung
erkennbar zu vereinfachen. Bei einem späteren Web-API-Bedarf neu bewerten.

**Lombok derzeit nicht einführen.** Für Datenobjekte verwendet die Anwendung
bereits Java-Records. Lombok würde eine Compiler-/Annotation-Processor-Abhängigkeit
sowie IDE-Konfiguration hinzufügen. Die verbleibenden Vereinfachungen betreffen
UI- und Ablaufcode, bei dem Lombok kaum Zeilen einsparen würde.

## Validierung und Hinweise

- Die Änderungen sind auf den separaten Branch `refactor/swing-simplification-20261114`
  begrenzt und sollen per Pull Request geprüft werden.
- In der lokalen Arbeitsumgebung stehen JDK 21, aber weder Maven noch JDK 25
  zur Verfügung; daher wurde hier kein vollständiger `mvn verify` durchgeführt.
  Der neue JDK-25-Workflow ist für diese Prüfung vorgesehen.
- Eine manuelle Swing-Überprüfung (Details/Erweiterte Optionen,
  DDL-Tabellenfilter und gespeicherte Entitäten) wird vor dem Merge empfohlen.
