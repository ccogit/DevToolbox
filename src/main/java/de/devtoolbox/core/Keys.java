package de.devtoolbox.core;

/**
 * Keys of the configuration values shared by jobs, tools, migration projects, the CLI and the UI (JSON files,
 * {@code key=value} arguments, form fields). One place, so a key cannot drift apart between classes.
 */
public final class Keys {

    /** Code of the reference project that is searched. */
    public static final String BASE = "base";
    /** Business entity: name first, then synonyms as search terms. */
    public static final String TERMS = "terms";
    /** Selected batch phases (READ, PROCESS, WRITE). */
    public static final String PHASES = "phases";
    /** DDL of the legacy database (file or folder). */
    public static final String DDL = "ddl";
    /** Target model: folder with one JSON file per entity. */
    public static final String TARGET_MODEL = "targetModel";
    /** Mapping documentation templates: folder with one template per entity. */
    public static final String MAPPING_TEMPLATES = "mappingTemplates";
    /** Code of the migration project itself. */
    public static final String TARGET = "target";
    /** Stored standards of the migration project. */
    public static final String STANDARDS = "standards";
    /** Id of the selected migration project. */
    public static final String PROJECT = "project";
    /** Output folder. */
    public static final String OUTPUT = "output";
    /** Entity to implement. */
    public static final String ENTITY = "entity";
    /** Approved specification (PDF). */
    public static final String SPEC = "spec";
    /** Additional context for the AI. */
    public static final String CONTEXT = "context";
    /** Level of detail of the specification. */
    public static final String DETAIL = "detail";
    /** Hits only or hits plus referenced classes. */
    public static final String SCOPE = "scope";
    /** Drop imports and license headers. */
    public static final String COMPACT = "compact";
    /** Include src/test. */
    public static final String TESTS = "tests";
    /** Package/import lines are no hits. */
    public static final String IGNORE_DECLARATIONS = "ignoreDeclarations";
    /** Maximum tokens per code file (split at file boundaries). */
    public static final String MAX_TOKENS = "maxTokens";
    /** File extensions to search. */
    public static final String EXTENSIONS = "extensions";
    /** Folder names to exclude. */
    public static final String EXCLUDE = "exclude";

    /** Reserved: upload limit of Copilot (not used yet). */
    public static final String COPILOT_LIMIT = "copilotLimit";
    /** Reserved: DDL of the reference project (not used yet). */
    public static final String REFERENCE_DDL = "referenceDdl";

    private Keys() {
    }
}
