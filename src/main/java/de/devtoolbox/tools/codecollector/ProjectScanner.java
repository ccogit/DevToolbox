package de.devtoolbox.tools.codecollector;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Walks the project once (in parallel), finds term hits and indexes all Java types for reference lookup. */
final class ProjectScanner {

    private static final long MAX_FILE_SIZE = 2L * 1024 * 1024;
    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);

    /**
     * @param ignoreDeclarations package/import lines are no hits, and only the file name (not the folder path)
     *                           counts as a name hit – otherwise every class in a package {@code ...risk} is a hit
     * @param ignoreComments     comment lines ({@code //}, {@code /*}, {@code *}, in .sql {@code --} at the start)
     *                           are no hit lines; a hit in the file name still counts
     * @param excludeFiles       glob patterns of files that are neither hits nor references: without {@code /}
     *                           matched against the file name, with {@code /} against the path relative to the base
     */
    record Settings(List<String> terms, Set<String> extensions, Set<String> excludedDirs,
                    boolean includeTests, boolean ignoreDeclarations, Path outputDir, boolean ignoreComments,
                    List<String> excludeFiles) {

        Settings(List<String> terms, Set<String> extensions, Set<String> excludedDirs, boolean includeTests,
                 boolean ignoreDeclarations, Path outputDir) {
            this(terms, extensions, excludedDirs, includeTests, ignoreDeclarations, outputDir, false, List.of());
        }
    }

    record Hit(Path path, String content, List<Integer> lines) {
    }

    /**
     * @param commentOnly   files that would be hits if comment lines counted (sorted)
     * @param excludedFiles number of files left out by {@link Settings#excludeFiles}
     */
    record Result(List<Hit> hits, Map<String, Path> typesByFqn, int scannedFiles, List<String> ambiguousTypes,
                  List<Path> commentOnly, int excludedFiles) {
    }

    private ProjectScanner() {
    }

    static Result scan(Path base, Settings settings) throws IOException {
        List<Path> all = collectFiles(base, settings);
        List<FileGlob> excludes = settings.excludeFiles().stream()
                .map(g -> new FileGlob(glob(g), g.contains("/") || g.contains("\\"))).toList();
        List<Path> files = all.stream().filter(f -> !excluded(base, f, excludes)).toList();
        List<String> terms = settings.terms().stream().map(t -> t.toLowerCase(Locale.ROOT)).toList();
        Map<String, Path> types = new ConcurrentHashMap<>();
        Set<String> ambiguous = ConcurrentHashMap.newKeySet();
        Queue<Hit> hits = new ConcurrentLinkedQueue<>();
        Queue<Path> commentOnly = new ConcurrentLinkedQueue<>();
        files.parallelStream().forEach(file -> {
            String content = SourceReader.read(file);
            if (content == null) {
                return;
            }
            if (file.getFileName().toString().endsWith(".java")) {
                String name = fqn(content, file);
                types.merge(name, file, (previous, current) -> {
                    if (!previous.equals(current)) {
                        ambiguous.add(name);
                    }
                    return previous.toString().compareTo(current.toString()) <= 0 ? previous : current;
                });
            }
            boolean sql = extension(file).equals("sql");
            List<Integer> lines = hitLines(content, terms, settings.ignoreDeclarations(), settings.ignoreComments(), sql);
            String name = (settings.ignoreDeclarations() ? file.getFileName() : base.relativize(file))
                    .toString().toLowerCase(Locale.ROOT);
            if (!lines.isEmpty() || containsAny(name, terms)) {
                hits.add(new Hit(file, content, lines));
            } else if (settings.ignoreComments()
                    && !hitLines(content, terms, settings.ignoreDeclarations(), false, sql).isEmpty()) {
                commentOnly.add(file);
            }
        });
        List<Hit> sorted = hits.stream().sorted(Comparator.comparing(Hit::path)).toList();
        // A duplicate FQN cannot be resolved safely without its module context.
        ambiguous.forEach(types::remove);
        return new Result(sorted, Map.copyOf(types), files.size(), ambiguous.stream().sorted().toList(),
                commentOnly.stream().sorted().toList(), all.size() - files.size());
    }

    static String packageOf(String content) {
        Matcher m = PACKAGE.matcher(content);
        return m.find() ? m.group(1) : "";
    }

    private static String fqn(String content, Path file) {
        String simple = file.getFileName().toString().replaceFirst("\\.java$", "");
        String pkg = packageOf(content);
        return pkg.isEmpty() ? simple : pkg + "." + simple;
    }

    private static List<Integer> hitLines(String content, List<String> terms, boolean ignoreDeclarations,
                                          boolean ignoreComments, boolean sql) {
        String lower = content.toLowerCase(Locale.ROOT);
        if (!containsAny(lower, terms)) {
            return List.of();
        }
        List<Integer> result = new ArrayList<>();
        String[] lines = lower.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (containsAny(lines[i], terms) && !(ignoreDeclarations && isDeclaration(lines[i]))
                    && !(ignoreComments && isComment(lines[i], sql))) {
                result.add(i + 1);
            }
        }
        return result;
    }

    private static boolean isDeclaration(String line) {
        String s = line.stripLeading();
        return s.startsWith("package ") || s.startsWith("import ");
    }

    /** Line-based: only comments at the start of a line are recognised (code after a closing block comment counts). */
    private static boolean isComment(String line, boolean sql) {
        String s = line.stripLeading();
        return s.startsWith("//") || s.startsWith("/*") || s.startsWith("*") || (sql && s.startsWith("--"));
    }

    /** An exclude pattern; {@code onPath}: matched against the relative path, otherwise against the file name. */
    private record FileGlob(Pattern regex, boolean onPath) {
    }

    private static boolean excluded(Path base, Path file, List<FileGlob> excludes) {
        if (excludes.isEmpty()) {
            return false;
        }
        String name = file.getFileName().toString();
        String rel = base.relativize(file).toString().replace('\\', '/');
        return excludes.stream().anyMatch(g -> g.regex().matcher(g.onPath() ? rel : name).matches());
    }

    /**
     * A glob pattern as regex, case-insensitive: {@code **} any path, {@code *} any characters within a folder name,
     * {@code ?} one character; a leading {@code **}{@code /} also matches no folder at all.
     */
    static Pattern glob(String glob) {
        String g = glob.strip().replace('\\', '/');
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < g.length(); i++) {
            char c = g.charAt(i);
            if (c == '*' && i + 1 < g.length() && g.charAt(i + 1) == '*') {
                boolean slash = i + 2 < g.length() && g.charAt(i + 2) == '/';
                regex.append(slash ? "(?:.*/)?" : ".*");
                i += slash ? 2 : 1;
            } else if (c == '*') {
                regex.append("[^/]*");
            } else if (c == '?') {
                regex.append("[^/]");
            } else if (c == '/') {
                regex.append('/');
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static boolean containsAny(String text, List<String> terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private static List<Path> collectFiles(Path base, Settings s) throws IOException {
        return SourceTree.files(base, s.excludedDirs(), s.includeTests(), s.outputDir(),
                (file, attrs) -> attrs.size() <= MAX_FILE_SIZE && s.extensions().contains(extension(file)));
    }

    static String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
