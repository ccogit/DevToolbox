package de.devtoolbox.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/** Checks the estimated size of the upload files against the Copilot limit (see {@link Keys#COPILOT_LIMIT}). */
public final class CopilotLimit {

    private CopilotLimit() {
    }

    /** The param shared by the jobs: optional and advanced. */
    public static Param param() {
        return Param.text(Keys.COPILOT_LIMIT, "Copilot-Limit (Tokens)", "", false,
                "Wert aus dem Copilot-Größentest; leer = keine Prüfung").advanced();
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
     * Logs the estimate and, if above the configured limit, a WARNUNG with {@code suggestions}.
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
