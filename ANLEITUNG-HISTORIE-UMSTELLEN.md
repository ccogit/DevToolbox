# Anleitung: Rechner nach der Bereinigung der Historie umstellen

Die Historie auf GitHub wurde am 14.11.2026 neu geschrieben (firmeninterne Dateien und Angaben entfernt). Ein Rechner
mit einem älteren Klon bekommt bei `git pull` deshalb **Konflikte**. Zusammenführen ist hier falsch: Der Klon wird
einmalig auf den neuen Stand umgestellt. Alle Befehle im DevToolbox-Ordner ausführen (Git Bash oder PowerShell).

## Kurzfassung

```
git merge --abort            # nur falls ein Pull hängt (sonst: git rebase --abort)
git stash -u                 # eigene, nicht committete Änderungen sichern
git branch sicherung-alt     # alten Stand mit eigenen Commits sichern
                             # interne Dateien nach außerhalb kopieren (Schritt 2)
git fetch origin
git reset --hard origin/main
                             # interne Dateien zurückkopieren, Einstellungen eintragen
git stash pop                # eigene Änderungen zurückholen
```

## Schritt für Schritt

### 1. Hängenden Pull abbrechen

```
git merge --abort
```

Meldet Git, dass kein Merge läuft: `git rebase --abort`. Danach zeigt `git status` keine Konflikte mehr.

### 2. Alten Stand sichern

```
git stash -u
git branch sicherung-alt
```

Außerdem diese Dateien **in einen Ordner außerhalb des Repositorys kopieren** – Schritt 3 entfernt sie aus dem
Arbeitsordner, weil sie im neuen Stand nicht mehr versioniert sind:

- `migrationsprojekte/<projekt>/ddl/*.sql` (importierte Schemas)
- `copilot-testdateien/schemavgb.sql`, `copilot-testdateien/tabelle.sql`
- `copilot-testdateien/*Confluence*.pdf`
- `copilot-testdateien/result/`

### 3. Auf den neuen Stand umstellen

```
git fetch origin
git reset --hard origin/main
```

Der Klon ist jetzt identisch mit GitHub, ohne Konflikte.

### 4. Interne Dateien zurücklegen

Die gesicherten Dateien wieder an ihre alten Plätze kopieren. Sie stehen in `.gitignore` und erscheinen nicht mehr
als Änderung.

### 5. Einstellungen eintragen

DevToolbox starten → Seitenleiste **⚙ Einstellungen** → Entwicklungsordner, Präfix der Sparten-Projekte und die beiden
Jira-Projekte eintragen → **Speichern**. Gespeichert wird in `migrationsprojekte/umgebung.properties` (nur lokal).

Die Werte stehen **nicht** im Repository. Quellen:

- auf einem schon eingerichteten Rechner die Datei `migrationsprojekte/umgebung.properties` (einfach mitkopieren), oder
- die private Notiz außerhalb des Repositorys (siehe Ordner mit den gesicherten internen Dateien).

### 6. Nur bei eigenen, ungepushten Commits auf diesem Rechner

```
git log --oneline sicherung-alt -15
```

Commits nach „Fixed und URL“ stammen von diesem Rechner. Einzeln auf den neuen Stand holen:

```
git show <commit-id>         # vorher prüfen: keine internen Dateien, keine Firmenpfade/-schlüssel?
git cherry-pick <commit-id>
```

### 7. Eigene Änderungen zurückholen

```
git stash pop
```

Konflikte betreffen hier nur die eigenen Änderungen.

### 8. Aufräumen

Wenn alles passt:

```
git branch -D sicherung-alt
git stash list               # sollte leer sein
```

## Wenn etwas nicht klappt

Ausgabe von `git status` und `git log --oneline -5 sicherung-alt` sichern und weitergeben – daran lässt sich ablesen,
was zu tun ist. Solange `sicherung-alt` existiert, geht nichts verloren.
