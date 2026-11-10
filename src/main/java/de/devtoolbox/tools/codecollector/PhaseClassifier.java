package de.devtoolbox.tools.codecollector;

import java.util.Locale;
import java.util.regex.Pattern;

/** Heuristic assignment of a file to a batch phase – only used for ordering the output. */
final class PhaseClassifier {

    private static final Pattern READ_TYPE = declares("\\w*ItemReader|ItemStreamReader|RowMapper|ResultSetExtractor");
    private static final Pattern PROCESS_TYPE = declares("\\w*ItemProcessor");
    private static final Pattern WRITE_TYPE = declares("\\w*ItemWriter");
    private static final Pattern CONFIG_JAVA =
            Pattern.compile("@Configuration|@EnableBatchProcessing|\\bJobBuilder\\b|\\bStepBuilder\\b|\\bimplements\\s+Tasklet\\b");
    private static final Pattern MODEL_JAVA =
            Pattern.compile("@Entity|@Embeddable|@Table\\b|@Xml(?:RootElement|Type)\\b|@Data\\b|\\brecord\\s+\\w+\\s*[(<]");

    private PhaseClassifier() {
    }

    static Phase classify(String relPath, String content) {
        String rel = relPath.replace('\\', '/');
        if (rel.startsWith("src/test/") || rel.contains("/src/test/")) {
            return Phase.TEST;
        }
        String fileName = rel.substring(rel.lastIndexOf('/') + 1);
        int dot = fileName.lastIndexOf('.');
        String name = dot < 0 ? fileName : fileName.substring(0, dot);
        String ext = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return switch (ext) {
            case "java" -> {
                Phase phase = java(name, content);
                yield phase == Phase.OTHER ? byPackage(rel) : phase;
            }
            case "sql" -> sql(content.toLowerCase(Locale.ROOT));
            case "xml" -> xml(content);
            case "properties", "yml", "yaml" -> Phase.CONFIG;
            default -> Phase.OTHER;
        };
    }

    private static Phase java(String name, String content) {
        // Implemented Spring Batch interfaces are the strongest signal, then naming conventions.
        if (READ_TYPE.matcher(content).find()) return Phase.READ;
        if (PROCESS_TYPE.matcher(content).find()) return Phase.PROCESS;
        if (WRITE_TYPE.matcher(content).find()) return Phase.WRITE;
        if (name.matches(".*(?:Reader|RowMapper|Extractor)")) return Phase.READ;
        if (name.matches(".*(?:Processor|Mapper|Mapping|Converter|Konverter|Transformer|Translator"
                + "|Zuordnung|Umsetzung|Ermittlung|Berechnung|Rechner|Calculator)")) return Phase.PROCESS;
        if (name.matches(".*(?:Writer|Exporter|Persister)")) return Phase.WRITE;
        if (name.matches(".*(?:Repository|Repo|Dao|DAO)")) return Phase.PERSISTENCE;
        if (CONFIG_JAVA.matcher(content).find()
                || name.matches(".*(?:Config|Configuration|Job|Step|Tasklet|Listener)")) {
            return Phase.CONFIG;
        }
        if (MODEL_JAVA.matcher(content).find() || name.matches(".*(?:Dto|DTO|TO|To|PO|Po|BO|Bo|Entity|Vo|VO|Model|Bean)")) {
            return Phase.MODEL;
        }
        return Phase.OTHER;
    }

    /** Fallback: package/folder names such as {@code ...risiko.process} or {@code .../reader/}. */
    private static Phase byPackage(String rel) {
        String dir = "/" + rel.substring(0, Math.max(0, rel.lastIndexOf('/'))).toLowerCase(Locale.ROOT) + "/";
        if (dir.matches(".*/(?:read|reader|readers|einlesen|lesen)/.*")) return Phase.READ;
        if (dir.matches(".*/(?:process|processor|processors|mapping|mapper|transform|verarbeitung)/.*")) return Phase.PROCESS;
        if (dir.matches(".*/(?:write|writer|writers|schreiben|export)/.*")) return Phase.WRITE;
        return Phase.OTHER;
    }

    private static Phase sql(String lower) {
        if (Pattern.compile("\\b(?:insert|update|merge)\\b").matcher(lower).find()) return Phase.WRITE;
        if (lower.contains("create table")) return Phase.MODEL;
        if (lower.matches("(?s).*\\bselect\\b.*")) return Phase.READ;
        return Phase.OTHER;
    }

    private static Phase xml(String content) {
        if (content.contains("<xs:schema") || content.contains("<xsd:schema")) return Phase.MODEL;
        if (content.contains("<mapper") || content.contains("<sqlMap")) return Phase.PERSISTENCE;
        if (content.contains("<batch:") || content.contains("<job") || content.contains("<beans")) return Phase.CONFIG;
        return Phase.OTHER;
    }

    private static Pattern declares(String types) {
        return Pattern.compile("\\b(?:implements|extends)\\b[^{;]*?\\b(?:" + types + ")\\b");
    }
}
