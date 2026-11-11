package de.devtoolbox.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The usual structure around a migration project: the migration ({@code root}) is a module of the parent project of
 * the insurance line (e.g. {@code firma-haftpflichtvertrag}, {@code firma-sachvertrag}). Next to it lies the module
 * {@code <parent>-designer-services} with the target model ({@code doc/migration/properties}) and the templates of
 * the mapping documentation ({@code resources/documentation}). Everything derived here is only a default – explicit
 * paths in the project or the job win.
 * <p>
 * All development projects lie in {@code C:/firma-develop}. So even without {@code root}, the line's parent project
 * follows from the insurance line, and in it the product's migration module (its name contains the project id,
 * e.g. "plg") and the designer-services module.
 */
public final class ProjectLayout {

    public static final String DESIGNER_SUFFIX = "-designer-services";
    public static final Path TARGET_MODEL = Path.of("doc", "migration", "properties");
    public static final List<Path> MAPPING_TEMPLATES = List.of(Path.of("resources", "documentation"),
            Path.of("src", "main", "resources", "documentation"));

    /** All development projects lie here; another place via the system property {@value #DEVELOP_PROPERTY}. */
    public static final Path DEVELOP_ROOT = Path.of("C:/firma-develop");
    public static final String DEVELOP_PROPERTY = "devtoolbox.develop";

    /** Jira project of the specification tickets per insurance line. */
    public static final String JIRA_LIABILITY = "HAFT";
    public static final String JIRA_PROPERTY = "SACH";

    private ProjectLayout() {
    }

    /**
     * The designer-services module next to {@code root}: {@code <parent>/<parent name>-designer-services}, otherwise
     * the only folder in the parent ending with {@code -designer-services}; null if there is none.
     */
    public static Path designerModule(Path root) {
        if (root == null || root.toAbsolutePath().normalize().getParent() == null) {
            return null;
        }
        return designerIn(root.toAbsolutePath().normalize().getParent());
    }

    /** The designer-services module in the parent project {@code parent}, or null. */
    public static Path designerIn(Path parent) {
        if (parent == null || !Files.isDirectory(parent)) {
            return null;
        }
        Path expected = parent.resolve(parent.getFileName() + DESIGNER_SUFFIX);
        if (Files.isDirectory(expected)) {
            return expected;
        }
        List<Path> found = folders(parent).stream()
                .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(DESIGNER_SUFFIX)).toList();
        return found.size() == 1 ? found.getFirst() : null;
    }

    /** {@code <designer>/doc/migration/properties}, or null without designer module. */
    public static Path targetModel(Path root) {
        return targetModelIn(designerModule(root));
    }

    /** {@code <designer>/resources/documentation} (or the first existing alternative), or null without designer module. */
    public static Path mappingTemplates(Path root) {
        return mappingTemplatesIn(designerModule(root));
    }

    public static Path targetModelIn(Path designer) {
        return designer == null ? null : designer.resolve(TARGET_MODEL);
    }

    public static Path mappingTemplatesIn(Path designer) {
        if (designer == null) {
            return null;
        }
        return MAPPING_TEMPLATES.stream().map(designer::resolve).filter(Files::isDirectory).findFirst()
                .orElse(designer.resolve(MAPPING_TEMPLATES.getFirst()));
    }

    /** {@code C:/firma-develop} or the folder of the system property {@value #DEVELOP_PROPERTY}. */
    public static Path developRoot() {
        String configured = System.getProperty(DEVELOP_PROPERTY);
        return configured == null || configured.isBlank() ? DEVELOP_ROOT : Path.of(configured.strip());
    }

    /**
     * The parent project of the insurance line in the development folder: {@code firma-<line>vertrag} or
     * {@code firma-<line>versicherung} (line = haftpflicht / sach), otherwise the only {@code firma-…<line>…} folder that
     * is no designer module; null if not found.
     */
    public static Path lineProject(String sparte) {
        String s = sparte == null ? "" : sparte.toLowerCase(Locale.ROOT);
        String line = s.contains("haftpflicht") ? "haftpflicht" : s.contains("sach") ? "sach" : "";
        Path develop = developRoot();
        if (line.isEmpty() || !Files.isDirectory(develop)) {
            return null;
        }
        for (String name : List.of("firma-" + line + "vertrag", "firma-" + line + "versicherung")) {
            if (Files.isDirectory(develop.resolve(name))) {
                return develop.resolve(name);
            }
        }
        List<Path> found = folders(develop).stream().filter(p -> {
            String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
            return n.startsWith("firma-") && n.contains(line) && !n.contains("-designer");
        }).toList();
        return found.size() == 1 ? found.getFirst() : null;
    }

    /**
     * The migration module of project {@code id} (e.g. "vgb") in the line's parent project: the only module with the
     * id as a part of its name; if none has it, the only module with "migration" in its name. Null if not unique.
     */
    public static Path migrationModule(Path lineProject, String id) {
        if (lineProject == null) {
            return null;
        }
        List<Path> modules = folders(lineProject).stream()
                .filter(p -> !p.getFileName().toString().toLowerCase(Locale.ROOT).contains("-designer")).toList();
        String key = id == null ? "" : id.strip().toLowerCase(Locale.ROOT);
        if (!key.isEmpty()) {
            List<Path> byId = modules.stream().filter(p -> List.of(
                    p.getFileName().toString().toLowerCase(Locale.ROOT).split("[^a-z0-9]+")).contains(key)).toList();
            if (!byId.isEmpty()) {
                return byId.size() == 1 ? byId.getFirst() : null;
            }
        }
        List<Path> migration = modules.stream()
                .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).contains("migration")).toList();
        return migration.size() == 1 ? migration.getFirst() : null;
    }

    /** The derived root of project {@code id} of the insurance line, or null. */
    public static Path root(String sparte, String id) {
        return migrationModule(lineProject(sparte), id);
    }

    /** Designer module: next to root, otherwise in the line's parent project. */
    public static Path designer(Path root, String sparte) {
        Path designer = designerModule(root);
        return designer != null ? designer : designerIn(lineProject(sparte));
    }

    private static List<Path> folders(Path dir) {
        try (Stream<Path> list = Files.list(dir)) {
            return list.filter(Files::isDirectory).sorted().toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** The derived paths as configuration values ({@link Keys#TARGET_MODEL}, {@link Keys#MAPPING_TEMPLATES}). */
    public static Map<String, String> derived(Path root) {
        Map<String, String> values = new LinkedHashMap<>();
        Path model = targetModel(root);
        if (model != null) {
            values.put(Keys.TARGET_MODEL, model.toString());
        }
        Path templates = mappingTemplates(root);
        if (templates != null) {
            values.put(Keys.MAPPING_TEMPLATES, templates.toString());
        }
        return values;
    }

    /** Insurance line from the parent project's name ("firma-haftpflichtvertrag" → Haftpflichtversicherung), or "". */
    public static String sparte(Path root) {
        if (root == null || root.toAbsolutePath().normalize().getParent() == null) {
            return "";
        }
        String parent = root.toAbsolutePath().normalize().getParent().getFileName().toString().toLowerCase(Locale.ROOT);
        return parent.contains("haftpflicht") ? "Haftpflichtversicherung"
                : parent.contains("sach") ? "Sachversicherung" : "";
    }

    /** HAFT for liability, SACH for property insurance; from the insurance line, else from the path; "" if unknown. */
    public static String jiraPrefix(String sparte, Path root) {
        String s = (sparte == null || sparte.isBlank() ? sparte(root) : sparte).toLowerCase(Locale.ROOT);
        return s.contains("haftpflicht") ? JIRA_LIABILITY : s.contains("sach") ? JIRA_PROPERTY : "";
    }

    /**
     * The ticket key: "HAFT-6554" stays (upper case), "6554" gets {@code prefix}. Fails with a message for the user if
     * the input is no ticket or the prefix is unknown.
     */
    public static String jiraIssue(String input, String prefix) {
        String t = input == null ? "" : input.strip().replaceAll("^\\{jiraissues:|}$", "").strip();
        if (t.isEmpty()) {
            throw new IllegalArgumentException("Das Jira-Ticket der Spezifikation fehlt (z. B. " + JIRA_LIABILITY
                    + "-6554 oder " + JIRA_PROPERTY + "-1234).");
        }
        if (t.matches("(?i)[A-Z][A-Z0-9]*-\\d+")) {
            return t.toUpperCase(Locale.ROOT);
        }
        if (t.matches("\\d+")) {
            if (prefix == null || prefix.isEmpty()) {
                throw new IllegalArgumentException("Jira-Ticket „" + t + "“ ohne Projekt – bitte vollständig angeben, "
                        + "z. B. " + JIRA_LIABILITY + "-" + t + " (Haftpflicht) oder " + JIRA_PROPERTY + "-" + t + " (Sach).");
            }
            return prefix + "-" + t;
        }
        throw new IllegalArgumentException("Ungültiges Jira-Ticket: „" + t + "“ (erwartet z. B. "
                + JIRA_LIABILITY + "-6554 oder 6554).");
    }
}
