package de.devtoolbox.tools.mappingdoc;

import de.devtoolbox.tools.codecollector.CodeCollectorTool.SourceFile;
import de.devtoolbox.tools.codecollector.Phase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Heuristic pre-analysis of the mapping code (Process phase) for the documentation: where each attribute of the
 * template is set (setter, builder, field assignment) and where the code distinguishes pending changes
 * ("Schwebe") from the regular stock. It only points the AI to the right places – the code stays authoritative.
 */
public final class MappingAnalysis {

    /** Where an attribute is set: class.method:line, the code line, and whether it lies in a Schwebe context. */
    public record Location(String file, String method, int line, String code, boolean schwebe) {

        public String where() {
            String cls = file.substring(file.lastIndexOf('/') + 1).replaceFirst("\\.[^.]*$", "");
            return cls + (method.isEmpty() ? "" : "." + method) + ":" + line;
        }
    }

    /** Result: locations per template attribute (in template order) and all Schwebe lines of the Process code. */
    public record Result(Map<String, List<Location>> byAttribute, List<Location> schwebeLines, int processFiles) {

        public long attributesFound() {
            return byAttribute.values().stream().filter(l -> !l.isEmpty()).count();
        }

        public long attributesWithSchwebe() {
            return byAttribute.values().stream().filter(l -> l.stream().anyMatch(Location::schwebe)).count();
        }
    }

    private static final Pattern SCHWEBE = Pattern.compile("(?i)schwebe");
    private static final Pattern METHOD = Pattern.compile(
            "^\\s*(?:@\\w+(?:\\([^)]*\\))?\\s+)*(?:(?:public|protected|private|static|final|synchronized|abstract|default)\\s+)*"
                    + "(?:<[^>]+>\\s+)?[\\w.$]+(?:<[^;=(]*>)?(?:\\[])*\\s+(\\w+)\\s*\\([^;]*$");
    private static final java.util.Set<String> NO_METHOD =
            java.util.Set.of("if", "for", "while", "switch", "catch", "return", "new", "else", "throw", "try", "do");
    private static final int MAX_SCHWEBE_LINES = 150;

    private MappingAnalysis() {
    }

    /** Analyses the Process files among {@code files} for the {@code attributes} of the template. */
    public static Result analyse(List<SourceFile> files, List<String> attributes) {
        List<SourceFile> process = files.stream().filter(f -> f.phase() == Phase.PROCESS).toList();
        Map<String, Pattern> patterns = new LinkedHashMap<>();
        Map<String, List<Location>> byAttribute = new LinkedHashMap<>();
        for (String attribute : attributes) {
            byAttribute.putIfAbsent(attribute, new ArrayList<>());
            patterns.putIfAbsent(attribute, assignment(attribute));
        }
        List<Location> schwebeLines = new ArrayList<>();
        for (SourceFile file : process) {
            String[] lines = file.content().split("\n", -1);
            String method = "";
            boolean methodSchwebe = false;
            boolean inBlockComment = false;
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                String stripped = line.strip();
                if (inBlockComment) {
                    inBlockComment = !stripped.contains("*/");
                    continue;
                }
                if (stripped.startsWith("/*")) {
                    inBlockComment = !stripped.contains("*/");
                    continue;
                }
                if (stripped.startsWith("import ") || stripped.startsWith("package ") || stripped.startsWith("//")
                        || stripped.startsWith("*")) {
                    continue;
                }
                Matcher m = METHOD.matcher(line);
                if (m.find() && !NO_METHOD.contains(m.group(1))) {
                    method = m.group(1);
                    methodSchwebe = SCHWEBE.matcher(method).find();
                }
                boolean schwebeHere = SCHWEBE.matcher(line).find();
                if (schwebeHere) {
                    methodSchwebe = true; // later statements of this method run under a Schwebe condition or branch
                    if (schwebeLines.size() < MAX_SCHWEBE_LINES) {
                        schwebeLines.add(new Location(file.relPath(), method, i + 1, shorten(stripped), true));
                    }
                }
                for (Map.Entry<String, Pattern> p : patterns.entrySet()) {
                    if (p.getValue().matcher(line).find()) {
                        byAttribute.get(p.getKey()).add(
                                new Location(file.relPath(), method, i + 1, shorten(stripped), methodSchwebe));
                    }
                }
            }
        }
        return new Result(byAttribute, schwebeLines, process.size());
    }

    /** setXyz(…), builder .xyz(…), field assignment xyz = … (not ==) – case-insensitive. */
    static Pattern assignment(String attribute) {
        String name = attribute.strip().replaceAll("[^\\w$]", "");
        String q = Pattern.quote(name);
        return Pattern.compile("(?i)(?:\\bset" + q + "\\s*\\(|\\." + q + "\\s*\\(|(?<![\\w$])" + q + "\\s*=(?!=))");
    }

    /** Markdown: one table row per attribute, then the Schwebe lines. */
    public static String markdown(Result r) {
        StringBuilder md = new StringBuilder();
        md.append("Automatisch und heuristisch ermittelt (").append(r.processFiles())
                .append(r.processFiles() == 1 ? " Datei" : " Dateien").append(" der Process-Phase): wo jedes Attribut der Vorlage gesetzt wird (Setter, Builder, ")
                .append("Zuweisung) und ob die Stelle in einer Methode mit Schwebe-Bezug liegt. Maßgeblich ist der Code ")
                .append("im Abschnitt „Code“; fehlende Fundstellen heißen nicht, dass das Attribut nicht gemappt wird ")
                .append("(z. B. Konstruktor, generisches Mapping, andere Namen).\n\n");
        md.append("| Nr | Attribut | Fundstellen (Klasse.Methode:Zeile) | Schwebe-Bezug |\n|---|---|---|---|\n");
        int nr = 1;
        for (Map.Entry<String, List<Location>> e : r.byAttribute().entrySet()) {
            List<Location> locations = e.getValue();
            String where = locations.isEmpty() ? "– keine gefunden"
                    : String.join(", ", locations.stream().limit(8).map(l -> "`" + l.where() + "`").toList())
                    + (locations.size() > 8 ? " …" : "");
            long schwebe = locations.stream().filter(Location::schwebe).count();
            String flag = locations.isEmpty() ? "–" : schwebe == 0 ? "nein"
                    : schwebe == locations.size() ? "ja (nur im Schwebe-Kontext)" : "ja (" + schwebe + " von " + locations.size() + ")";
            md.append("| ").append(nr++).append(" | ").append(cell(e.getKey())).append(" | ").append(where)
                    .append(" | ").append(flag).append(" |\n");
        }
        md.append("\n### Schwebe-Stellen im Process-Code\n\n");
        if (r.schwebeLines().isEmpty()) {
            md.append("Keine Zeile der Process-Phase erwähnt „Schwebe“ – vermutlich wird Schwebe genauso gemappt wie ")
                    .append("der Bestand („wie Bestand“). Eine Unterscheidung außerhalb des Mappings (z. B. Reader, ")
                    .append("Step-Konfiguration) ist hier nicht sichtbar; weist der Code darauf hin, nenne es in der ")
                    .append("Chat-Übersicht.\n");
        } else {
            md.append("| Klasse.Methode:Zeile | Code |\n|---|---|\n");
            for (Location l : r.schwebeLines()) {
                md.append("| `").append(l.where()).append("` | `").append(l.code().replace("`", "'").replace("|", "\\|"))
                        .append("` |\n");
            }
            if (r.schwebeLines().size() >= MAX_SCHWEBE_LINES) {
                md.append("\n(Liste nach ").append(MAX_SCHWEBE_LINES).append(" Zeilen gekürzt.)\n");
            }
        }
        return md.toString();
    }

    private static String shorten(String code) {
        return code.length() > 160 ? code.substring(0, 157) + "…" : code;
    }

    private static String cell(String value) {
        return value.replace("|", "\\|").replace("\n", " ");
    }
}
