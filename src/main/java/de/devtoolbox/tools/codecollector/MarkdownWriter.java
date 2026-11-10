package de.devtoolbox.tools.codecollector;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Renders the collected files into one or more Markdown documents with unambiguous file boundaries.
 * Splitting happens only between files; every part carries the full table of contents.
 */
final class MarkdownWriter {

    private static final int MAX_LISTED_LINES = 20;
    /** Room for the phase headings inside a part when estimating its size. */
    private static final int HEADING_RESERVE = 600;
    private static final Pattern LEADING_COMMENT = Pattern.compile("\\A\\s*/\\*.*?\\*/\\s*(?=package\\b)", Pattern.DOTALL);
    private static final Pattern IMPORT_LINE = Pattern.compile("^import\\s+[^;]+;[ \\t]*\\n", Pattern.MULTILINE);

    /** {@code reason} is e.g. "12 Trefferzeilen" or "Referenz von X.java". */
    record Entry(String relPath, String content, Phase phase, List<Integer> hitLines, String reason) {
        Entry withPhase(Phase newPhase) {
            return new Entry(relPath, content, newPhase, hitLines, reason);
        }

        boolean isReference() {
            return reason.startsWith("Referenz");
        }
    }

    /** Facts about the run, shown in each part's header. */
    record Meta(Path base, List<String> terms, String phases, boolean references, int scannedFiles,
                boolean compact) {
    }

    private MarkdownWriter() {
    }

    /** @param maxChars upper size per part (0 = one document) */
    static List<String> render(Meta meta, List<Entry> entries, int maxChars) {
        List<String> blocks = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            blocks.add(block(i + 1, entries.get(i), meta.compact()));
        }
        int[] partOf = new int[entries.size()];
        int fixed = header(meta, entries, partOf, 0, 1).length() + HEADING_RESERVE;
        int part = 0;
        int size = fixed;
        for (int i = 0; i < blocks.size(); i++) {
            int len = blocks.get(i).length();
            if (maxChars > 0 && size > fixed && size + len > maxChars) {
                part++;
                size = fixed;
            }
            partOf[i] = part;
            size += len;
        }
        int parts = part + 1;

        List<String> documents = new ArrayList<>();
        for (int p = 0; p < parts; p++) {
            StringBuilder md = new StringBuilder(header(meta, entries, partOf, p, parts));
            Phase current = null;
            for (int i = 0; i < entries.size(); i++) {
                if (partOf[i] != p) {
                    continue;
                }
                Phase phase = entries.get(i).phase();
                if (phase != current) {
                    boolean continued = i > 0 && entries.get(i - 1).phase() == phase && current == null;
                    md.append("\n## ").append(phase.title).append(continued ? " (Fortsetzung)" : "").append('\n');
                    current = phase;
                }
                md.append(blocks.get(i));
            }
            documents.add(md.toString());
        }
        return documents;
    }

    private static String header(Meta meta, List<Entry> entries, int[] partOf, int part, int parts) {
        long refs = entries.stream().filter(Entry::isReference).count();
        String terms = String.join(", ", meta.terms());
        StringBuilder md = new StringBuilder(4096);
        md.append("# Code-Kontext: ").append(terms);
        if (parts > 1) {
            md.append(" – Teil ").append(part + 1).append(" von ").append(parts);
        }
        md.append("\n\n- Projekt: `").append(meta.base()).append("`\n");
        md.append("- Erzeugt: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))).append('\n');
        md.append("- Suchbegriffe: ").append(terms).append(" (Groß-/Kleinschreibung ignoriert)\n");
        md.append("- Betrachtete Phasen: ").append(meta.phases()).append('\n');
        md.append("- Umfang: ").append(meta.references()
                ? "Dateien mit Suchbegriff + direkt referenzierte Projektklassen" : "nur Dateien mit Suchbegriff").append('\n');
        md.append("- Dateien: %d (%d Treffer, %d Referenzen) von %d durchsuchten\n"
                .formatted(entries.size(), entries.size() - refs, refs, meta.scannedFiles()));
        if (parts > 1) {
            int first = -1;
            int last = -1;
            for (int i = 0; i < partOf.length; i++) {
                if (partOf[i] == part) {
                    first = first < 0 ? i + 1 : first;
                    last = i + 1;
                }
            }
            md.append("- **Teil %d von %d:** enthält die Dateien Nr. %d–%d. Die übrigen stehen in den anderen Teilen "
                    .formatted(part + 1, parts, first, last)).append("(Spalte „Teil“ im Inhaltsverzeichnis).\n");
        }

        md.append("\n## Aufbau dieser Datei\n\n");
        md.append("- Jede Datei beginnt mit der Zeile `===== BEGIN FILE: <Pfad> =====` und endet mit ")
                .append("`===== END FILE: <Pfad> =====`. Dazwischen steht ihr Quelltext.\n");
        md.append("- Die Dateien sind heuristisch nach Phasen der Batch-Verarbeitung gruppiert ")
                .append("(Read → Process → Write → …). Die Zuordnung kann falsch sein; maßgeblich ist der Code.\n");
        md.append("- Dateien nicht betrachteter Batch-Phasen wurden weggelassen. Modell, Datenzugriff, ")
                .append("Konfiguration und Sonstiges sind immer enthalten.\n");
        md.append("- „Treffer“ enthalten einen Suchbegriff; „Referenzen“ enthalten ihn nicht, ")
                .append("werden aber von einer Trefferdatei direkt verwendet (z. B. DTOs, Entities).\n");
        if (meta.compact()) {
            md.append("- Kompaktmodus: Import-Zeilen und Lizenzköpfe wurden entfernt; ")
                    .append("Zeilennummern beziehen sich auf die Originaldatei.\n");
        }

        md.append("\n## Inhaltsverzeichnis\n\n");
        md.append(parts > 1 ? "| # | Teil | Phase | Datei | Grund |\n|---|---|---|---|---|\n"
                : "| # | Phase | Datei | Grund |\n|---|---|---|---|\n");
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            md.append("| ").append(i + 1).append(" | ");
            if (parts > 1) {
                md.append(partOf[i] + 1).append(" | ");
            }
            md.append(e.phase().shortName).append(" | `").append(e.relPath()).append("` | ").append(e.reason()).append(" |\n");
        }
        return md.toString();
    }

    private static String block(int number, Entry e, boolean compact) {
        String code = compact ? compact(e.relPath(), e.content()) : e.content().strip();
        String fence = fence(code);
        StringBuilder md = new StringBuilder(code.length() + 300);
        md.append("\n### [").append(number).append("] ").append(fileName(e.relPath())).append("\n\n");
        md.append("Pfad: `").append(e.relPath()).append("` · ").append(e.reason());
        if (!e.hitLines().isEmpty()) {
            md.append(" · Zeilen: ").append(lines(e.hitLines()));
        }
        md.append("\n\n===== BEGIN FILE: ").append(e.relPath()).append(" =====\n");
        md.append(fence).append(language(e.relPath())).append('\n');
        md.append(code).append('\n');
        md.append(fence).append('\n');
        md.append("===== END FILE: ").append(e.relPath()).append(" =====\n");
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

    /** A fence longer than any backtick run inside the code, so the code can never close it. */
    private static String fence(String code) {
        int longest = 0;
        int run = 0;
        for (int i = 0; i < code.length(); i++) {
            run = code.charAt(i) == '`' ? run + 1 : 0;
            longest = Math.max(longest, run);
        }
        return "`".repeat(Math.max(3, longest + 1));
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
