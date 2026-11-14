package de.devtoolbox.tools.speccheck;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parsed result of Copilot (or the merged state of several). Sections are the level-2 headings of the outline;
 * per phase the first table is the attribute/field table and the second the sentence rules.
 *
 * @param summary summary text without the status line
 * @param flow    table of the "Ablaufübersicht", or null
 * @param opl     the open-points table, or null
 * @param status  text after "Status:" in the summary, or null if there is no status line
 */
public record Spec(List<String> summary, MdTable flow, Map<SpecPhase, PhaseTables> phases, MdTable opl,
                   String status) {

    /** The two tables of one phase; either may be null. */
    public record PhaseTables(MdTable attrs, MdTable rules) {
    }

    private static final Pattern STATUS = Pattern.compile("^[\\s*_>`-]*Status:\\s*(.*?)[\\s*_`]*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADING2 = Pattern.compile("^##\\s+(.*?)\\s*#*\\s*$");

    public static Spec parse(String text) {
        List<String> summary = new ArrayList<>();
        String status = null;
        MdTable flow = null;
        MdTable opl = null;
        Map<SpecPhase, PhaseTables> phases = new EnumMap<>(SpecPhase.class);

        List<String> titles = new ArrayList<>();
        List<List<String>> bodies = new ArrayList<>();
        for (String line : text.replace("﻿", "").lines().toList()) {
            Matcher m = HEADING2.matcher(line);
            if (m.matches()) {
                titles.add(m.group(1));
                bodies.add(new ArrayList<>());
            } else if (!titles.isEmpty()) {
                bodies.getLast().add(line);
            }
        }
        for (int i = 0; i < titles.size(); i++) {
            String name = titles.get(i).toLowerCase(Locale.ROOT);
            List<String> lines = bodies.get(i);
            if (name.startsWith("zusammenfassung")) {
                for (String line : lines) {
                    Matcher m = STATUS.matcher(line);
                    if (status == null && m.matches()) {
                        status = m.group(1);
                    } else {
                        summary.add(line);
                    }
                }
            } else if (name.startsWith("ablauf")) {
                flow = flow == null ? table(lines, 0) : flow;
            } else if (name.startsWith("offene-punkte") || name.startsWith("opl")) {
                opl = opl == null ? table(lines, 0) : opl;
            } else {
                for (SpecPhase phase : SpecPhase.values()) {
                    if (name.startsWith(phase.section().toLowerCase(Locale.ROOT)) && !phases.containsKey(phase)) {
                        phases.put(phase, phaseTables(lines));
                    }
                }
            }
        }
        return new Spec(summary, flow, phases, opl, status);
    }

    private static PhaseTables phaseTables(List<String> lines) {
        List<MdTable> tables = MdTable.parseAll(lines);
        return new PhaseTables(tables.isEmpty() ? null : tables.getFirst(),
                tables.size() < 2 ? null : tables.get(1));
    }

    private static MdTable table(List<String> lines, int index) {
        List<MdTable> tables = MdTable.parseAll(lines);
        return index < tables.size() ? tables.get(index) : null;
    }

    /** True if {@code status} says VOLLSTÄNDIG, false for UNVOLLSTÄNDIG, null if not recognizable. */
    public static Boolean complete(String status) {
        if (status == null) {
            return null;
        }
        String s = status.toUpperCase(Locale.ROOT).replace("Ä", "AE");
        if (s.contains("UNVOLLSTAENDIG")) {
            return false;
        }
        return s.contains("VOLLSTAENDIG") ? Boolean.TRUE : null;
    }
}
