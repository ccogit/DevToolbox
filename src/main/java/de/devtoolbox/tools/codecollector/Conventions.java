package de.devtoolbox.tools.codecollector;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The naming and folder conventions of the migration projects, in one place. Both the phase assignment of the code
 * collector ({@link PhaseClassifier}) and the artefact detection of the standards are driven by these rules, so a
 * new convention (a class suffix, a folder name, a prefix) is added here only.
 */
public final class Conventions {

    // ---- fixed rules with the highest priority ----

    /**
     * Classes {@code …Repository} belong to the Read phase by default (usually a repository reads the data), but
     * not always: a fixed directory, an implemented Spring Batch type or the package can place one elsewhere.
     */
    public static final String REPOSITORY_SUFFIX = "Repository";

    /** "Anreicherung" and "Anreicherer" are key terms of the Read phase: in a class name and in a directory name. */
    static final Pattern ENRICHMENT_NAME = Pattern.compile("(?i).*anreicher(?:ung|er).*");

    /**
     * Classes in these directories always belong to the given phase, whatever they implement or how they are named.
     * Case-insensitive; the innermost matching directory wins. A directory whose name contains "anreicherung" or
     * "anreicherer" counts like these two.
     */
    public static final Map<String, Phase> FIXED_DIRECTORIES = Map.of(
            "anreicherer", Phase.READ,
            "anreicherung", Phase.READ,
            "mapper", Phase.PROCESS,
            "mapping", Phase.PROCESS,
            "write", Phase.WRITE,
            "writer", Phase.WRITE);

    // ---- Java content: implemented Spring Batch types and annotations ----

    static final Pattern READ_TYPE = declares("\\w*ItemReader|ItemStreamReader|RowMapper|ResultSetExtractor");
    static final Pattern PROCESS_TYPE = declares("\\w*ItemProcessor");
    static final Pattern WRITE_TYPE = declares("\\w*ItemWriter");
    static final Pattern CONFIG_CONTENT =
            Pattern.compile("@Configuration|@EnableBatchProcessing|\\bJobBuilder\\b|\\bStepBuilder\\b|\\bimplements\\s+Tasklet\\b");
    static final Pattern MODEL_CONTENT =
            Pattern.compile("@Entity|@Embeddable|@Table\\b|@Xml(?:RootElement|Type)\\b|@Data\\b|\\brecord\\s+\\w+\\s*[(<]");

    // ---- class name suffixes per phase ----

    static final Pattern READ_NAME = suffixes("Reader|RowMapper|Extractor");
    static final Pattern PROCESS_NAME = suffixes("Processor|Mapper|Mapping|Converter|Konverter|Transformer|Translator"
            + "|Zuordnung|Umsetzung|Ermittlung|Berechnung|Rechner|Calculator");
    static final Pattern WRITE_NAME = suffixes("Writer|Exporter|Persister");
    static final Pattern PERSISTENCE_NAME = suffixes("Repo|Dao|DAO");
    static final Pattern CONFIG_NAME = suffixes("Config|Configuration|Job|Step|Tasklet|Listener");

    /**
     * Data structures (TO, PO, DTO, entity) that define an entity, in the order they are checked ("RisikoDTO" is a
     * DTO, not a TO). The suffix is removed to get the entity's stem; an {@code @Entity} annotation also makes an
     * entity.
     */
    public enum DataKind {
        DTO("Dto|DTO"), TO("TO|To"), PO("PO|Po"), ENTITY("Entity");

        final String suffixes;
        private final Pattern namePattern;
        private final Pattern suffix;

        DataKind(String suffixes) {
            this.suffixes = suffixes;
            this.namePattern = Pattern.compile("\\w+(?:" + suffixes + ")");
            this.suffix = Pattern.compile("(?:" + suffixes + ")$");
        }
    }

    /** A data structure and the entity it belongs to, e.g. TO "Vertrag" for {@code VertragTO}. */
    public record DataStructure(DataKind kind, String stem) {
    }

    /** Further model suffixes that do not define an entity of their own (business objects, value objects …). */
    private static final String OTHER_MODEL_SUFFIXES = "BO|Bo|Vo|VO|Model|Bean";
    static final Pattern MODEL_NAME = suffixes(String.join("|",
            List.of(DataKind.values()).stream().map(k -> k.suffixes).toList()) + "|" + OTHER_MODEL_SUFFIXES);

    /** Logic classes: the entity's stem followed by the role, e.g. "VertragItemReader" → "Vertrag". */
    private static final Pattern LOGIC_NAME = Pattern.compile("(\\w+?)(ItemReader|Reader|RowMapper|Anreicherer|Anreicherung"
            + "|Leser|Loader|ItemProcessor|Processor|Mapper|Mapping|Converter|Konverter|Zuordnung|Umsetzung"
            + "|ItemWriter|Writer|Exporter|Repository|Dao|DAO)$");

    /** Prefixes distinguishing old and new variants of the same entity ("AltVertragTO", "NeuVertragTO"). */
    private static final Pattern VARIANT_PREFIX = Pattern.compile("^(?:Alt|Neu|Old|New)(?=[A-Z])");

    // ---- folder (package) names as a fallback ----

    static final Pattern READ_DIR = Pattern.compile(".*/(?:read|reader|readers|einlesen|lesen)/.*");
    static final Pattern PROCESS_DIR = Pattern.compile(".*/(?:process|processor|processors|transform|verarbeitung)/.*");
    static final Pattern WRITE_DIR = Pattern.compile(".*/(?:writers|schreiben|export)/.*");

    private Conventions() {
    }

    /** A class named {@code …Repository}; Read is its default phase, not a fixed one. */
    public static boolean isRepository(String className) {
        return className.endsWith(REPOSITORY_SUFFIX);
    }

    /** Phase fixed by the innermost {@linkplain #FIXED_DIRECTORIES fixed directory} of {@code rel}, or null. */
    public static Phase fixedByDirectory(String rel) {
        String[] segments = rel.toLowerCase(Locale.ROOT).split("/");
        for (int i = segments.length - 2; i >= 0; i--) { // last segment is the file name
            Phase phase = FIXED_DIRECTORIES.get(segments[i]);
            if (phase != null) {
                return phase;
            }
            if (ENRICHMENT_NAME.matcher(segments[i]).matches()) {
                return Phase.READ;
            }
        }
        return null;
    }

    /** The data structure a class (simple name, content) is, or null if it is none. */
    public static DataStructure dataStructure(String className, String content) {
        for (DataKind kind : DataKind.values()) {
            boolean annotated = kind == DataKind.ENTITY && content.contains("@Entity");
            if (kind.namePattern.matcher(className).matches() || annotated) {
                return new DataStructure(kind, kind.suffix.matcher(className).replaceFirst(""));
            }
        }
        return null;
    }

    /** The entity's stem of a logic class ("VertragItemReader" → "Vertrag"); the name itself if no role matches. */
    public static String logicStem(String className) {
        var m = LOGIC_NAME.matcher(className);
        return m.matches() ? m.group(1) : className;
    }

    /** Removes an old/new variant prefix: "AltVertrag" → "Vertrag". */
    public static String stripVariantPrefix(String name) {
        return VARIANT_PREFIX.matcher(name).replaceFirst("");
    }

    private static Pattern declares(String types) {
        return Pattern.compile("\\b(?:implements|extends)\\b[^{;]*?\\b(?:" + types + ")\\b");
    }

    /** Whole class name ending with one of the alternatives. */
    private static Pattern suffixes(String alternatives) {
        return Pattern.compile(".*(?:" + alternatives + ")");
    }
}
