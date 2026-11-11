package de.devtoolbox.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The form values of each job, kept per migration project: {@code <projektordner>/<id>/konfigurationen/<job-id>.json}
 * (a job configuration file with its {@code job} and {@code project} entries, so the CLI can run it directly). Also the export of a whole project
 * (projekt.json, entity list, all job configurations) into one file and the import of such a file.
 */
public final class ProjectConfigs {

    /** Folder of the job configurations inside a project folder. */
    public static final String FOLDER = "konfigurationen";
    /** Marker entry of an export file. */
    public static final String FORMAT = "devtoolbox-projektkonfiguration";

    private static final String ID = "id";
    private static final String PROJECT_PREFIX = "projekt.";
    private static final String JOB_PREFIX = "job.";

    private ProjectConfigs() {
    }

    /** The file of {@code jobId} in the project, may not exist. */
    public static Path file(MigrationProject project, String jobId) {
        return project.folder().resolve(FOLDER).resolve(jobId + ".json");
    }

    /** The stored values of the job in the project; empty if there are none or the file is unreadable. */
    public static Map<String, String> load(MigrationProject project, String jobId) {
        if (project == null || project.folder() == null) {
            return Map.of();
        }
        Path file = file(project, jobId);
        try {
            return Files.isRegularFile(file) ? Json.parse(Files.readString(file, StandardCharsets.UTF_8)) : Map.of();
        } catch (IOException | RuntimeException e) {
            return Map.of();
        }
    }

    /**
     * Stores the values of the job in the project. Values that equal what the project itself provides
     * ({@link MigrationProject#values()}) are left out, so paths keep following the project's definition.
     */
    public static void save(MigrationProject project, String jobId, Map<String, Object> values) throws IOException {
        if (project == null || project.folder() == null || !Files.isRegularFile(project.folder().resolve("projekt.json"))) {
            return; // no project, or deleted in the meantime
        }
        Map<String, String> own = project.values();
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("job", jobId);               // so the CLI can run the file directly
        content.put(Keys.PROJECT, project.id());
        values.forEach((key, value) -> {
            if (!String.valueOf(value).equals(own.get(key))) {
                content.put(key, value);
            }
        });
        Path file = file(project, jobId);
        Files.createDirectories(file.getParent());
        Files.writeString(file, Json.write(content), StandardCharsets.UTF_8);
    }

    /** All stored job configurations of the project, by job id. */
    public static Map<String, Map<String, String>> loadAll(MigrationProject project) throws IOException {
        Map<String, Map<String, String>> all = new LinkedHashMap<>();
        Path dir = project.folder().resolve(FOLDER);
        if (!Files.isDirectory(dir)) {
            return all;
        }
        List<Path> files;
        try (Stream<Path> list = Files.list(dir)) {
            files = list.filter(f -> f.getFileName().toString().endsWith(".json")).sorted(Comparator.naturalOrder()).toList();
        }
        for (Path f : files) {
            String name = f.getFileName().toString();
            all.put(name.substring(0, name.length() - 5), Json.parse(Files.readString(f, StandardCharsets.UTF_8)));
        }
        return all;
    }

    /**
     * Writes one JSON file with the project definition ({@code projekt.<key>}, incl. the entity list) and all job
     * configurations ({@code job.<job-id>.<key>}).
     */
    public static void export(MigrationProject project, Path target) throws IOException {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("format", FORMAT);
        content.put(ID, project.id());
        Json.parse(Files.readString(project.folder().resolve("projekt.json"), StandardCharsets.UTF_8))
                .forEach((k, v) -> content.put(PROJECT_PREFIX + k, v));
        loadAll(project).forEach((job, values) -> values.forEach((k, v) -> content.put(JOB_PREFIX + job + "." + k, v)));
        Files.writeString(target, Json.write(content), StandardCharsets.UTF_8);
    }

    /** The project id named in an export file; throws if the file is no export. */
    public static String exportedId(Path file) throws IOException {
        Map<String, String> content = readExport(file);
        String id = content.getOrDefault(ID, "").strip();
        if (!id.matches("[A-Za-z0-9][A-Za-z0-9_-]*")) {
            throw new IllegalArgumentException("Die Datei enthält keine gültige Projekt-ID: „" + id + "“");
        }
        return id.toLowerCase();
    }

    /**
     * Writes the export into the project folder {@code <projektordner>/<id>}: projekt.json is replaced, the job
     * configurations of the file replace those of the same job (others stay).
     */
    public static void importInto(Path file, String id) throws IOException {
        Map<String, String> content = readExport(file);
        Path projects = MigrationProject.directory();
        if (projects == null) {
            throw new IOException("Es gibt keinen Ordner für Migrationsprojekte.");
        }
        if (!id.matches("[A-Za-z0-9][A-Za-z0-9_-]*")) {
            throw new IllegalArgumentException("Ungültige Projekt-ID: " + id);
        }
        Path folder = projects.resolve(id);
        Map<String, Object> project = new LinkedHashMap<>();
        Map<String, Map<String, Object>> jobs = new LinkedHashMap<>();
        content.forEach((key, value) -> {
            if (key.startsWith(PROJECT_PREFIX)) {
                project.put(key.substring(PROJECT_PREFIX.length()), value);
            } else if (key.startsWith(JOB_PREFIX)) {
                String rest = key.substring(JOB_PREFIX.length());
                int dot = rest.indexOf('.');
                if (dot > 0 && rest.substring(0, dot).matches("[A-Za-z0-9_-]+")) {
                    jobs.computeIfAbsent(rest.substring(0, dot), j -> new LinkedHashMap<>()).put(rest.substring(dot + 1), value);
                }
            }
        });
        if (project.isEmpty()) {
            throw new IllegalArgumentException("Die Datei enthält keine Projektdefinition (projekt.*).");
        }
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("projekt.json"), Json.write(project), StandardCharsets.UTF_8);
        for (var job : jobs.entrySet()) {
            if (job.getValue().containsKey(Keys.PROJECT)) {
                job.getValue().put(Keys.PROJECT, id); // the file now belongs to this project
            }
            Files.createDirectories(folder.resolve(FOLDER));
            Files.writeString(folder.resolve(FOLDER).resolve(job.getKey() + ".json"), Json.write(job.getValue()),
                    StandardCharsets.UTF_8);
        }
    }

    private static Map<String, String> readExport(Path file) throws IOException {
        Map<String, String> content;
        try {
            content = Json.parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Keine gültige JSON-Datei: " + e.getMessage());
        }
        if (!FORMAT.equals(content.get("format"))) {
            throw new IllegalArgumentException("Das ist keine exportierte Projektkonfiguration (Eintrag „format“ fehlt).");
        }
        return content;
    }
}
