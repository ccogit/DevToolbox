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
 * A migration project for one product of an insurance line (e.g. PLG, VGB, VHV). Jobs create specifications
 * <em>for</em> this project by analysing the code of its <em>reference project</em> – an already implemented,
 * very similar migration. Each project is a folder {@code migrationsprojekte/<id>/} with a {@code projekt.json}
 * and usually its schema description ({@code ddl/}). Paths in the file may be relative to that folder.
 *
 * @param root           the project's own code ({@code null} if not configured); used when another project
 *                       names this one as its reference
 * @param reference      code of the reference project that is searched ({@code null} if not configured)
 * @param referenceLabel how the reference was given: project label (e.g. "VGB – Wohngebäude") or the path; with a
 *                       {@code null} reference the id of a project that was not found
 * @param ddl            schema description of this project: DDL file or folder ({@code null} if none)
 * @param targetModel    target model of this project: folder with one JSON file per entity ({@code null} if none);
 *                       without an entry derived from root ({@link ProjectLayout})
 * @param mappingTemplates folder with the mapping documentation templates, one per entity ({@code null} if none);
 *                       without an entry derived from root ({@link ProjectLayout})
 */
public record MigrationProject(String id, String name, String sparte, String produkt, Path root, Path reference,
                               String referenceLabel, Path ddl, Path targetModel, Path mappingTemplates,
                               String description, Path folder) {

    /** Key under which the selected project's id is kept in the configuration (JSON, CLI). */
    public static final String KEY = Keys.PROJECT;

    private static final String FOLDER = "migrationsprojekte";
    private static final String FILE = "projekt.json";

    /** Display name, e.g. "PLG – Privat, Landwirtschaft, Gewerbe". */
    public String label() {
        return name + (produkt.isEmpty() ? "" : " – " + produkt);
    }

    /** The job params this project provides; only configured values. */
    public Map<String, String> values() {
        Map<String, String> values = new LinkedHashMap<>();
        if (reference != null) {
            values.put(Keys.BASE, reference.toString());   // specification: code of the reference project
        }
        if (root != null) {
            values.put(Keys.TARGET, root.toString());      // implementation: code of this project
        }
        if (targetModel != null) {
            values.put(Keys.TARGET_MODEL, targetModel.toString());
        }
        if (mappingTemplates != null) {
            values.put(Keys.MAPPING_TEMPLATES, mappingTemplates.toString());
        }
        if (folder != null && Files.isDirectory(standardsDirectory())) {
            values.put(Keys.STANDARDS, standardsDirectory().toString()); // implementation: stored standards
        }
        if (ddl != null) {
            values.put(Keys.DDL, ddl.toString());
        }
        return values;
    }

    /** The project's standards (templates for the implementation), created with "Standards erzeugen…". */
    public Path standardsDirectory() {
        return folder.resolve("standards");
    }

    /**
     * Folder that receives imported schema files (DDL): the configured {@code ddl} folder; if {@code ddl} names a
     * single file, its folder; if nothing is configured, {@code <projektordner>/ddl}.
     */
    public Path schemaDirectory() {
        if (ddl == null) {
            return folder.resolve("ddl");
        }
        String name = ddl.getFileName().toString().toLowerCase();
        return name.endsWith(".sql") || name.endsWith(".ddl") ? ddl.getParent() : ddl;
    }

    /** Points "ddl" in projekt.json at {@code dir} (relative to the project folder) unless it already does. */
    public void ensureDdlEntry(Path dir) throws IOException {
        if (dir.equals(ddl)) {
            return;
        }
        Path file = folder.resolve(FILE);
        Map<String, Object> json = new LinkedHashMap<>(Json.parse(Files.readString(file)));
        json.put("ddl", folder.relativize(dir).toString().replace('\\', '/'));
        Files.writeString(file, Json.write(json), java.nio.charset.StandardCharsets.UTF_8);
    }

    /** All projects found in the projects folder, sorted by name; empty if there is none. */
    public static List<MigrationProject> all() {
        Path dir = directory();
        if (dir == null) {
            return List.of();
        }
        Map<String, Map<String, String>> raw = new LinkedHashMap<>();
        Map<String, Path> folders = new LinkedHashMap<>();
        try (Stream<Path> list = Files.list(dir)) {
            for (Path folder : list.filter(Files::isDirectory).toList()) {
                Path file = folder.resolve(FILE);
                if (Files.isRegularFile(file)) {
                    String id = folder.getFileName().toString().toLowerCase();
                    raw.put(id, Json.parse(Files.readString(file)));
                    folders.put(id, folder);
                }
            }
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("Migrationsprojekte nicht lesbar (" + dir + "): " + e.getMessage());
        }
        List<MigrationProject> projects = new ArrayList<>();
        raw.forEach((id, json) -> projects.add(build(id, json, folders.get(id), raw, folders)));
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

    /** "referenz" is either the id of another project (its root is used) or a path. */
    private static MigrationProject build(String id, Map<String, String> json, Path folder,
                                          Map<String, Map<String, String>> raw, Map<String, Path> folders) {
        String ref = json.getOrDefault("referenz", "").strip();
        Path reference;
        String referenceLabel;
        Map<String, String> other = raw.get(ref.toLowerCase());
        if (!ref.isEmpty() && other != null && !ref.equalsIgnoreCase(id)) {
            Path otherFolder = folders.get(ref.toLowerCase());
            Path otherRoot = path(otherFolder, other.get("root"));
            reference = otherRoot != null ? otherRoot : ProjectLayout.root(other.getOrDefault("sparte", ""), ref.toLowerCase());
            String produkt = other.getOrDefault("produkt", "");
            referenceLabel = other.getOrDefault("name", ref.toUpperCase()) + (produkt.isEmpty() ? "" : " – " + produkt);
        } else if (ref.matches("[A-Za-z0-9_-]+") && !Files.exists(folder.resolve(ref))) {
            // looks like the id of a project that does not exist (any more), not like a path
            reference = null;
            referenceLabel = ref;
        } else {
            reference = path(folder, ref);
            referenceLabel = reference == null ? "" : reference.toString();
        }
        // not configured: derived from the usual layout (ProjectLayout) – root from the line's parent project in
        // C:/firma-develop, target model and templates from the designer-services module
        Path configuredRoot = path(folder, json.get("root"));
        String configuredSparte = json.getOrDefault("sparte", "");
        Path root = configuredRoot != null ? configuredRoot : ProjectLayout.root(configuredSparte, id);
        String sparte = configuredSparte.isBlank() ? ProjectLayout.sparte(root) : configuredSparte;
        Path designer = ProjectLayout.designer(root, sparte);
        Path targetModel = path(folder, json.get("zielmodell"));
        Path mappingTemplates = path(folder, json.get("mappingvorlagen"));
        return new MigrationProject(id,
                json.getOrDefault("name", id.toUpperCase()),
                sparte,
                json.getOrDefault("produkt", ""),
                root,
                reference,
                referenceLabel,
                path(folder, json.get("ddl")),
                targetModel != null ? targetModel : ProjectLayout.targetModelIn(designer),
                mappingTemplates != null ? mappingTemplates : ProjectLayout.mappingTemplatesIn(designer),
                json.getOrDefault("beschreibung", ""),
                folder);
    }

    private static Path path(Path folder, String value) {
        return value == null || value.isBlank() ? null : folder.resolve(value.strip()).normalize();
    }
}
