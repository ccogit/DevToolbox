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
 */
public final class ProjectLayout {

    public static final String DESIGNER_SUFFIX = "-designer-services";
    public static final Path TARGET_MODEL = Path.of("doc", "migration", "properties");
    public static final List<Path> MAPPING_TEMPLATES = List.of(Path.of("resources", "documentation"),
            Path.of("src", "main", "resources", "documentation"));

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
        Path parent = root.toAbsolutePath().normalize().getParent();
        Path expected = parent.resolve(parent.getFileName() + DESIGNER_SUFFIX);
        if (Files.isDirectory(expected)) {
            return expected;
        }
        try (Stream<Path> list = Files.list(parent)) {
            List<Path> found = list.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(DESIGNER_SUFFIX)).toList();
            return found.size() == 1 ? found.getFirst() : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** {@code <designer>/doc/migration/properties}, or null without designer module. */
    public static Path targetModel(Path root) {
        Path module = designerModule(root);
        return module == null ? null : module.resolve(TARGET_MODEL);
    }

    /** {@code <designer>/resources/documentation} (or the first existing alternative), or null without designer module. */
    public static Path mappingTemplates(Path root) {
        Path module = designerModule(root);
        if (module == null) {
            return null;
        }
        return MAPPING_TEMPLATES.stream().map(module::resolve).filter(Files::isDirectory).findFirst()
                .orElse(module.resolve(MAPPING_TEMPLATES.getFirst()));
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
