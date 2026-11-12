package de.devtoolbox.tools.speccheck;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The target lists ("Soll Read", "Soll Process") of a task file: tables with the columns Nr and Schlüssel below a
 * heading that starts with the list name. A list ends at the next heading of level 1 to 3 (e.g. "Nebentabellen").
 */
public final class TaskLists {

    /** One target line. */
    public record Soll(String nr, String key) {
    }

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*?)\\s*$");

    private final Map<SpecPhase, List<Soll>> lists;

    private TaskLists(Map<SpecPhase, List<Soll>> lists) {
        this.lists = lists;
    }

    /** Merges the lists of one or more task texts (a key listed twice is kept once). */
    public static TaskLists parse(List<String> texts) {
        Map<SpecPhase, Map<String, Soll>> byKey = new EnumMap<>(SpecPhase.class);
        for (String text : texts) {
            SpecPhase current = null;
            List<String> block = new ArrayList<>();
            for (String line : text.replace("﻿", "").lines().toList()) {
                Matcher m = HEADING.matcher(line);
                if (m.matches()) {
                    collect(current, block, byKey);
                    block = new ArrayList<>();
                    current = m.group(1).length() <= 3 ? phaseOf(m.group(2)) : current;
                    continue;
                }
                block.add(line);
            }
            collect(current, block, byKey);
        }
        Map<SpecPhase, List<Soll>> lists = new EnumMap<>(SpecPhase.class);
        byKey.forEach((phase, rows) -> lists.put(phase, List.copyOf(rows.values())));
        return new TaskLists(lists);
    }

    private static SpecPhase phaseOf(String heading) {
        String h = heading.toLowerCase(Locale.ROOT);
        for (SpecPhase phase : SpecPhase.values()) {
            if (phase.sollHeading() != null && h.startsWith(phase.sollHeading().toLowerCase(Locale.ROOT))) {
                return phase;
            }
        }
        return null;
    }

    private static void collect(SpecPhase phase, List<String> block, Map<SpecPhase, Map<String, Soll>> byKey) {
        if (phase == null) {
            return;
        }
        for (MdTable table : MdTable.parseAll(block)) {
            int nr = table.colExact("nr");
            int key = table.col("schlüssel", "schluessel");
            if (nr < 0 || key < 0) {
                continue;
            }
            Map<String, Soll> rows = byKey.computeIfAbsent(phase, p -> new LinkedHashMap<>());
            for (List<String> row : table.rows()) {
                String k = key(MdTable.cell(row, key));
                if (!k.isEmpty()) {
                    rows.putIfAbsent(k, new Soll(MdTable.cell(row, nr), k));
                }
            }
        }
    }

    /** The target lines of {@code phase} in task order (empty if the task has no list for it). */
    public List<Soll> of(SpecPhase phase) {
        return lists.getOrDefault(phase, List.of());
    }

    /** A key without backticks, the "(Hilfswert)" suffix and surrounding blanks. */
    public static String key(String cell) {
        return cell.replace("`", "").replaceAll("(?i)\\(\\s*Hilfswert\\s*\\)", "").strip();
    }
}
