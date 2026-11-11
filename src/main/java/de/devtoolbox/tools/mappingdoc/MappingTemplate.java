package de.devtoolbox.tools.mappingdoc;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * A template of the mapping documentation ("Mapping Export"): a Confluence wiki table, one per entity.
 * <pre>
 * ||Entität||Attribut||Mapping||Schwebe Mapping||Spezifikation||Status||
 * |NachlassZuschlag|DiffStatus|{status:colour=Red|title=TBD|subtle=true}|…|
 * | |NachlassZuschlagArt|…|
 * </pre>
 * An empty entity cell continues the entity of the row above. Rows wrapped over several lines (e.g. by an export)
 * are joined. Cells are split at {@code |} outside of macros ({@code {…}}) and links ({@code […]}).
 *
 * @param text            the file content as read (it is handed to the AI unchanged)
 * @param header          column titles
 * @param rows            data rows in file order
 * @param mappingColumn   index of the mapping column for the regular stock ("Mapping"), -1 if missing
 * @param schwebeColumn   index of the mapping column for pending changes ("Schwebe Mapping"), -1 if missing
 */
public record MappingTemplate(Path file, String text, List<String> header, List<Row> rows, int mappingColumn,
                              int schwebeColumn) {

    /** One attribute of the template; {@code cells} in header order (entity cell filled in). */
    public record Row(String entity, String attribute, List<String> cells) {
    }

    /** Result of {@link #find}: {@code template} is null if no template matches. */
    public record Found(MappingTemplate template, List<String> otherCandidates) {
    }

    /** The entities of the template in file order, without duplicates. */
    public List<String> entities() {
        return rows.stream().map(Row::entity).filter(e -> !e.isEmpty()).distinct().toList();
    }

    public List<String> attributes() {
        return rows.stream().map(Row::attribute).toList();
    }

    /** All files of the folder that contain a mapping table (any extension; unreadable files are skipped). */
    public static List<MappingTemplate> all(Path folder) throws IOException {
        if (folder == null || !Files.isDirectory(folder)) {
            return List.of();
        }
        List<Path> files;
        try (Stream<Path> list = Files.list(folder)) {
            files = list.filter(Files::isRegularFile).filter(p -> !p.getFileName().toString().startsWith("."))
                    .sorted().toList();
        }
        List<MappingTemplate> result = new ArrayList<>();
        for (Path file : files) {
            String text;
            try {
                text = Files.readString(file, StandardCharsets.UTF_8);
            } catch (MalformedInputException e) {
                text = Files.readString(file, StandardCharsets.ISO_8859_1);
            }
            MappingTemplate t = parse(file, text);
            if (t != null) {
                result.add(t);
            }
        }
        return result;
    }

    /**
     * The template of an entity: entity name in the table equal to a term first, then a file name equal to a term,
     * then names containing a term.
     */
    public static Found find(Path folder, List<String> terms) throws IOException {
        List<String> lower = terms.stream().map(t -> t.strip().toLowerCase(Locale.ROOT)).filter(t -> !t.isEmpty()).toList();
        List<MappingTemplate> exact = new ArrayList<>();
        List<MappingTemplate> byFile = new ArrayList<>();
        List<MappingTemplate> partial = new ArrayList<>();
        for (MappingTemplate t : all(folder)) {
            List<String> entities = t.entities().stream().map(e -> e.toLowerCase(Locale.ROOT)).toList();
            String fileName = t.file().getFileName().toString().replaceFirst("\\.[^.]*$", "").toLowerCase(Locale.ROOT);
            if (entities.stream().anyMatch(lower::contains)) {
                exact.add(t);
            } else if (lower.contains(fileName)) {
                byFile.add(t);
            } else if (lower.stream().anyMatch(term -> fileName.contains(term)
                    || entities.stream().anyMatch(e -> e.contains(term)))) {
                partial.add(t);
            }
        }
        List<MappingTemplate> candidates = new ArrayList<>(exact);
        candidates.addAll(byFile);
        candidates.addAll(partial);
        if (candidates.isEmpty()) {
            return new Found(null, List.of());
        }
        return new Found(candidates.getFirst(), candidates.subList(1, candidates.size()).stream()
                .map(t -> t.file().getFileName() + " (" + String.join(", ", t.entities()) + ")").toList());
    }

    /** The table of {@code text}, or null if it contains none with an attribute column. */
    static MappingTemplate parse(Path file, String text) {
        List<String> lines = joinWrapped(text.replace("\r\n", "\n").replace('\r', '\n'));
        List<String> header = null;
        List<Row> rows = new ArrayList<>();
        int entityCol = -1;
        int attributeCol = -1;
        String entity = "";
        for (String line : lines) {
            String l = line.strip();
            if (header == null) {
                if (l.startsWith("||")) {
                    header = new ArrayList<>();
                    for (String h : l.split("\\|\\|")) {
                        if (!h.isBlank()) {
                            header.add(h.strip());
                        }
                    }
                    entityCol = column(header, "entit");
                    attributeCol = column(header, "attribut");
                    if (attributeCol < 0) {
                        header = null;
                    }
                }
                continue;
            }
            if (!l.startsWith("|") || l.startsWith("||")) {
                if (!rows.isEmpty() && !l.isEmpty()) {
                    break; // the table ended
                }
                continue;
            }
            List<String> cells = cells(l);
            while (cells.size() < header.size()) {
                cells.add("");
            }
            if (entityCol >= 0) {
                String e = cells.get(entityCol).strip();
                if (!e.isEmpty()) {
                    entity = e;
                }
                cells.set(entityCol, entity);
            }
            String attribute = cells.get(attributeCol).strip();
            if (!attribute.isEmpty()) {
                rows.add(new Row(entity, attribute, List.copyOf(cells)));
            }
        }
        if (header == null) {
            return null;
        }
        int schwebe = column(header, "schwebe");
        int mapping = -1;
        for (int i = 0; i < header.size(); i++) {
            String h = header.get(i).toLowerCase(Locale.ROOT);
            if (h.contains("mapping") && i != schwebe) {
                mapping = i;
                break;
            }
        }
        return new MappingTemplate(file, text, List.copyOf(header), List.copyOf(rows), mapping, schwebe);
    }

    private static int column(List<String> header, String part) {
        for (int i = 0; i < header.size(); i++) {
            if (header.get(i).toLowerCase(Locale.ROOT).contains(part)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Joins continuation lines (not starting with "|") to the row before. Inside an open macro ({@code {…}}) the
     * break was inserted by wrapping and is removed; otherwise it becomes a space.
     */
    static List<String> joinWrapped(String text) {
        List<String> result = new ArrayList<>();
        StringBuilder current = null;
        for (String line : text.split("\n", -1)) {
            String stripped = line.strip();
            boolean continuation = current != null && !stripped.isEmpty() && !stripped.startsWith("|");
            if (continuation) {
                current.append(openBraces(current) > 0 ? "" : " ").append(stripped);
                continue;
            }
            if (current != null) {
                result.add(current.toString());
                current = null;
            }
            if (stripped.startsWith("|")) {
                current = new StringBuilder(line);
            } else {
                result.add(line);
            }
        }
        if (current != null) {
            result.add(current.toString());
        }
        return result;
    }

    private static int openBraces(CharSequence s) {
        int open = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '{') {
                open++;
            } else if (s.charAt(i) == '}' && open > 0) {
                open--;
            }
        }
        return open;
    }

    /** Cells of a data row, without the outer pipes. */
    static List<String> cells(String row) {
        List<String> cells = new ArrayList<>();
        String s = row.strip();
        if (s.startsWith("|")) {
            s = s.substring(1);
        }
        StringBuilder cell = new StringBuilder();
        int braces = 0;
        int brackets = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length() && s.charAt(i + 1) == '|') {
                cell.append("\\|");
                i++;
                continue;
            }
            if (c == '{') {
                braces++;
            } else if (c == '}' && braces > 0) {
                braces--;
            } else if (c == '[') {
                brackets++;
            } else if (c == ']' && brackets > 0) {
                brackets--;
            } else if (c == '|' && braces == 0 && brackets == 0) {
                cells.add(cell.toString().strip());
                cell.setLength(0);
                continue;
            }
            cell.append(c);
        }
        if (!cell.toString().isBlank()) {
            cells.add(cell.toString().strip());
        }
        return cells;
    }
}
