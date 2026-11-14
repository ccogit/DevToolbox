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
 * @param root           the project's own code as a local folder ({@code null} if not configured or given as a
 *                       Bitbucket address, see {@code rootUrl}); used when another project names this one as its
 *                       reference
 * @param reference      code of the reference project that is searched, as a local folder ({@code null} if not
 *                       configured or a Bitbucket address, see {@code referenceUrl})
 * @param referenceLabel how the reference was given: project label (e.g. "VGB – Wohngebäude"), the path or the
 *                       Bitbucket address; with a {@code null} reference and no address the id of a project that
 *                       was not found
 * @param ddl            schema description of this project: DDL file or folder ({@code null} if none)
 * @param targetModel    target model of this project: folder with one JSON file per entity ({@code null} if none);
 *                       without an entry derived from root ({@link ProjectLayout})
 * @param mappingTemplates folder with the mapping documentation templates, one per entity ({@code null} if none);
 *                       without an entry derived from root ({@link ProjectLayout})
 * @param entities       the stored extra business entities ("entitaeten") that are in no source (target model, DDL,
 *                       templates, reference code); may be empty. The full list is {@link #effectiveEntities()}
 * @param rootUrl        the Bitbucket address of the project's own code ("root" in projekt.json), or "" if it is a
 *                       local folder; cloned when a job runs
 * @param referenceUrl   the Bitbucket address of the reference – given directly as "referenz" or taken from the
 *                       referenced project's own code –, or "" if the reference is a local folder
 */
public record MigrationProject(String id, String name, String sparte, String produkt, Path root, Path reference,
                               String referenceLabel, Path ddl, Path targetModel, Path mappingTemplates,
                               String description, Path folder, List<String> entities, String rootUrl,
                               String referenceUrl) {

    /** With local code; a reference label that is a Bitbucket address is the reference's address. */
    public MigrationProject(String id, String name, String sparte, String produkt, Path root, Path reference,
                            String referenceLabel, Path ddl, Path targetModel, Path mappingTemplates,
                            String description, Path folder, List<String> entities) {
        this(id, name, sparte, produkt, root, reference, referenceLabel, ddl, targetModel, mappingTemplates,
                description, folder, entities, "",
                reference == null && CodeSource.isRemote(referenceLabel) ? referenceLabel : "");
    }

    /** Without a stored entity list. */
    public MigrationProject(String id, String name, String sparte, String produkt, Path root, Path reference,
                            String referenceLabel, Path ddl, Path targetModel, Path mappingTemplates,
                            String description, Path folder) {
        this(id, name, sparte, produkt, root, reference, referenceLabel, ddl, targetModel, mappingTemplates,
                description, folder, List.of());
    }

    /** Key of the entity list (comma separated) in projekt.json. */
    public static final String ENTITIES_KEY = "entitaeten";

    /**
     * The project's effective entities (target model, else DDL/mapping templates/reference code, plus the stored
     * extras). Reads files and may scan the reference code: call it off the EDT.
     */
    public EntitySuggestions.Effective effectiveEntities() {
        return EntitySuggestions.effective(ddl, targetModel, mappingTemplates, referenceCode(), entities);
    }

    /** Local code of this project: the folder, for a Bitbucket address the clone of an earlier run (else null). */
    public Path rootCode() {
        return root != null ? root : CodeSource.cached(rootUrl);
    }

    /** Local code of the reference: the folder, for a Bitbucket address the clone of an earlier run (else null). */
    public Path referenceCode() {
        return reference != null ? reference : CodeSource.cached(referenceUrl());
    }

    /** True if the job value {@code base} is this project's reference (folder or address). */
    public boolean isReference(String base) {
        if (base == null || base.isBlank()) {
            return false;
        }
        if (CodeSource.isRemote(base)) {
            return base.strip().equals(referenceUrl());
        }
        try {
            return reference != null && reference.equals(Path.of(base.strip()).toAbsolutePath().normalize());
        } catch (java.nio.file.InvalidPathException e) {
            return false;
        }
    }

    /** Choices for the entity drop-down: the names of {@link #effectiveEntities()}. */
    public List<String> entityChoices() {
        return effectiveEntities().names();
    }

    /** Splits a stored list (comma, semicolon or line separated) into distinct, trimmed names. */
    public static List<String> parseEntities(String text) {
        if (text == null) {
            return List.of();
        }
        return java.util.Arrays.stream(text.split("[,;\\r\\n]+")).map(String::strip).filter(t -> !t.isEmpty())
                .distinct().toList();
    }

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
        } else if (!referenceUrl().isEmpty()) {
            values.put(Keys.BASE, referenceUrl());         // … or its Bitbucket address, cloned by the job
        }
        if (root != null) {
            values.put(Keys.TARGET, root.toString());      // implementation: code of this project
        } else if (!rootUrl.isEmpty()) {
            values.put(Keys.TARGET, rootUrl);              // … or its Bitbucket address, cloned by the job
        }
        if (targetModel != null) {
            values.put(Keys.TARGET_MODEL, targetModel.toString());
        }
        if (mappingTemplates != null) {
            values.put(Keys.MAPPING_TEMPLATES, mappingTemplates.toString());
        }
        Path standards = standardsDirectory();
        if (standards != null && Files.isDirectory(standards)) {
            values.put(Keys.STANDARDS, standards.toString()); // implementation: effective standards
        }
        if (ddl != null) {
            values.put(Keys.DDL, ddl.toString());
        }
        return values;
    }

    /** Name of the cross-project basic data folder inside the projects folder (no projekt.json, no project id). */
    public static final String SHARED_BASISDATEN = "_basisdaten";
    /** Former name of the cross-project standards folder (now {@code _basisdaten/standards}); still used if present. */
    public static final String LEGACY_SHARED_STANDARDS = "_standards";
    /** File of the cross-project entity list inside {@link #SHARED_BASISDATEN}. */
    public static final String SHARED_ENTITIES_FILE = "entitaeten.json";

    /** The cross-project basic data folder {@code <projektordner>/_basisdaten}, may not exist; {@code null} without projects folder. */
    public static Path sharedBasisdatenDirectory() {
        Path dir = directory();
        return dir == null ? null : dir.resolve(SHARED_BASISDATEN);
    }

    /** The cross-project entities (stored in {@code _basisdaten/entitaeten.json}); empty if none or unreadable. */
    public static List<String> sharedEntities() {
        Path dir = sharedBasisdatenDirectory();
        Path file = dir == null ? null : dir.resolve(SHARED_ENTITIES_FILE);
        if (file == null || !Files.isRegularFile(file)) {
            return List.of();
        }
        try {
            return parseEntities(Json.parse(Files.readString(file)).get(ENTITIES_KEY));
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    /** Stores the cross-project entities as {@code {"entitaeten": [...]}} (distinct, trimmed). */
    public static void saveSharedEntities(List<String> entities) throws IOException {
        Path dir = sharedBasisdatenDirectory();
        if (dir == null) {
            throw new IOException("Es gibt keinen Ordner für Migrationsprojekte.");
        }
        Files.createDirectories(dir);
        Map<String, Object> json = new LinkedHashMap<>();
        json.put(ENTITIES_KEY, parseEntities(String.join("\n", entities)));
        Files.writeString(dir.resolve(SHARED_ENTITIES_FILE), Json.write(json), java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * The standards that apply: the project's own ones if it has them ({@link #hasOwnStandards()}), otherwise the
     * cross-project ones ({@code null} if there is no projects folder).
     */
    public Path standardsDirectory() {
        return hasOwnStandards() ? ownStandardsDirectory() : sharedStandardsDirectory();
    }

    /** The project's own standards (override of the cross-project ones), may not exist. */
    public Path ownStandardsDirectory() {
        return folder.resolve("standards");
    }

    /** Whether the project overrides the cross-project standards with its own (folder with STANDARDS.md). */
    public boolean hasOwnStandards() {
        return folder != null && Files.isRegularFile(ownStandardsDirectory().resolve("STANDARDS.md"));
    }

    /**
     * The cross-project standards {@code <projektordner>/_basisdaten/standards}; as long as only the former folder
     * {@code _standards} exists, that one. May not exist; {@code null} without projects folder.
     */
    public static Path sharedStandardsDirectory() {
        Path dir = directory();
        if (dir == null) {
            return null;
        }
        Path current = dir.resolve(SHARED_BASISDATEN).resolve("standards");
        Path legacy = dir.resolve(LEGACY_SHARED_STANDARDS);
        return !Files.isDirectory(current) && Files.isDirectory(legacy) ? legacy : current;
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

    /**
     * "referenz" is the id of another project (its code is used, folder or Bitbucket address), a path or a
     * Bitbucket address; "root" is a path or a Bitbucket address.
     */
    private static MigrationProject build(String id, Map<String, String> json, Path folder,
                                          Map<String, Map<String, String>> raw, Map<String, Path> folders) {
        String ref = json.getOrDefault("referenz", "").strip();
        Path reference;
        String referenceLabel;
        String referenceUrl = "";
        Map<String, String> other = raw.get(ref.toLowerCase());
        if (CodeSource.isRemote(ref)) {
            // a Bitbucket repository: no local path; the address is kept as label and cloned when a job runs
            reference = null;
            referenceLabel = ref;
            referenceUrl = ref;
        } else if (!ref.isEmpty() && other != null && !ref.equalsIgnoreCase(id)) {
            String otherCode = other.getOrDefault("root", "").strip();
            if (CodeSource.isRemote(otherCode)) {
                // the referenced project keeps its code in Bitbucket: its address is this project's reference
                reference = null;
                referenceUrl = otherCode;
            } else {
                Path otherRoot = path(folders.get(ref.toLowerCase()), otherCode);
                reference = otherRoot != null ? otherRoot
                        : ProjectLayout.root(other.getOrDefault("sparte", ""), ref.toLowerCase());
            }
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
        // the development folder, target model and templates from the designer-services module
        String configuredCode = json.getOrDefault("root", "").strip();
        String rootUrl = CodeSource.isRemote(configuredCode) ? configuredCode : "";
        Path configuredRoot = rootUrl.isEmpty() ? path(folder, configuredCode) : null;
        String configuredSparte = json.getOrDefault("sparte", "");
        Path root = !rootUrl.isEmpty() ? null
                : configuredRoot != null ? configuredRoot : ProjectLayout.root(configuredSparte, id);
        // a Bitbucket address: line, target model and templates follow the clone of an earlier run, if there is one
        Path code = rootUrl.isEmpty() ? root : CodeSource.cached(rootUrl);
        String sparte = configuredSparte.isBlank() ? ProjectLayout.sparte(code) : configuredSparte;
        Path designer = ProjectLayout.designer(code, sparte);
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
                folder,
                parseEntities(json.get(ENTITIES_KEY)),
                rootUrl,
                referenceUrl);
    }

    private static Path path(Path folder, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String input = value.strip();
        Path selected = Path.of(input);
        // Unix does not consider Windows drive/UNC paths absolute; never prepend the project folder.
        boolean windowsAbsolute = input.matches("(?i)^[a-z]:[/\\\\].*") || input.startsWith("\\\\");
        return (selected.isAbsolute() || windowsAbsolute ? selected : folder.resolve(selected)).normalize();
    }
}
