package de.devtoolbox.tools.codecollector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cuts a Java class that mentions the search term only in passing (a facade, a caching service, an aggregate TO)
 * down to the members that matter: those with a hit line plus the members of the same class they use. Without this,
 * such a class brings its whole content – and, through its imports, every entity of the project – into the code file.
 */
final class Excerpt {

    /** An excerpt is only taken if it leaves out at least this many lines … */
    private static final int MIN_OMITTED_LINES = 20;
    /** … and at least this share of the file. */
    private static final double MIN_OMITTED_SHARE = 0.3;

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*");
    private static final Pattern TYPE_DECLARATION = Pattern.compile("\\b(?:class|interface|enum|record)\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+(?:static\\s+)?([\\w.]+?)(\\.\\*)?\\s*;\\s*$");

    /**
     * @param content the shortened source
     * @param kept    members kept
     * @param total   members of the class
     */
    record Result(String content, int kept, int total) {
    }

    private record Member(int start, int end, String name) {
    }

    private Excerpt() {
    }

    /** True if the file name (without extension) contains a search term – such a file is about the entity itself. */
    static boolean namedAfter(String relPath, List<String> terms) {
        String name = relPath.substring(relPath.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String base = dot < 0 ? name : name.substring(0, dot);
        return terms.stream().anyMatch(t -> !t.isBlank() && base.contains(t.strip().toLowerCase(Locale.ROOT)));
    }

    /**
     * @param hitLines 1-based lines with a search term
     * @return the excerpt, or {@code null} if the file is kept whole (not parseable, or hardly anything to leave out)
     */
    static Result of(String content, List<Integer> hitLines) {
        if (hitLines.isEmpty()) {
            return null;
        }
        boolean[] code = codeMask(content);
        int open = -1;
        for (int i = 0; i < content.length() && open < 0; i++) {
            if (code[i] && content.charAt(i) == '{') {
                open = i;
            }
        }
        int close = open < 0 ? -1 : matching(content, code, open);
        if (close < 0 || !content.substring(close + 1).isBlank()) {
            return null; // no type, unbalanced braces or several top-level types
        }
        List<Member> members = members(content, code, open + 1, close);
        if (members.isEmpty()) {
            return null;
        }
        int[] lineOf = lineNumbers(content);
        String typeName = typeName(content.substring(0, open));
        boolean[] keep = new boolean[members.size()];
        for (int i = 0; i < keep.length; i++) {
            Member m = members.get(i);
            int from = lineOf[firstCode(content, m)];
            int to = lineOf[m.end() - 1];
            keep[i] = hitLines.stream().anyMatch(l -> l >= from && l <= to);
        }
        closure(content, code, members, keep, typeName);

        int totalLines = lineOf[content.length() - 1];
        int omittedLines = 0;
        int kept = 0;
        for (int i = 0; i < keep.length; i++) {
            if (keep[i]) {
                kept++;
            } else {
                omittedLines += lineOf[members.get(i).end() - 1] - lineOf[members.get(i).start()] + 1;
            }
        }
        if (kept == 0 || omittedLines < MIN_OMITTED_LINES || omittedLines < totalLines * MIN_OMITTED_SHARE) {
            return null;
        }

        StringBuilder body = new StringBuilder();
        for (int i = 0; i < keep.length; i++) {
            if (keep[i]) {
                body.append(content, members.get(i).start(), members.get(i).end());
                continue;
            }
            int last = i;
            while (last + 1 < keep.length && !keep[last + 1]) {
                last++;
            }
            int n = last - i + 1;
            body.append("\n    // … ").append(n).append(" Member ohne Bezug ausgelassen (")
                    .append(lines(lineOf[firstCode(content, members.get(i))], lineOf[members.get(last).end() - 1]))
                    .append(")\n");
            i = last;
        }
        String head = content.substring(0, open + 1);
        String tail = content.substring(close);
        String result = usedImports(head, head.substring(lastImportEnd(head)) + body) + body
                + (body.toString().endsWith("\n") ? "" : "\n") + tail;
        return new Result(result, kept, members.size());
    }

    private static String lines(int from, int to) {
        return from == to ? "Zeile " + from : "Zeilen " + from + "–" + to;
    }

    /**
     * Keeps the members a hit member uses directly (helpers, constants) and then the fields those use. Following
     * helpers of helpers would, in a large class, bring back most of it.
     */
    private static void closure(String content, boolean[] code, List<Member> members, boolean[] keep, String typeName) {
        for (int pass = 0; pass < 2; pass++) {
            Set<String> used = new HashSet<>();
            for (int i = 0; i < keep.length; i++) {
                if (keep[i]) {
                    used.addAll(identifiers(content, code, members.get(i).start(), members.get(i).end()));
                }
            }
            for (int i = 0; i < keep.length; i++) {
                Member m = members.get(i);
                boolean field = pass == 0 || !hasBody(content, code, m);
                if (!keep[i] && field && m.name() != null && !m.name().equals(typeName) && used.contains(m.name())) {
                    keep[i] = true;
                }
            }
        }
    }

    private static boolean hasBody(String content, boolean[] code, Member m) {
        for (int i = m.start(); i < m.end(); i++) {
            if (code[i] && content.charAt(i) == '{') {
                return true;
            }
        }
        return false;
    }

    private static Set<String> identifiers(String content, boolean[] code, int from, int to) {
        Set<String> result = new HashSet<>();
        Matcher m = IDENTIFIER.matcher(content).region(from, to);
        while (m.find()) {
            if (code[m.start()]) {
                result.add(m.group());
            }
        }
        return result;
    }

    /** Splits the class body into members: each ends at a {@code ;} or a closing brace on member level. */
    private static List<Member> members(String content, boolean[] code, int from, int to) {
        List<Member> members = new ArrayList<>();
        int start = from;
        int braces = 0;
        int parens = 0;
        for (int i = from; i < to; i++) {
            if (!code[i]) {
                continue;
            }
            char c = content.charAt(i);
            if (c == '(') {
                parens++;
            } else if (c == ')') {
                parens--;
            } else if (c == '{') {
                braces++;
            } else if (c == '}') {
                braces--;
            }
            boolean ends = braces == 0 && parens == 0 && (c == ';' || c == '}');
            if (!ends) {
                continue;
            }
            int end = i + 1;
            if (c == '}') { // "= { … };" and "= new X() { … };" end with the semicolon
                int next = nextCode(content, code, end, to);
                if (next >= 0 && content.charAt(next) == ';') {
                    end = next + 1;
                    i = next;
                }
            }
            // the rest of the line (trailing comment) belongs to the member
            int lineEnd = content.indexOf('\n', end);
            if (lineEnd >= 0 && lineEnd < to && content.substring(end, lineEnd).strip().matches("(//.*)?")) {
                end = lineEnd + 1;
                i = Math.max(i, end - 1);
            }
            members.add(new Member(start, end, memberName(content, code, start, end)));
            start = end;
        }
        if (braces != 0 || parens != 0) {
            return List.of();
        }
        if (start < to && !content.substring(start, to).isBlank() && !members.isEmpty()) {
            Member last = members.removeLast(); // e.g. enum constants without a closing semicolon
            members.add(new Member(last.start(), to, last.name()));
        }
        return members;
    }

    /** The declared name: of a nested type, otherwise the last identifier before {@code (}, {@code =}, {@code ;} or an opening brace. */
    private static String memberName(String content, boolean[] code, int from, int to) {
        StringBuilder declaration = new StringBuilder();
        for (int i = from; i < to; i++) {
            if (!code[i]) {
                declaration.append(' ');
                continue;
            }
            char c = content.charAt(i);
            if (c == '@') { // skip the annotation and its arguments
                i++;
                while (i < to && (Character.isJavaIdentifierPart(content.charAt(i)) || content.charAt(i) == '.')) {
                    i++;
                }
                int next = nextCode(content, code, i, to);
                if (next >= 0 && content.charAt(next) == '(') {
                    int depth = 0;
                    for (i = next; i < to; i++) {
                        if (code[i] && content.charAt(i) == '(') {
                            depth++;
                        } else if (code[i] && content.charAt(i) == ')' && --depth == 0) {
                            break;
                        }
                    }
                } else {
                    i--;
                }
                declaration.append(' ');
                continue;
            }
            if (c == '(' || c == '=' || c == ';' || c == '{') {
                break;
            }
            declaration.append(c);
        }
        Matcher type = TYPE_DECLARATION.matcher(declaration);
        if (type.find()) {
            return type.group(1);
        }
        String name = null;
        Matcher m = IDENTIFIER.matcher(declaration);
        while (m.find()) {
            name = m.group();
        }
        return name;
    }

    private static String typeName(String head) {
        String name = null;
        Matcher m = TYPE_DECLARATION.matcher(head);
        while (m.find()) {
            name = m.group(1);
        }
        return name;
    }

    /** Import lines of {@code head} reduced to those whose type is used in {@code used}; the rest of the head stays. */
    private static String usedImports(String head, String used) {
        Set<String> tokens = new HashSet<>();
        Matcher m = IDENTIFIER.matcher(used);
        while (m.find()) {
            tokens.add(m.group());
        }
        StringBuilder result = new StringBuilder();
        for (String line : head.split("\n", -1)) {
            Matcher imp = IMPORT.matcher(line);
            if (imp.matches() && imp.group(2) == null) {
                String[] parts = imp.group(1).split("\\.");
                boolean needed = tokens.contains(parts[parts.length - 1])
                        || (line.contains("static") && parts.length > 1 && tokens.contains(parts[parts.length - 2]));
                if (!needed) {
                    continue;
                }
            }
            result.append(line).append('\n');
        }
        result.setLength(result.length() - 1);
        return result.toString();
    }

    private static int lastImportEnd(String head) {
        int end = 0;
        int pos = 0;
        for (String line : head.split("\n", -1)) {
            pos += line.length() + 1;
            if (IMPORT.matcher(line).matches() || line.stripLeading().startsWith("package ")) {
                end = Math.min(pos, head.length());
            }
        }
        return end;
    }

    private static int firstCode(String content, Member m) {
        for (int i = m.start(); i < m.end(); i++) {
            if (!Character.isWhitespace(content.charAt(i))) {
                return i;
            }
        }
        return m.start();
    }

    private static int nextCode(String content, boolean[] code, int from, int to) {
        for (int i = from; i < to; i++) {
            if (code[i] && !Character.isWhitespace(content.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static int matching(String content, boolean[] code, int open) {
        int depth = 0;
        for (int i = open; i < content.length(); i++) {
            if (!code[i]) {
                continue;
            }
            if (content.charAt(i) == '{') {
                depth++;
            } else if (content.charAt(i) == '}' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    private static int[] lineNumbers(String content) {
        int[] lines = new int[content.length()];
        int line = 1;
        for (int i = 0; i < content.length(); i++) {
            lines[i] = line;
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return lines;
    }

    /** True for every character that is code – not part of a comment, string, text block or char literal. */
    private static boolean[] codeMask(String s) {
        boolean[] code = new boolean[s.length()];
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (s.startsWith("//", i)) {
                int end = s.indexOf('\n', i);
                i = end < 0 ? s.length() : end;
            } else if (s.startsWith("/*", i)) {
                int end = s.indexOf("*/", i + 2);
                i = end < 0 ? s.length() : end + 2;
            } else if (s.startsWith("\"\"\"", i)) {
                int end = s.indexOf("\"\"\"", i + 3);
                i = end < 0 ? s.length() : end + 3;
            } else if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < s.length() && s.charAt(j) != c && s.charAt(j) != '\n') {
                    j += s.charAt(j) == '\\' ? 2 : 1;
                }
                i = Math.min(j + 1, s.length());
            } else {
                code[i++] = true;
            }
        }
        return code;
    }
}
