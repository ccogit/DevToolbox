package de.devtoolbox.tools.speccheck;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The files of the code context: blocks between {@code ===== BEGIN FILE: <path> =====} and the matching END line. */
public final class CodeContext {

    private static final Pattern BEGIN = Pattern.compile("^===== BEGIN FILE: (.+?) =====\\s*$");
    private static final Pattern END = Pattern.compile("^===== END FILE: (.+?) =====\\s*$");

    private final Map<String, String> files;

    private CodeContext(Map<String, String> files) {
        this.files = files;
    }

    /** Parses the blocks of one or more code files; paths use '/'. */
    public static CodeContext parse(List<String> texts) {
        Map<String, String> files = new LinkedHashMap<>();
        for (String text : texts) {
            String path = null;
            StringBuilder content = new StringBuilder();
            for (String line : text.replace("﻿", "").lines().toList()) {
                Matcher begin = BEGIN.matcher(line);
                Matcher end = END.matcher(line);
                if (path == null && begin.matches()) {
                    path = begin.group(1).replace('\\', '/');
                    content.setLength(0);
                } else if (path != null && end.matches()) {
                    files.merge(path, content.toString(), (a, b) -> a + "\n" + b);
                    path = null;
                } else if (path != null) {
                    content.append(line).append('\n');
                }
            }
        }
        return new CodeContext(files);
    }

    public int size() {
        return files.size();
    }

    /** Contents of all files named {@code <className>.java}. */
    public List<String> classSources(String className) {
        return named(className + ".java");
    }

    /** True if there is a file block whose path ends with the file name {@code name} (case-insensitive). */
    public boolean hasFile(String name) {
        return !named(name).isEmpty();
    }

    private List<String> named(String name) {
        String lower = name.replace('\\', '/').toLowerCase(Locale.ROOT);
        return files.entrySet().stream()
                .filter(e -> {
                    String p = e.getKey().toLowerCase(Locale.ROOT);
                    return p.equals(lower) || p.endsWith("/" + lower);
                })
                .map(Map.Entry::getValue)
                .toList();
    }

    /** True if {@code source} contains a call or declaration {@code method(}. */
    public static boolean hasMethod(String source, String method) {
        return Pattern.compile("\\b" + Pattern.quote(method) + "\\s*\\(").matcher(source).find();
    }
}
