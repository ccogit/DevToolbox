package de.devtoolbox.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Size of the upload files: a report for the log and the check against the Copilot limit (see
 * {@link Keys#COPILOT_LIMIT}). Tokens are estimated as characters / 4.
 */
public final class CopilotLimit {

    /** Default limit in estimated tokens for all upload files together (Startwert, zu kalibrieren). */
    public static final String DEFAULT_LIMIT = "30000";

    private CopilotLimit() {
    }

    /** The param shared by the jobs: optional and advanced. */
    public static Param param() {
        return Param.text(Keys.COPILOT_LIMIT, "Copilot-Limit (Tokens)", DEFAULT_LIMIT, false,
                "Geschätzte Tokens für alle drei Dateien. Startwert, mit `copilot-testdateien` kalibrieren; 0 = aus.")
                .advanced();
    }

    /** Rough estimate: 4 characters per token. */
    public static int estimateTokens(List<Path> textFiles) throws IOException {
        long chars = 0;
        for (Path file : textFiles) {
            chars += Files.readString(file, StandardCharsets.UTF_8).length();
        }
        return (int) Math.min(Integer.MAX_VALUE, chars / 4);
    }

    /**
     * Logs one line per file (characters, lines, estimated tokens), the sum and – if given – the largest source files
     * of {@code contentFile}. PDF files are named but not counted.
     */
    public static void report(List<Path> files, String contentFile, List<String> largestSources, Consumer<String> log)
            throws IOException {
        long chars = 0;
        long lines = 0;
        for (Path file : files) {
            String name = file.getFileName().toString();
            if (name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                log.accept("Größe " + name + ": PDF, nicht gezählt");
                continue;
            }
            String text = Files.readString(file, StandardCharsets.UTF_8);
            long n = text.lines().count();
            log.accept("Größe %s: %,d Zeichen, %,d Zeilen, ca. %,d Tokens (geschätzt: Zeichen / 4)"
                    .formatted(name, text.length(), n, text.length() / 4));
            chars += text.length();
            lines += n;
        }
        log.accept("Größe gesamt: %,d Zeichen, %,d Zeilen, ca. %,d Tokens (geschätzt)".formatted(chars, lines, chars / 4));
        if (!largestSources.isEmpty()) {
            log.accept("Größte Quelldateien in " + contentFile + ": " + String.join(", ", largestSources));
        }
    }

    /**
     * Logs the estimate against the configured limit and, if above it, a WARNUNG with {@code suggestions}. A limit of
     * 0 (or empty) switches the check off.
     *
     * @param pdfNote true if an additional PDF is uploaded that is not counted
     */
    public static void check(Config config, List<Path> textFiles, boolean pdfNote, String suggestions,
                             Consumer<String> log) throws IOException {
        int limit = config.number(Keys.COPILOT_LIMIT);
        if (limit <= 0) {
            return;
        }
        int tokens = estimateTokens(textFiles);
        String note = pdfNote ? " (eine PDF-Spezifikation ist darin nicht gezählt)" : "";
        if (tokens > limit) {
            log.accept("WARNUNG: Geschätzt ca. %,d Tokens – über dem Copilot-Limit von %,d%s. %s"
                    .formatted(tokens, limit, note, suggestions));
        } else {
            log.accept("Geschätzte Größe: ca. %,d Tokens, Copilot-Limit %,d – ok%s.".formatted(tokens, limit, note));
        }
    }
}
