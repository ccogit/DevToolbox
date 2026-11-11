package de.devtoolbox.tools.codecollector;

import de.devtoolbox.tools.codecollector.DdlReader.Column;
import de.devtoolbox.tools.codecollector.DdlReader.Table;
import de.devtoolbox.tools.codecollector.MarkdownWriter.Entry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The attribute directory of the business entity – the checklist the AI works through attribute by attribute.
 * Primary source is the DDL of the legacy database; without (relevant) DDL the fields of the collected data
 * structures (TOs, POs, DTOs, entities) are used.
 */
final class AttributeIndex {

    private static final Pattern FIELD = Pattern.compile(
            "^\\s*(?:private|protected|public)\\s+(?!static\\b)(?:final\\s+|transient\\s+)*([\\w.$]+(?:<[^;=]*>)?(?:\\[])*)\\s+(\\w+)\\s*(?:=[^;]*)?;");
    private static final Pattern RECORD = Pattern.compile("\\brecord\\s+(\\w+)\\s*(?:<[^>]*>)?\\s*\\(([^)]*)\\)");
    private static final Pattern ENUM = Pattern.compile("\\benum\\s+\\w+");
    private static final Pattern COLUMN_NAME = Pattern.compile("@Column\\s*\\([^)]*\\bname\\s*=\\s*\"([^\"]+)\"");

    /** Rendered Markdown and a short summary for the log. */
    record Result(String markdown, String summary) {
    }

    private AttributeIndex() {
    }

    /** Tables whose name contains a search term or that are named in any collected file (SQL, @Table, constants). */
    static List<Table> relevantTables(List<Table> tables, List<String> terms, List<Entry> entries,
                                      Map<Table, String> foundIn) {
        List<Table> relevant = new ArrayList<>();
        for (Table table : tables) {
            String name = table.name().toLowerCase(Locale.ROOT);
            if (terms.stream().anyMatch(t -> name.contains(t.toLowerCase(Locale.ROOT)))) {
                relevant.add(table);
                foundIn.put(table, "Tabellenname enthält Suchbegriff");
                continue;
            }
            // a view is also relevant if the code reads its base table directly
            List<String> names = new ArrayList<>(List.of(table.name()));
            if (table.isView() && !table.viewOf().isEmpty()) {
                for (String base : table.viewOf().split(",\\s*")) {
                    names.add(base.substring(base.lastIndexOf('.') + 1));
                }
            }
            Pattern word = Pattern.compile("(?i)(?<![\\w$#@])(?:" + String.join("|", names.stream().map(Pattern::quote).toList())
                    + ")(?![\\w$#@])");
            for (Entry entry : entries) {
                Matcher m = word.matcher(entry.content());
                if (m.find()) {
                    relevant.add(table);
                    String file = entry.relPath().substring(entry.relPath().lastIndexOf('/') + 1);
                    foundIn.put(table, m.group().equalsIgnoreCase(table.name()) ? "verwendet in `" + file + "`"
                            : "Basistabelle " + m.group().toUpperCase(Locale.ROOT) + " verwendet in `" + file + "`");
                    break;
                }
            }
        }
        return relevant;
    }

    static Result fromDdl(List<Table> tables, Map<Table, String> foundIn) {
        StringBuilder md = new StringBuilder();
        md.append("Quelle: **DDL der Altdatenbank** (").append(tables.size()).append(" Tabellen). ")
                .append("Jede Spalte ist ein Attribut, das in der Spezifikation betrachtet werden muss.\n");
        int columns = 0;
        for (Table t : tables) {
            md.append("\n### ").append(t.isView() ? "View " : "Tabelle ").append(t.qualifiedName()).append("\n\n");
            if (!t.label().isEmpty() || !t.comment().isEmpty()) {
                md.append(String.join(" – ", nonEmpty(t.label(), t.comment()))).append("\n\n");
            }
            md.append("- Gefunden: ").append(foundIn.get(t)).append(" · DDL: `").append(t.source().getFileName()).append("`\n");
            if (t.isView()) {
                boolean untyped = t.columns().stream().anyMatch(c -> c.type().isEmpty());
                md.append("- View auf: ").append(t.viewOf().isEmpty() ? "– (unbekannt)" : t.viewOf())
                        .append(untyped ? " (Typ/Pflicht nicht in der DDL der View; „?“ = unbekannt)" : "").append('\n');
            }
            md.append("- Primärschlüssel: ").append(t.primaryKey().isEmpty() ? "– (nicht deklariert)" : String.join(", ", t.primaryKey())).append('\n');
            for (List<String> unique : t.uniqueIndexes()) {
                md.append("- Eindeutiger Index: ").append(String.join(", ", unique))
                        .append(t.primaryKey().isEmpty() ? " (Ersatz für den Primärschlüssel, Spalte „Schlüssel“ = UK)" : "")
                        .append('\n');
            }
            for (String fk : t.foreignKeys()) {
                md.append("- Fremdschlüssel: ").append(fk).append('\n');
            }
            md.append("\n| Attribut (Spalte) | Typ | Pflicht | Default | Schlüssel | Bezeichnung | Kommentar |\n")
                    .append("|---|---|---|---|---|---|---|\n");
            for (Column c : t.columns()) {
                boolean unknown = c.type().isEmpty();
                md.append("| ").append(c.name()).append(" | ").append(unknown ? "?" : c.type()).append(" | ")
                        .append(unknown ? "?" : c.nullable() ? "nein" : "ja").append(" | ").append(cell(c.defaultValue())).append(" | ")
                        .append(t.primaryKey().contains(c.name()) ? "PK"
                                : t.primaryKey().isEmpty() && !t.uniqueIndexes().isEmpty()
                                && t.uniqueIndexes().getFirst().contains(c.name()) ? "UK" : "").append(" | ")
                        .append(cell(c.label())).append(" | ").append(cell(c.comment())).append(" |\n");
                columns++;
            }
        }
        return new Result(md.toString(), "%d Tabellen, %d Attribute aus DDL".formatted(tables.size(), columns));
    }

    /** Fields of the data structures among the collected files. */
    static Result fromCode(List<Entry> entries) {
        Map<String, List<String[]>> byClass = new LinkedHashMap<>();
        for (Entry e : entries) {
            if (e.phase() != Phase.MODEL || !e.relPath().endsWith(".java") || ENUM.matcher(e.content()).find()) {
                continue;
            }
            List<String[]> fields = fields(e.content());
            if (!fields.isEmpty()) {
                byClass.put(e.relPath(), fields);
            }
        }
        if (byClass.isEmpty()) {
            return new Result("Keine DDL angegeben und keine Datenstrukturen mit Feldern gefunden.\n", "keine Attribute gefunden");
        }
        StringBuilder md = new StringBuilder();
        md.append("Quelle: **Datenstrukturen im Code** (TOs, POs, DTOs, Entities – ").append(byClass.size())
                .append(" Klassen; keine DDL angegeben oder keine passende Tabelle). ")
                .append("Jedes Feld ist ein Attribut, das in der Spezifikation betrachtet werden muss.\n");
        int count = 0;
        for (Map.Entry<String, List<String[]>> cls : byClass.entrySet()) {
            String path = cls.getKey();
            md.append("\n### ").append(path.substring(path.lastIndexOf('/') + 1).replaceFirst("\\.java$", ""))
                    .append("\n\n`").append(path).append("`\n\n");
            boolean anyColumn = cls.getValue().stream().anyMatch(f -> !f[2].isEmpty());
            md.append(anyColumn ? "| Attribut | Typ | Spalte (@Column) |\n|---|---|---|\n" : "| Attribut | Typ |\n|---|---|\n");
            for (String[] f : cls.getValue()) {
                md.append("| ").append(f[1]).append(" | ").append(f[0].replace("|", "\\|"));
                md.append(anyColumn ? " | " + f[2] + " |\n" : " |\n");
                count++;
            }
        }
        return new Result(md.toString(), "%d Attribute aus %d Datenstrukturen im Code".formatted(count, byClass.size()));
    }

    /** The target attributes of the entity (for Process), or a note that no target model file matched. */
    static String targetSection(TargetModel.Result target, java.nio.file.Path folder) {
        StringBuilder md = new StringBuilder("### Zielattribute (Zielmodell)\n\n");
        if (target.file() == null) {
            md.append("Im Zielmodell-Ordner `").append(folder).append("` gibt es keine JSON-Datei zu dieser Entität. ")
                    .append("Die Attributtabelle Process wird deshalb je Quellattribut aufgebaut; das fehlende ")
                    .append("Zielmodell ist ein OPL-Punkt.\n");
            return md.toString();
        }
        md.append("Quelle: **Zielmodell**, Entität **").append(target.entity()).append("** aus `")
                .append(target.file().getFileName()).append("` (")
                .append(target.attributes().size()).append(" Attribute). Jedes Zielattribut bekommt in der ")
                .append("Attributtabelle Process genau eine Zeile.\n\n");
        boolean details = target.hasDetails();
        md.append(details ? "| Nr | Zielattribut | Angaben im Zielmodell |\n|---|---|---|\n" : "| Nr | Zielattribut |\n|---|---|\n");
        for (int i = 0; i < target.attributes().size(); i++) {
            String name = target.attributes().get(i);
            md.append("| ").append(i + 1).append(" | ").append(cell(name));
            md.append(details ? " | " + cell(target.details().getOrDefault(name, "")) + " |\n" : " |\n");
        }
        return md.toString();
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
