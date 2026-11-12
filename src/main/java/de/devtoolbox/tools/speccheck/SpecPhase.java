package de.devtoolbox.tools.speccheck;

import java.util.List;

/** The three phases of a specification with the names the skill templates use for headings and table columns. */
public enum SpecPhase {

    READ("Phase Read", "Soll Read", "Read", List.of("attribut"), List.of("einlesen"), "SR"),
    PROCESS("Phase Process", "Soll Process", "Process", List.of("zielattribut", "attribut"), List.of("mapping"), "SP"),
    WRITE("Phase Write", null, "Write", List.of("zielfeld"), List.of("schreiben"), "SW");

    private final String section;
    private final String sollHeading;
    private final String label;
    private final List<String> keyColumns;
    private final List<String> ruleColumns;
    private final String ruleNrPrefix;

    SpecPhase(String section, String sollHeading, String label, List<String> keyColumns, List<String> ruleColumns,
              String ruleNrPrefix) {
        this.section = section;
        this.sollHeading = sollHeading;
        this.label = label;
        this.keyColumns = keyColumns;
        this.ruleColumns = ruleColumns;
        this.ruleNrPrefix = ruleNrPrefix;
    }

    /** Heading (level 2) of the phase in the result, e.g. "Phase Read". */
    public String section() {
        return section;
    }

    /** Heading of the target list in the task ("Soll Read"), or null if the phase has none. */
    public String sollHeading() {
        return sollHeading;
    }

    /** Prefix of the numbers of the sentence rules (SR, SP, SW). */
    public String ruleNrPrefix() {
        return ruleNrPrefix;
    }

    public String label() {
        return label;
    }

    /** Column of the key (Attribut, Zielattribut, Zielfeld) in the attribute table, or -1. */
    public int keyColumn(MdTable table) {
        return table.col(keyColumns.toArray(String[]::new));
    }

    /** Column of the rule text of the attribute table (Einlesen, Mapping, Schreiben), or -1. */
    public int ruleColumn(MdTable table) {
        return table.col(ruleColumns.toArray(String[]::new));
    }
}
