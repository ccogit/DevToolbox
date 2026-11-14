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
 * the insurance line ({@code <präfix>haftpflichtvertrag}, {@code <präfix>sachvertrag}, e.g. {@code firma-sachvertrag}).
 * Next to it lies the module
 * {@code <parent>-designer-services} with the target model ({@code doc/migration/properties}) and the templates of
 * the mapping documentation ({@code resources/documentation}). Everything derived here is only a default – explicit
 * paths in the project or the job win.
 * <p>
 * All development projects lie in one development folder ({@link #developRoot()}). Folder and project prefix are
 * local settings ({@link #settingsFile()}, or the system properties {@value #DEVELOP_PROPERTY} and
 * {@value #PREFIX_PROPERTY}), so that no company-specific paths live in the code. So even without {@code root}, the line's parent project
 * follows from the insurance line, and in it the product's migration module (its name contains the project id,
 * e.g. "plg") and the designer-services module.
 */
public final class ProjectLayout {

    public static final String DESIGNER_SUFFIX = "-designer-services";
    public static final Path TARGET_MODEL = Path.of("doc", "migration", "properties");
    public static final List<Path> MAPPING_TEMPLATES = List.of(Path.of("resources", "documentation"),
            Path.of("src", "main", "resources", "documentation"));

    /** Default development folder if neither the system property nor the local settings name one. */
    public static final Path DEVELOP_ROOT = Path.of("C:/develop");
    public static final String DEVELOP_PROPERTY = "devtoolbox.develop";
    /** Prefix of the insurance lines' parent projects, e.g. "firma-" for firma-sachvertrag; default: none. */
    public static final String PREFIX_PROPERTY = "devtoolbox.prefix";
    /** Keys in the local settings file. */
    public static final String DEVELOP_SETTING = "entwicklungsordner";
    public static final String PREFIX_SETTING = "projektpraefix";

    /** Default Jira projects of the specification tickets per insurance line (real keys: local settings). */
    public static final String JIRA_LIABILITY = "HAFT";
    public static final String JIRA_PROPERTY = "SACH";
    public static final String JIRA_LIABILITY_PROPERTY = "devtoolbox.jira.haftpflicht";
    public static final String JIRA_PROPERTY_PROPERTY = "devtoolbox.jira.sach";
    public static final String JIRA_LIABILITY_SETTING = "jira_haftpflicht";
    public static final String JIRA_PROPERTY_SETTING = "jira_sach";

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

    /** Name of the local settings file. */
    public static final String SETTINGS_FILE = "umgebung.properties";

    /** The four local settings as stored ("" = not set, the default applies). */
    public record Settings(String developFolder, String prefix, String jiraLiability, String jiraProperty) {
    }

    /**
     * Where the local settings are stored: {@code <migrationsprojekte>/umgebung.properties} (next to the projects,
     * excluded from Git, writable wherever the toolbox runs); without projects folder {@link #profileSettingsFile()}.
     * Content, e.g.:
     * <pre>
     * entwicklungsordner=C:/firma-develop
     * projektpraefix=firma-
     * jira_haftpflicht=HAFT
     * jira_sach=SACH
     * </pre>
     */
    public static Path settingsFile() {
        Path projects = MigrationProject.directory();
        return projects != null ? projects.resolve(SETTINGS_FILE) : profileSettingsFile();
    }

    /** Fallback in the user profile: {@code <user.home>/.devtoolbox/umgebung.properties} (read only if present). */
    public static Path profileSettingsFile() {
        return Path.of(System.getProperty("user.home"), ".devtoolbox", SETTINGS_FILE);
    }

    /** The entries of the profile file, overridden by those of {@link #settingsFile()}. */
    private static java.util.Properties loadSettings() {
        java.util.Properties props = new java.util.Properties();
        for (Path file : new java.util.LinkedHashSet<>(List.of(profileSettingsFile(), settingsFile()))) {
            if (!Files.isRegularFile(file)) {
                continue;
            }
            java.util.Properties one = new java.util.Properties();
            try (var reader = Files.newBufferedReader(file, java.nio.charset.StandardCharsets.UTF_8)) {
                one.load(reader);
                props.putAll(one);
            } catch (IOException | IllegalArgumentException e) {
                // unreadable: ignored, the defaults apply
            }
        }
        return props;
    }

    /** The system property, else the entry of the local settings, else null. */
    private static String setting(String property, String key) {
        String value = System.getProperty(property);
        if (value != null) {
            return value.strip();
        }
        String entry = loadSettings().getProperty(key);
        return entry == null ? null : entry.strip();
    }

    /** The stored local settings (without system properties); "" where nothing is stored. */
    public static Settings storedSettings() {
        java.util.Properties props = loadSettings();
        return new Settings(props.getProperty(DEVELOP_SETTING, "").strip(), props.getProperty(PREFIX_SETTING, "").strip(),
                props.getProperty(JIRA_LIABILITY_SETTING, "").strip(), props.getProperty(JIRA_PROPERTY_SETTING, "").strip());
    }

    /** System properties that currently override the stored settings (e.g. "-Ddevtoolbox.prefix"); empty if none. */
    public static List<String> overridingProperties() {
        return Stream.of(DEVELOP_PROPERTY, PREFIX_PROPERTY, JIRA_LIABILITY_PROPERTY, JIRA_PROPERTY_PROPERTY)
                .filter(p -> System.getProperty(p) != null).map(p -> "-D" + p).toList();
    }

    /**
     * Writes the settings to {@link #settingsFile()}; an empty value means the default applies. Backslashes in the
     * folder become slashes, so the file stays readable as properties.
     *
     * @return the written file
     */
    public static Path saveSettings(Settings settings) throws IOException {
        Path file = settingsFile();
        StringBuilder text = new StringBuilder("# Lokale DevToolbox-Einstellungen – nicht im Repository (siehe .gitignore)\n");
        append(text, DEVELOP_SETTING, settings.developFolder().strip().replace('\\', '/'));
        append(text, PREFIX_SETTING, settings.prefix().strip());
        append(text, JIRA_LIABILITY_SETTING, settings.jiraLiability().strip().toUpperCase(Locale.ROOT));
        append(text, JIRA_PROPERTY_SETTING, settings.jiraProperty().strip().toUpperCase(Locale.ROOT));
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, java.nio.charset.StandardCharsets.UTF_8);
        return file;
    }

    /** Empty values are written too, so they also override an entry of the profile file. */
    private static void append(StringBuilder text, String key, String value) {
        text.append(key).append('=').append(value.replace("\\", "\\\\")).append('\n');
    }

    /** The development folder: system property {@value #DEVELOP_PROPERTY}, local settings, else {@code C:/develop}. */
    public static Path developRoot() {
        String configured = setting(DEVELOP_PROPERTY, DEVELOP_SETTING);
        return configured == null || configured.isBlank() ? DEVELOP_ROOT : Path.of(configured);
    }

    /** Jira project for liability insurance: system property, local settings, else {@value #JIRA_LIABILITY}. */
    public static String jiraLiability() {
        String configured = setting(JIRA_LIABILITY_PROPERTY, JIRA_LIABILITY_SETTING);
        return configured == null || configured.isBlank() ? JIRA_LIABILITY : configured.toUpperCase(Locale.ROOT);
    }

    /** Jira project for property insurance: system property, local settings, else {@value #JIRA_PROPERTY}. */
    public static String jiraProperty() {
        String configured = setting(JIRA_PROPERTY_PROPERTY, JIRA_PROPERTY_SETTING);
        return configured == null || configured.isBlank() ? JIRA_PROPERTY : configured.toUpperCase(Locale.ROOT);
    }

    /** The prefix of the lines' parent projects (lower case): system property, local settings, else "". */
    public static String projectPrefix() {
        String configured = setting(PREFIX_PROPERTY, PREFIX_SETTING);
        return configured == null ? "" : configured.toLowerCase(Locale.ROOT);
    }

    /**
     * The parent project of the insurance line in the development folder: {@code <präfix>haftpflichtvertrag} or
     * {@code <präfix>sachvertrag}; if it is missing, the only {@code <präfix>…<line>…} folder that
     * is no designer module; null if not found.
     */
    public static Path lineProject(String sparte) {
        String s = sparte == null ? "" : sparte.toLowerCase(Locale.ROOT);
        String line = s.contains("haftpflicht") ? "haftpflicht" : s.contains("sach") ? "sach" : "";
        Path develop = developRoot();
        if (line.isEmpty() || !Files.isDirectory(develop)) {
            return null;
        }
        String prefix = projectPrefix();
        Path expected = develop.resolve(prefix + line + "vertrag");
        if (Files.isDirectory(expected)) {
            return expected;
        }
        List<Path> found = folders(develop).stream().filter(p -> {
            String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
            return n.startsWith(prefix) && n.contains(line) && !n.contains("-designer");
        }).toList();
        return found.size() == 1 ? found.getFirst() : null;
    }

    /**
     * The migration module of project {@code id} in the line's parent project. Module names are not uniform
     * ({@code firma-sachvertrag-vgb-migration-batch}, {@code firma-haftpflichtvertrag-plg-migration}, and the general
     * {@code firma-sachvertrag-migration} for Hausrat), so in this order:
     * <ol>
     *   <li>the module with the id as a part of its name ("vgb", "plg"; with several, the one with "migration");</li>
     *   <li>the general module {@code <parent>-migration};</li>
     *   <li>the only module with "migration" in its name.</li>
     * </ol>
     * Null if not unique.
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
            if (byId.size() > 1) {
                byId = byId.stream().filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).contains("migration"))
                        .toList();
            }
            if (!byId.isEmpty()) {
                return byId.size() == 1 ? byId.getFirst() : null;
            }
        }
        Path general = lineProject.resolve(lineProject.getFileName() + "-migration");
        if (Files.isDirectory(general)) {
            return general;
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

    /** The Jira project of the insurance line ({@link #jiraLiability}, {@link #jiraProperty}), else from the path; "" if unknown. */
    public static String jiraPrefix(String sparte, Path root) {
        String s = (sparte == null || sparte.isBlank() ? sparte(root) : sparte).toLowerCase(Locale.ROOT);
        return s.contains("haftpflicht") ? jiraLiability() : s.contains("sach") ? jiraProperty() : "";
    }

    /**
     * The ticket key: "HAFT-6554" stays (upper case), "6554" gets {@code prefix}. Fails with a message for the user if
     * the input is no ticket or the prefix is unknown.
     */
    public static String jiraIssue(String input, String prefix) {
        String t = input == null ? "" : input.strip().replaceAll("^\\{jiraissues:|}$", "").strip();
        if (t.isEmpty()) {
            throw new IllegalArgumentException("Das Jira-Ticket der Spezifikation fehlt (z. B. " + jiraLiability()
                    + "-6554 oder " + jiraProperty() + "-1234).");
        }
        if (t.matches("(?i)[A-Z][A-Z0-9]*-\\d+")) {
            return t.toUpperCase(Locale.ROOT);
        }
        if (t.matches("\\d+")) {
            if (prefix == null || prefix.isEmpty()) {
                throw new IllegalArgumentException("Jira-Ticket „" + t + "“ ohne Projekt – bitte vollständig angeben, "
                        + "z. B. " + jiraLiability() + "-" + t + " (Haftpflicht) oder " + jiraProperty() + "-" + t + " (Sach).");
            }
            return prefix + "-" + t;
        }
        throw new IllegalArgumentException("Ungültiges Jira-Ticket: „" + t + "“ (erwartet z. B. "
                + jiraLiability() + "-6554 oder 6554).");
    }
}
