package de.devtoolbox.core;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * An existing migration project for one product of an insurance line (e.g. VGB = Wohngebäude), whose code and
 * schema description the jobs analyse. Each project is a folder {@code migrationsprojekte/<id>/} with a
 * {@code projekt.json} and usually its schema description ({@code ddl/}). Paths in the file may be relative to
 * that folder. Selecting a project pre-fills the job params it provides (project root, DDL).
 *
 * @param root project root of the migration code ({@code null} if not configured)
 * @param ddl  schema description: DDL file or folder ({@code null} if none)
 */
public record MigrationProject(String id, String name, String sparte, String produkt, Path root, Path ddl,
                               String description, Path folder) {

    /** Key under which the selected project's id is kept in the configuration (JSON, CLI). */
    public static final String KEY = "project";

    private static final String FOLDER = "migrationsprojekte";
    private static final String FILE = "projekt.json";

    /** Display name, e.g. "VGB – Wohngebäude". */
    public String label() {
        return name + (produkt.isEmpty() ? "" : " – " + produkt);
    }

    /** The job params this project provides; only configured, non-empty values. */
    public Map<String, String> values() {
        Map<String, String> values = new LinkedHashMap<>();
        if (root != null) {
            values.put("base", root.toString());
        }
        if (ddl != null) {
            values.put("ddl", ddl.toString());
        }
        return values;
    }

    /** All projects found in the projects folder, sorted by name; empty if there is none. */
    public static List<MigrationProject> all() {
        Path dir = directory();
        if (dir == null) {
            return List.of();
        }
        List<MigrationProject> projects = new ArrayList<>();
        try (Stream<Path> folders = Files.list(dir)) {
            for (Path folder : folders.filter(Files::isDirectory).toList()) {
                Path file = folder.resolve(FILE);
                if (Files.isRegularFile(file)) {
                    projects.add(read(folder, Json.parse(Files.readString(file))));
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Migrationsprojekte nicht lesbar (" + dir + "): " + e.getMessage());
        }
        projects.sort(Comparator.comparing(MigrationProject::name, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(projects);
    }

    public static Optional<MigrationProject> find(String id) {
        return id == null || id.isBlank() ? Optional.empty()
                : all().stream().filter(p -> p.id().equalsIgnoreCase(id.strip())).findFirst();
    }

    /**
     * The projects folder: system property {@code devtoolbox.projekte}, otherwise {@code migrationsprojekte} in the
     * working directory, next to the jar or up to three levels above it (covers {@code target/} and IDE runs).
     */
    public static Path directory() {
        String configured = System.getProperty("devtoolbox.projekte");
        if (configured != null && !configured.isBlank()) {
            Path dir = Path.of(configured).toAbsolutePath().normalize();
            return Files.isDirectory(dir) ? dir : null;
        }
        List<Path> candidates = new ArrayList<>();
        candidates.add(Path.of("").toAbsolutePath());
        try {
            Path location = Path.of(MigrationProject.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            for (int i = 0; i < 4 && location != null; i++) {
                candidates.add(location);
                location = location.getParent();
            }
        } catch (URISyntaxException | SecurityException | NullPointerException e) {
            // working directory only
        }
        return candidates.stream().map(c -> c.resolve(FOLDER)).filter(Files::isDirectory)
                .map(p -> p.toAbsolutePath().normalize()).findFirst().orElse(null);
    }

    private static MigrationProject read(Path folder, Map<String, String> json) {
        String id = folder.getFileName().toString();
        return new MigrationProject(id,
                json.getOrDefault("name", id.toUpperCase()),
                json.getOrDefault("sparte", ""),
                json.getOrDefault("produkt", ""),
                path(folder, json.get("root")),
                path(folder, json.get("ddl")),
                json.getOrDefault("beschreibung", ""),
                folder);
    }

    private static Path path(Path folder, String value) {
        return value == null || value.isBlank() ? null : folder.resolve(value.strip()).normalize();
    }
}
