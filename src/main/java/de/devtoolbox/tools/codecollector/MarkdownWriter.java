package de.devtoolbox.tools.codecollector;

import de.devtoolbox.core.Texts;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Renders the collected files into one Markdown document with unambiguous file boundaries.
 */
final class MarkdownWriter {

    private static final int MAX_LISTED_LINES = 20;
    private static final Pattern LEADING_COMMENT = Pattern.compile("\\A\\s*/\\*.*?\\*/\\s*(?=package\\b)", Pattern.DOTALL);
    private static final Pattern IMPORT_LINE = Pattern.compile("^import\\s+[^;]+;[ \\t]*\\n", Pattern.MULTILINE);

    /**
     * {@code reason} is e.g. "12 Trefferzeilen" or "Referenz von X.java"; {@code depth} is 0 for a hit and the
     * reference level otherwise (1 = used directly by a hit); {@code origin} is the category before a reference
     * without a clearer role was taken as a data structure.
     */
    record Entry(String relPath, String content, Phase phase, List<Integer> hitLines, String reason, int depth,
                 Phase origin) {

        Entry(String relPath, String content, Phase phase, List<Integer> hitLines, String reason) {
            this(relPath, content, phase, hitLines, reason, 0, phase);
        }

        Entry withPhase(Phase newPhase) {
            return new Entry(relPath, content, newPhase, hitLines, reason, depth, origin);
        }

        Entry withDepth(int newDepth) {
            return new Entry(relPath, content, phase, hitLines, reason, newDepth, origin);
        }

        boolean isReference() {
            return reason.startsWith("Referenz");
        }
    }

    /** A file left out to keep the content file within its budget, with its category and the reason. */
    record Omitted(String relPath, Phase phase, String reason) {
    }

    /**
     * Facts about the run, shown in the header.
     *
     * @param supportAlwaysKept true for the job phase-spec: the supporting categories are kept whatever phases are
     *                          selected (other callers keep only the categories named in {@code phases})
     * @param omitted           files left out for the budget, listed before the table of contents
     */
    record Meta(String sourceLabel, Path base, List<String> terms, String phases, boolean references, int scannedFiles,
                boolean compact, boolean supportAlwaysKept, List<Omitted> omitted) {
    }

    private MarkdownWriter() {
    }

    static String render(Meta meta, List<Entry> entries) {
        StringBuilder md = new StringBuilder(header(meta, entries));
        Phase current = null;
        for (int i = 0; i < entries.size(); i++) {
            Phase phase = entries.get(i).phase();
            if (phase != current) {
                md.append("\n## ").append(phase.title).append('\n');
                current = phase;
            }
            md.append(block(i + 1, entries.get(i), meta.compact()));
        }
        return md.toString();
    }

    private static String header(Meta meta, List<Entry> entries) {
        long refs = entries.stream().filter(Entry::isReference).count();
        String terms = String.join(", ", meta.terms());
        StringBuilder md = new StringBuilder(4096);
        md.append("# Code-Kontext: ").append(meta.terms().getFirst());
        md.append("\n\n- ").append(meta.sourceLabel()).append(" (durchsucht): `").append(meta.base()).append("`\n");
        md.append("- Erzeugt: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append('\n');
        md.append("- Fachliche Entität: ").append(meta.terms().getFirst()).append(" · Suchbegriffe: ").append(terms)
                .append(" (Groß-/Kleinschreibung ignoriert)\n");
        md.append("- Betrachtete Phasen: ").append(meta.phases()).append('\n');
        md.append("- Umfang: ").append(meta.references()
                ? "Dateien mit Suchbegriff + direkt referenzierte Projektklassen" : "nur Dateien mit Suchbegriff").append('\n');
        md.append("- Dateien: %d (%d Treffer, %d Referenzen) von %d durchsuchten\n"
                .formatted(entries.size(), entries.size() - refs, refs, meta.scannedFiles()));

        md.append("\n## Aufbau dieser Datei\n\n");
        md.append("- Jede Datei beginnt mit der Zeile `===== BEGIN FILE: <Pfad> =====` und endet mit ")
                .append("`===== END FILE: <Pfad> =====`. Dazwischen steht ihr Quelltext.\n");
        md.append("- Die Dateien sind heuristisch nach Phasen der Batch-Verarbeitung gruppiert ")
                .append("(Read → Process → Write → …). Die Zuordnung kann falsch sein; maßgeblich ist der Code.\n");
        md.append(meta.supportAlwaysKept()
                ? "- Dateien nicht betrachteter Batch-Phasen wurden weggelassen. Modell, Datenzugriff, "
                + "Konfiguration und Sonstiges sind immer enthalten"
                + (meta.omitted().isEmpty() ? "" : ", soweit nicht unter „Nicht enthaltene Dateien“ genannt") + ".\n"
                : "- Enthalten sind nur Dateien der oben genannten Phasen/Kategorien.\n");
        md.append("- „Treffer“ enthalten einen Suchbegriff; „Referenzen“ enthalten ihn nicht, ")
                .append("werden aber von einer Trefferdatei direkt verwendet (z. B. DTOs, Entities).\n");
        if (meta.compact()) {
            md.append("- Kompaktmodus: Import-Zeilen und Lizenzköpfe wurden entfernt; ")
                    .append("Zeilennummern beziehen sich auf die Originaldatei.\n");
        }

        if (!meta.omitted().isEmpty()) {
            md.append("\n## Nicht enthaltene Dateien\n\n")
                    .append("Diese Dateien gehören zum Umfeld, sind aber nicht mitgeliefert. Was nur dort steht, ist ")
                    .append("ein OPL-Punkt.\n\n| Datei | Kategorie | Grund |\n|---|---|---|\n");
            for (Omitted o : meta.omitted()) {
                md.append("| `").append(o.relPath()).append("` | ").append(o.phase().shortName).append(" | ")
                        .append(o.reason()).append(" |\n");
            }
        }

        md.append("\n## Inhaltsverzeichnis\n\n");
        md.append("| # | Phase | Datei | Grund |\n|---|---|---|---|\n");
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            md.append("| ").append(i + 1).append(" | ");
            md.append(e.phase().shortName).append(" | `").append(e.relPath()).append("` | ").append(e.reason()).append(" |\n");
        }
        return md.toString();
    }

    private static String block(int number, Entry e, boolean compact) {
        String code = compact ? compact(e.relPath(), e.content()) : e.content().strip();
        StringBuilder md = new StringBuilder(code.length() + 300);
        md.append("\n### [").append(number).append("] ").append(fileName(e.relPath())).append("\n\n");
        md.append("Pfad: `").append(e.relPath()).append("` · ").append(e.reason());
        if (!e.hitLines().isEmpty()) {
            md.append(" · Zeilen: ").append(lines(e.hitLines()));
        }
        md.append("\n\n").append(Texts.fileBlock(e.relPath(), language(e.relPath()), code));
        return md.toString();
    }

    private static String compact(String relPath, String content) {
        String code = content;
        if (relPath.endsWith(".java")) {
            code = LEADING_COMMENT.matcher(code).replaceFirst("");
            code = IMPORT_LINE.matcher(code).replaceAll("");
        }
        return code.replaceAll("\n[ \\t]*(?:\n[ \\t]*)+\n", "\n\n").strip();
    }

    private static String lines(List<Integer> hitLines) {
        String listed = hitLines.stream().limit(MAX_LISTED_LINES).map(String::valueOf).collect(Collectors.joining(", "));
        return hitLines.size() > MAX_LISTED_LINES ? listed + ", …" : listed;
    }

    private static String fileName(String relPath) {
        return relPath.substring(relPath.lastIndexOf('/') + 1);
    }

    private static String language(String relPath) {
        String ext = ProjectScanner.extension(Path.of(fileName(relPath)));
        return switch (ext) {
            case "yml" -> "yaml";
            case "kt" -> "kotlin";
            default -> ext;
        };
    }
}
