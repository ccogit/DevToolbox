package de.devtoolbox.tools.speccheck;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** A Markdown table: header cells and rows of cells (unescaped, trimmed). Cells are separated at unescaped '|'. */
public record MdTable(List<String> header, List<List<String>> rows) {

    private static final Pattern SEPARATOR_CELL = Pattern.compile(":?-+:?");

    /** All tables in {@code lines} (a header line, a separator line and the rows following directly). */
    public static List<MdTable> parseAll(List<String> lines) {
        List<MdTable> tables = new ArrayList<>();
        int i = 0;
        while (i < lines.size()) {
            if (!isRow(lines.get(i))) {
                i++;
                continue;
            }
            int start = i;
            while (i < lines.size() && isRow(lines.get(i))) {
                i++;
            }
            if (i - start >= 2 && isSeparator(splitRow(lines.get(start + 1)))) {
                List<String> header = splitRow(lines.get(start));
                List<List<String>> rows = new ArrayList<>();
                for (int r = start + 2; r < i; r++) {
                    List<String> cells = new ArrayList<>(splitRow(lines.get(r)));
                    while (cells.size() < header.size()) {
                        cells.add("");
                    }
                    rows.add(cells);
                }
                tables.add(new MdTable(header, rows));
            }
        }
        return tables;
    }

    private static boolean isRow(String line) {
        return line.stripLeading().startsWith("|");
    }

    private static boolean isSeparator(List<String> cells) {
        return !cells.isEmpty() && cells.stream().allMatch(c -> SEPARATOR_CELL.matcher(c).matches());
    }

    /** Splits one table line at unescaped '|' (outer pipes dropped, {@code \|} becomes '|'). */
    public static List<String> splitRow(String line) {
        String text = line.strip();
        if (text.startsWith("|")) {
            text = text.substring(1);
        }
        if (text.endsWith("|") && !text.endsWith("\\|")) {
            text = text.substring(0, text.length() - 1);
        }
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length() && text.charAt(i + 1) == '|') {
                cell.append('|');
                i++;
            } else if (c == '|') {
                cells.add(cell.toString().strip());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString().strip());
        return cells;
    }

    /** Index of the first column whose normalized header starts with one of {@code names} (tried in order), or -1. */
    public int col(String... names) {
        for (String name : names) {
            for (int i = 0; i < header.size(); i++) {
                if (normalize(header.get(i)).startsWith(name)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** Index of the first column whose normalized header equals one of {@code names}, or -1. */
    public int colExact(String... names) {
        for (String name : names) {
            for (int i = 0; i < header.size(); i++) {
                if (normalize(header.get(i)).equals(name)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** Cell {@code col} of {@code row}, or an empty string if there is no such column. */
    public static String cell(List<String> row, int col) {
        return col < 0 || col >= row.size() ? "" : row.get(col);
    }

    private static String normalize(String header) {
        return header.toLowerCase(Locale.ROOT).replaceAll("\\(.*", "").replaceAll("[*`_]", "").strip();
    }

    /** The table as Markdown lines (no trailing line break). */
    public String render() {
        StringBuilder sb = new StringBuilder();
        sb.append(line(header)).append('\n');
        sb.append("|").append("---|".repeat(Math.max(1, header.size())));
        for (List<String> row : rows) {
            sb.append('\n').append(line(row));
        }
        return sb.toString();
    }

    private static String line(List<String> cells) {
        StringBuilder sb = new StringBuilder("|");
        for (String cell : cells) {
            sb.append(' ').append(cell.replace("|", "\\|").replace("\n", " ")).append(" |");
        }
        return sb.toString();
    }
}
