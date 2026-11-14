package de.devtoolbox.tools.codecollector;

import java.util.Locale;
import java.util.regex.Pattern;

import static de.devtoolbox.tools.codecollector.Conventions.*;

/**
 * Heuristic assignment of a file to a batch phase – only used for ordering the output. The rules (suffixes, folder
 * names, fixed conventions) live in {@link Conventions}.
 */
public final class PhaseClassifier {

    private static final Pattern SQL_WRITE = Pattern.compile("\\b(?:insert|update|merge)\\b");

    private PhaseClassifier() {
    }

    /**
     * A class named {@code …Repository}. Repositories usually read the data, so Read is their phase unless a
     * directory, an implemented Spring Batch type or the package says otherwise.
     */
    public static boolean isRepository(String className) {
        return Conventions.isRepository(className);
    }

    public static Phase classify(String relPath, String content) {
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
                Phase fixed = fixedByDirectory(rel);
                if (fixed != null) {
                    yield fixed;
                }
                if (ENRICHMENT_NAME.matcher(name).matches()) {
                    yield Phase.READ; // "Anreicherung"/"Anreicherer" is part of reading, whatever the class implements
                }
                Phase phase = java(name, content);
                if (isRepository(name) && !phase.batchPhase) {
                    // usually a repository reads; the package may place it in another phase
                    Phase byPackage = byPackage(rel);
                    yield byPackage == Phase.OTHER ? Phase.READ : byPackage;
                }
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
        if (READ_NAME.matcher(name).matches()) return Phase.READ;
        if (PROCESS_NAME.matcher(name).matches()) return Phase.PROCESS;
        if (WRITE_NAME.matcher(name).matches()) return Phase.WRITE;
        if (PERSISTENCE_NAME.matcher(name).matches()) return Phase.PERSISTENCE;
        if (CONFIG_CONTENT.matcher(content).find() || CONFIG_NAME.matcher(name).matches()) {
            return Phase.CONFIG;
        }
        if (MODEL_CONTENT.matcher(content).find() || MODEL_NAME.matcher(name).matches()) {
            return Phase.MODEL;
        }
        return Phase.OTHER;
    }

    /** Fallback: package/folder names such as {@code ...risiko.process} or {@code .../reader/}. */
    private static Phase byPackage(String rel) {
        String dir = "/" + rel.substring(0, Math.max(0, rel.lastIndexOf('/'))).toLowerCase(Locale.ROOT) + "/";
        if (READ_DIR.matcher(dir).matches()) return Phase.READ;
        if (PROCESS_DIR.matcher(dir).matches()) return Phase.PROCESS;
        if (WRITE_DIR.matcher(dir).matches()) return Phase.WRITE;
        return Phase.OTHER;
    }

    private static Phase sql(String lower) {
        if (SQL_WRITE.matcher(lower).find()) return Phase.WRITE;
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
}
