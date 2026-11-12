package de.devtoolbox.tools.codecollector;

import de.devtoolbox.tools.codecollector.DdlReader.Column;
import de.devtoolbox.tools.codecollector.DdlReader.Table;
import de.devtoolbox.tools.codecollector.MarkdownWriter.Entry;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The target lists (Soll-Listen) of the business entity – the numbered checklist the AI works through row by row.
 * Primary source is the DDL of the legacy database, split into core tables (the entity's own tables) and secondary
 * tables (only mentioned in the code); without (relevant) DDL the fields of the collected data structures (TOs, POs,
 * DTOs, entities) are used. The target list of Process comes from the target model.
 */
final class AttributeIndex {

    private static final Pattern FIELD = Pattern.compile(
            "^\\s*(?:private|protected|public)\\s+(?!static\\b)(?:final\\s+|transient\\s+)*([\\w.$]+(?:<[^;=]*>)?(?:\\[])*)\\s+(\\w+)\\s*(?:=[^;]*)?;");
    private static final Pattern RECORD = Pattern.compile("\\brecord\\s+(\\w+)\\s*(?:<[^>]*>)?\\s*\\(([^)]*)\\)");
    private static final Pattern ENUM = Pattern.compile("\\benum\\s+\\w+");
    private static final Pattern COLUMN_NAME = Pattern.compile("@Column\\s*\\([^)]*\\bname\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern IDENTIFIER = Pattern.compile("[\\w$#@]+");

    static final String READ_TITLE = "Soll Read";
    static final String PROCESS_TITLE = "Soll Process";
    static final String SOURCES_TITLE = "Quellattribute (kein Soll in diesem Auftrag)";
    static final String SECONDARY_TITLE = "Nebentabellen (kein Soll)";
    static final String WRITE_NOTE = "Für Write gibt es keine Soll-Liste; siehe Skill, Abschnitt Phase Write.";

    /**
     * One group of source attributes: a table or a data structure.
     *
     * @param heading     e.g. "Tabelle ALTB.ALT_RISIKO" or "Klasse NeuRisikoEntity"
     * @param description lines between heading and table (label/comment, found in, keys …)
     * @param header      column names of the table, without "Nr"; the first column is the key
     * @param rows        one row per attribute, cells in the order of {@code header}
     */
    record Group(String heading, List<String> description, List<String> header, List<List<String>> rows) {
    }

    /**
     * The source attributes of a run.
     *
     * @param core        groups that make up the target list of Read
     * @param secondary   tables only mentioned in the code (no target rows)
     * @param fromDdl     whether the groups are DDL tables (otherwise data structures in the code)
     * @param summary     short summary for the log line "Attributverzeichnis: …"
     * @param warnings    log lines (WARNUNG …) about the selection of core tables
     */
    record Sources(List<Group> core, List<Group> secondary, boolean fromDdl, String summary, List<String> warnings) {

        int coreRows() {
            return core.stream().mapToInt(g -> g.rows().size()).sum();
        }
    }

    /** Rendered target lists plus the number of target rows per selected phase ("Read", "Process"). */
    record Inventory(String markdown, Map<String, Integer> targetRows, String summary) {
    }

    private record Mention(int fileIndex, int offset, String word, String fileName) {
    }

    private AttributeIndex() {
    }

    /**
     * Splits the DDL tables into core and secondary tables. Core tables are the {@code requested} ones (names with or
     * without schema, any case) or – if none are requested – the tables whose name contains a search term; if there is
     * no such table, all tables mentioned in the code. Secondary tables are the other tables mentioned in the code.
     *
     * @return null if no table is relevant at all (then the data structures in the code are used)
     */
    static Sources fromDdl(List<Table> tables, List<String> terms, List<String> requested, List<Entry> entries) {
        Map<Table, String> foundIn = new HashMap<>();
        List<Table> relevant = relevantTables(tables, terms, entries, foundIn);
        List<String> warnings = new ArrayList<>();
        List<Table> core = new ArrayList<>();
        if (!requested.isEmpty()) {
            List<String> unknown = new ArrayList<>();
            for (String name : requested) {
                List<Table> matching = tables.stream().filter(t -> t.name().equalsIgnoreCase(name)
                        || t.qualifiedName().equalsIgnoreCase(name)).toList();
                if (matching.isEmpty()) {
                    unknown.add(name);
                }
                for (Table t : matching) {
                    if (!core.contains(t)) {
                        core.add(t);
                        foundIn.put(t, "angegeben unter „Tabellen der Entität“");
                    }
                }
            }
            if (!unknown.isEmpty()) {
                throw new IllegalArgumentException("Tabellen der Entität nicht in der DDL: " + String.join(", ", unknown)
                        + ". Vorhanden: " + String.join(", ", tables.stream().map(Table::qualifiedName).toList()));
            }
        } else {
            List<String> lowerTerms = terms.stream().map(t -> t.toLowerCase(Locale.ROOT)).toList();
            relevant.stream().filter(t -> lowerTerms.stream().anyMatch(t.name().toLowerCase(Locale.ROOT)::contains))
                    .forEach(core::add);
            if (core.isEmpty() && !relevant.isEmpty()) {
                core.addAll(relevant);
                warnings.add("WARNUNG: Keine Tabelle enthält einen Suchbegriff – Tabellen der Entität angeben.");
            }
        }
        if (core.isEmpty()) {
            return null;
        }
        List<Table> secondary = relevant.stream().filter(t -> !core.contains(t)).toList();

        // The key is Tabelle.Spalte without schema: DdlReader already merges tables by their unqualified name.
        List<Group> coreGroups = new ArrayList<>();
        for (Table t : core) {
            coreGroups.add(coreGroup(t, foundIn.get(t), t.name()));
        }
        List<Group> secondaryGroups = new ArrayList<>();
        for (Table t : secondary) {
            secondaryGroups.add(secondaryGroup(t, foundIn.get(t), t.name()));
        }
        int columns = concat(core, secondary).stream().mapToInt(t -> t.columns().size()).sum();
        String summary = "%d Tabellen, %d Attribute aus DDL (Kern: %s%s)".formatted(core.size() + secondary.size(),
                columns, names(core), secondary.isEmpty() ? "" : " · Neben: " + names(secondary));
        return new Sources(coreGroups, secondaryGroups, true, summary, warnings);
    }

    /**
     * Finds table references in a single scan of the collected code instead of matching every table against every
     * source file. A table is relevant if its name contains a search term or the code mentions it (or, for a view, one
     * of its base tables). First mention (file order, then offset) wins.
     */
    static List<Table> relevantTables(List<Table> tables, List<String> terms, List<Entry> entries,
                                      Map<Table, String> foundIn) {
        Map<String, Mention> mentioned = new HashMap<>();
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            String filename = entry.relPath().substring(entry.relPath().lastIndexOf('/') + 1);
            Matcher words = IDENTIFIER.matcher(entry.content());
            while (words.find()) {
                String word = words.group();
                if (word.length() <= 128) {
                    mentioned.putIfAbsent(word.toLowerCase(Locale.ROOT),
                            new Mention(index, words.start(), word, filename));
                }
            }
        }

        List<String> lowerTerms = terms.stream().map(t -> t.toLowerCase(Locale.ROOT)).toList();
        List<Table> relevant = new ArrayList<>();
        for (Table table : tables) {
            String name = table.name().toLowerCase(Locale.ROOT);
            if (lowerTerms.stream().anyMatch(name::contains)) {
                relevant.add(table);
                foundIn.put(table, "Tabellenname enthält Suchbegriff");
                continue;
            }
            List<String> names = new ArrayList<>(List.of(table.name()));
            if (table.isView() && !table.viewOf().isEmpty()) {
                for (String base : table.viewOf().split(",\\s*")) {
                    names.add(base.substring(base.lastIndexOf('.') + 1));
                }
            }
            Mention first = null;
            for (String candidate : names) {
                Mention found = mentioned.get(candidate.toLowerCase(Locale.ROOT));
                if (found != null && (first == null || found.fileIndex() < first.fileIndex()
                        || (found.fileIndex() == first.fileIndex() && found.offset() < first.offset()))) {
                    first = found;
                }
            }
            if (first != null) {
                relevant.add(table);
                foundIn.put(table, first.word().equalsIgnoreCase(table.name())
                        ? "verwendet in `" + first.fileName() + "`"
                        : "Basistabelle " + first.word().toUpperCase(Locale.ROOT)
                        + " verwendet in `" + first.fileName() + "`");
            }
        }
        return relevant;
    }

    private static Group coreGroup(Table t, String foundIn, String prefix) {
        List<String> description = new ArrayList<>();
        if (!t.label().isEmpty() || !t.comment().isEmpty()) {
            description.add(String.join(" – ", nonEmpty(t.label(), t.comment())));
            description.add("");
        }
        description.add("- Gefunden: " + foundIn + " · DDL: `" + t.source().getFileName() + "`");
        if (t.isView()) {
            boolean untyped = t.columns().stream().anyMatch(c -> c.type().isEmpty());
            description.add("- View auf: " + (t.viewOf().isEmpty() ? "– (unbekannt)" : t.viewOf())
                    + (untyped ? " (Typ/Pflicht nicht in der DDL der View; „?“ = unbekannt)" : ""));
        }
        description.add("- Primärschlüssel: " + (t.primaryKey().isEmpty() ? "– (nicht deklariert)" : String.join(", ", t.primaryKey())));
        for (List<String> unique : t.uniqueIndexes()) {
            description.add("- Eindeutiger Index: " + String.join(", ", unique)
                    + (t.primaryKey().isEmpty() ? " (Ersatz für den Primärschlüssel, Spalte „Schlüsselart“ = UK)" : ""));
        }
        for (String fk : t.foreignKeys()) {
            description.add("- Fremdschlüssel: " + fk);
        }
        List<List<String>> rows = new ArrayList<>();
        for (Column c : t.columns()) {
            boolean unknown = c.type().isEmpty();
            String keyKind = t.primaryKey().contains(c.name()) ? "PK"
                    : t.primaryKey().isEmpty() && !t.uniqueIndexes().isEmpty()
                    && t.uniqueIndexes().getFirst().contains(c.name()) ? "UK" : "";
            rows.add(List.of(prefix + "." + c.name(), unknown ? "?" : c.type(), unknown ? "?" : c.nullable() ? "nein" : "ja",
                    cell(c.defaultValue()), keyKind, cell(c.label()), cell(c.comment())));
        }
        return new Group((t.isView() ? "View " : "Tabelle ") + t.qualifiedName(), description,
                List.of("Schlüssel", "Typ", "Pflicht", "Default", "Schlüsselart", "Bezeichnung", "Kommentar"), rows);
    }

    private static Group secondaryGroup(Table t, String foundIn, String prefix) {
        List<List<String>> rows = new ArrayList<>();
        for (Column c : t.columns()) {
            rows.add(List.of(prefix + "." + c.name(), c.type().isEmpty() ? "?" : c.type(), cell(c.label()), cell(c.comment())));
        }
        return new Group((t.isView() ? "View " : "Tabelle ") + t.qualifiedName(),
                List.of("- Gefunden: " + foundIn + " · DDL: `" + t.source().getFileName() + "`"),
                List.of("Spalte", "Typ", "Bezeichnung", "Kommentar"), rows);
    }

    /** Fields of the data structures among the collected files; the key is {@code Klasse.Feld}. */
    static Sources fromCode(List<Entry> entries) {
        List<Group> groups = new ArrayList<>();
        for (Entry e : entries) {
            if (e.phase() != Phase.MODEL || !e.relPath().endsWith(".java") || ENUM.matcher(e.content()).find()) {
                continue;
            }
            List<String[]> fields = fields(e.content());
            if (fields.isEmpty()) {
                continue;
            }
            String cls = e.relPath().substring(e.relPath().lastIndexOf('/') + 1).replaceFirst("\\.java$", "");
            boolean anyColumn = fields.stream().anyMatch(f -> !f[2].isEmpty());
            List<List<String>> rows = new ArrayList<>();
            for (String[] f : fields) {
                String type = f[0].replace("|", "\\|");
                rows.add(anyColumn ? List.of(cls + "." + f[1], type, f[2]) : List.of(cls + "." + f[1], type));
            }
            groups.add(new Group("Klasse " + cls, List.of("`" + e.relPath() + "`"),
                    anyColumn ? List.of("Schlüssel", "Typ", "Spalte (@Column)") : List.of("Schlüssel", "Typ"), rows));
        }
        int count = groups.stream().mapToInt(g -> g.rows().size()).sum();
        String summary = groups.isEmpty() ? "keine Attribute gefunden"
                : "%d Attribute aus %d Datenstrukturen im Code".formatted(count, groups.size());
        return new Sources(groups, List.of(), false, summary, List.of());
    }

    /**
     * The section {@code {{INVENTORY}}} of the task: "Soll Read" (or, without Read, the source attributes without
     * numbers), the secondary tables, "Soll Process" (target model, or the source attributes if the entity has no
     * target model file) and, with Write, a note that Write has no target list.
     *
     * @param target       null if Process is not selected
     * @param targetFolder the target model folder (for the note if no file matched)
     */
    static Inventory inventory(Sources sources, boolean read, boolean process, boolean write,
                               TargetModel.Result target, Path targetFolder) {
        StringBuilder md = new StringBuilder();
        Map<String, Integer> counts = new LinkedHashMap<>();
        List<String> summary = new ArrayList<>();
        if (read || process) {
            int rows = sources.coreRows();
            String unit = sources.fromDdl() ? (sources.core().size() == 1 ? "Tabelle" : "Tabellen")
                    : (sources.core().size() == 1 ? "Datenstruktur" : "Datenstrukturen");
            if (read) {
                md.append("### ").append(READ_TITLE).append(" – ").append(rows(rows, "R")).append("\n\n");
                counts.put("Read", rows);
                summary.add(READ_TITLE + ": " + lines(rows) + " aus " + sources.core().size() + " " + unit);
            } else {
                md.append("### ").append(SOURCES_TITLE).append("\n\n");
            }
            if (!sources.fromDdl()) {
                md.append(sources.core().isEmpty()
                        ? "Keine DDL angegeben und keine Datenstrukturen mit Feldern gefunden.\n\n"
                        : "Quelle: Datenstrukturen im Code (keine DDL angegeben oder keine passende Tabelle).\n\n");
            }
            int next = 1;
            for (Group g : sources.core()) {
                md.append("#### ").append(g.heading()).append(" – ")
                        .append(read ? rows(g.rows().size(), "R", next) : lines(g.rows().size())).append("\n\n");
                appendGroup(md, g, read ? "R" : null, next);
                next += g.rows().size();
            }
            if (!sources.secondary().isEmpty()) {
                md.append("### ").append(SECONDARY_TITLE).append("\n\n")
                        .append("Diese Tabellen kommen im Code vor, gehören aber nicht zur Entität. Führe eine Spalte ")
                        .append("nur als Hilfswert auf, wenn der Code sie nachweislich liest.\n\n");
                for (Group g : sources.secondary()) {
                    md.append("#### ").append(g.heading()).append("\n\n");
                    appendGroup(md, g, null, 0);
                }
            }
        }
        if (process) {
            if (target.file() != null) {
                int rows = target.attributes().size();
                md.append("### ").append(PROCESS_TITLE).append(" – ").append(rows(rows, "P")).append("\n\n")
                        .append("Quelle: Zielmodell, Entität **").append(target.entity()).append("** aus `")
                        .append(target.file().getFileName()).append("`.\n\n");
                boolean details = target.hasDetails();
                md.append(details ? "| Nr | Schlüssel | Angaben im Zielmodell |\n|---|---|---|\n" : "| Nr | Schlüssel |\n|---|---|\n");
                for (int i = 0; i < rows; i++) {
                    String name = target.attributes().get(i);
                    md.append("| P").append(i + 1).append(" | ").append(cell(name));
                    md.append(details ? " | " + cell(target.details().getOrDefault(name, "")) + " |\n" : " |\n");
                }
                md.append('\n');
                counts.put("Process", rows);
            } else {
                int rows = sources.coreRows();
                md.append("### ").append(PROCESS_TITLE).append(" – ").append(rows(rows, "P")).append("\n\n")
                        .append("Im Zielmodell-Ordner `").append(targetFolder).append("` gibt es keine JSON-Datei zu ")
                        .append("dieser Entität. Die Liste nennt deshalb die Quellattribute der Kerntabellen; das ")
                        .append("fehlende Zielmodell ist ein OPL-Punkt.\n\n");
                md.append("| Nr | Schlüssel | Typ |\n|---|---|---|\n");
                int n = 1;
                for (Group g : sources.core()) {
                    for (List<String> row : g.rows()) {
                        md.append("| P").append(n++).append(" | ").append(row.get(0)).append(" | ").append(row.get(1)).append(" |\n");
                    }
                }
                md.append('\n');
                counts.put("Process", rows);
            }
            summary.add(PROCESS_TITLE + ": " + lines(counts.get("Process")));
        }
        if (write) {
            md.append(WRITE_NOTE).append('\n');
        }
        return new Inventory(md.toString().strip(), counts,
                summary.isEmpty() ? "keine (Write hat keine Soll-Liste)" : String.join(" · ", summary));
    }

    /** Description and table of a group; {@code prefix} null = without the column "Nr". */
    private static void appendGroup(StringBuilder md, Group g, String prefix, int first) {
        for (String line : g.description()) {
            md.append(line).append('\n');
        }
        md.append('\n');
        md.append(prefix == null ? "|" : "| Nr |");
        g.header().forEach(h -> md.append(' ').append(h).append(" |"));
        md.append('\n').append(prefix == null ? "|" : "|---|");
        g.header().forEach(h -> md.append("---|"));
        md.append('\n');
        int n = first;
        for (List<String> row : g.rows()) {
            md.append(prefix == null ? "|" : "| " + prefix + n++ + " |");
            row.forEach(c -> md.append(' ').append(c).append(" |"));
            md.append('\n');
        }
        md.append('\n');
    }

    /** "8 Zeilen (R1–R8)". */
    private static String rows(int count, String prefix) {
        return rows(count, prefix, 1);
    }

    private static String rows(int count, String prefix, int first) {
        if (count == 0) {
            return lines(0);
        }
        int last = first + count - 1;
        return lines(count) + " (" + prefix + first + (count == 1 ? "" : "–" + prefix + last) + ")";
    }

    private static String lines(int count) {
        return count + (count == 1 ? " Zeile" : " Zeilen");
    }

    private static String names(List<Table> tables) {
        return String.join(", ", tables.stream().map(Table::name).toList());
    }

    private static List<Table> concat(List<Table> a, List<Table> b) {
        List<Table> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    /** {type, name, column} per instance field, or per record component. */
    private static List<String[]> fields(String content) {
        List<String[]> fields = new ArrayList<>();
        Matcher record = RECORD.matcher(content);
        if (record.find()) {
            for (String component : record.group(2).split(",(?![^<]*>)")) {
                String[] parts = component.strip().replaceAll("@\\w+(\\([^)]*\\))?\\s*", "").split("\\s+");
                if (parts.length >= 2) {
                    fields.add(new String[]{parts[parts.length - 2], parts[parts.length - 1], ""});
                }
            }
            return fields;
        }
        String pendingColumn = "";
        for (String line : content.split("\n")) {
            Matcher column = COLUMN_NAME.matcher(line);
            if (column.find()) {
                pendingColumn = column.group(1);
            }
            Matcher field = FIELD.matcher(line);
            if (field.find()) {
                fields.add(new String[]{field.group(1).replaceAll("\\s+", ""), field.group(2), pendingColumn});
                pendingColumn = "";
            } else if (!line.strip().startsWith("@") && !line.isBlank()) {
                pendingColumn = ""; // the annotation belonged to something else
            }
        }
        return fields;
    }

    private static List<String> nonEmpty(String... values) {
        return java.util.Arrays.stream(values).filter(v -> !v.isEmpty()).toList();
    }

    private static String cell(String value) {
        return value.isEmpty() ? "" : value.replace("|", "\\|").replace("\n", " ");
    }
}
