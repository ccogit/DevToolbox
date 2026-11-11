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

    /** {@code reason} is e.g. "12 Trefferzeilen" or "Referenz von X.java". */
    record Entry(String relPath, String content, Phase phase, List<Integer> hitLines, String reason) {
        Entry withPhase(Phase newPhase) {
            return new Entry(relPath, content, newPhase, hitLines, reason);
        }

        boolean isReference() {
            return reason.startsWith("Referenz");
        }
    }

    /** Facts about the run, shown in the header. */
    record Meta(String sourceLabel, Path base, List<String> terms, String phases, boolean references, int scannedFiles,
                boolean compact, String attributes) {
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
        if (meta.attributes() != null) {
            md.append("- Das **Attributverzeichnis** listet die Quellattribute der Entität – aus der DDL der ")
                    .append("Altdatenbank oder, falls keine vorliegt, aus den Datenstrukturen im Code – und bei Phase ")
                    .append("Process die Zielattribute aus dem Zielmodell. Danach folgt der Code.\n");
        }
        md.append("- Jede Datei beginnt mit der Zeile `===== BEGIN FILE: <Pfad> =====` und endet mit ")
                .append("`===== END FILE: <Pfad> =====`. Dazwischen steht ihr Quelltext.\n");
        md.append("- Die Dateien sind heuristisch nach Phasen der Batch-Verarbeitung gruppiert ")
                .append("(Read → Process → Write → …). Die Zuordnung kann falsch sein; maßgeblich ist der Code.\n");
        md.append(meta.attributes() != null
                ? "- Dateien nicht betrachteter Batch-Phasen wurden weggelassen. Modell, Datenzugriff, "
                + "Konfiguration und Sonstiges sind immer enthalten.\n"
                : "- Enthalten sind nur Dateien der oben genannten Phasen/Kategorien.\n");
        md.append("- „Treffer“ enthalten einen Suchbegriff; „Referenzen“ enthalten ihn nicht, ")
                .append("werden aber von einer Trefferdatei direkt verwendet (z. B. DTOs, Entities).\n");
        if (meta.compact()) {
            md.append("- Kompaktmodus: Import-Zeilen und Lizenzköpfe wurden entfernt; ")
                    .append("Zeilennummern beziehen sich auf die Originaldatei.\n");
        }

        if (meta.attributes() != null) {
            md.append("\n## Attributverzeichnis\n\n").append(meta.attributes().strip()).append('\n');
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
