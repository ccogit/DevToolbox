package de.devtoolbox.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Text helpers shared by jobs and tools: templates from the classpath, placeholders, code blocks. */
public final class Texts {

    private Texts() {
    }

    /** A UTF-8 text resource from the classpath (e.g. "/skills/phase-spec/skill.md") with "\n" line ends. */
    public static String resource(String path) throws IOException {
        try (InputStream in = Texts.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Vorlage fehlt: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    /**
     * Replaces each {@code {{KEY}}} of {@code values} in {@code template}, one key after another in the map's
     * iteration order (use a {@link java.util.LinkedHashMap}): a value may itself contain placeholders of later keys.
     */
    public static String fill(String template, Map<String, String> values) {
        String text = template;
        for (Map.Entry<String, String> e : values.entrySet()) {
            text = text.replace("{{" + e.getKey() + "}}", e.getValue());
        }
        return text;
    }

    /** Collapses three or more consecutive line breaks to one blank line (left over by empty placeholders). */
    public static String collapseBlankLines(String text) {
        return text.replaceAll("\n{3,}", "\n\n");
    }

    /** A Markdown code fence longer than any backtick run inside the code, so the code can never close it. */
    public static String fence(String code) {
        int longest = 0;
        int run = 0;
        for (int i = 0; i < code.length(); i++) {
            run = code.charAt(i) == '`' ? run + 1 : 0;
            longest = Math.max(longest, run);
        }
        return "`".repeat(Math.max(3, longest + 1));
    }

    /**
     * One file with unambiguous boundaries: {@code ===== BEGIN FILE: <name> =====}, the fenced code (taken as is)
     * and {@code ===== END FILE: <name> =====}, ending with a line break.
     */
    public static String fileBlock(String name, String language, String code) {
        String fence = fence(code);
        return "===== BEGIN FILE: " + name + " =====\n"
                + fence + language + '\n'
                + code + '\n'
                + fence + '\n'
                + "===== END FILE: " + name + " =====\n";
    }
}
