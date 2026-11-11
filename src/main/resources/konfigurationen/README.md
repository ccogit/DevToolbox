# Konfigurationen

Die Konfigurationen der Jobs liegen je Migrationsprojekt in `migrationsprojekte/<id>/konfigurationen/<job-id>.json`
und werden von der Oberfläche automatisch gespeichert und geladen. Eine Konfiguration lässt sich auch ohne Oberfläche
ausführen (Job und Projekt stehen in der Datei; Pfade, die das Projekt selbst liefert, kommen aus der `projekt.json`):

`java -jar target/devtoolbox.jar migrationsprojekte/<id>/konfigurationen/<job-id>.json`
