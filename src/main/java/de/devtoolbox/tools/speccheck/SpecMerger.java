package de.devtoolbox.tools.speccheck;

import de.devtoolbox.tools.speccheck.Spec.PhaseTables;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Merges several results (main file, continuation, corrections) into one {@link Spec}: attribute rows by key (the
 * later file wins), sentence rules appended and renumbered, open points appended and renumbered with the references
 * of each file adjusted.
 */
public final class SpecMerger {

    private static final Pattern OPL_REF = Pattern.compile("OPL-(\\d+)");
    private static final Pattern DIGITS = Pattern.compile("(\\d+)");

    private SpecMerger() {
    }

    /** Whether a row is a helper value (number H1, H2, … or key suffix "(Hilfswert)"). */
    public static boolean isHelper(String nr, String keyCell) {
        return nr.matches("(?i)H\\d+") || keyCell.toLowerCase().contains("(hilfswert)");
    }

    public static Spec merge(List<Spec> specs, TaskLists soll) {
        // open points: new numbers per file
        List<Map<Integer, Integer>> oplMaps = new ArrayList<>();
        List<String> oplHeader = null;
        List<List<String>> oplRows = new ArrayList<>();
        int next = 1;
        for (Spec spec : specs) {
            Map<Integer, Integer> map = new LinkedHashMap<>();
            if (spec.opl() != null) {
                if (oplHeader == null) {
                    oplHeader = spec.opl().header();
                }
                for (List<String> row : spec.opl().rows()) {
                    Matcher m = DIGITS.matcher(MdTable.cell(row, 0));
                    if (!m.find()) {
                        continue;
                    }
                    int old = Integer.parseInt(m.group(1));
                    int renumbered = next++;
                    map.putIfAbsent(old, renumbered);
                    List<String> copy = new ArrayList<>(row);
                    copy.set(0, "OPL-" + renumbered);
                    oplRows.add(copy);
                }
            }
            oplMaps.add(map);
        }

        Map<SpecPhase, PhaseTables> phases = new EnumMap<>(SpecPhase.class);
        for (SpecPhase phase : SpecPhase.values()) {
            MdTable attrs = mergeAttrs(phase, specs, oplMaps, soll);
            MdTable rules = mergeRules(phase, specs, oplMaps);
            if (attrs != null || rules != null) {
                phases.put(phase, new PhaseTables(attrs, rules));
            }
        }

        List<String> summary = new ArrayList<>();
        String status = null;
        for (Spec spec : specs) {
            List<String> text = spec.summary().stream()
                    .filter(l -> !l.replaceAll("[\\s*_`>-]", "").toLowerCase().startsWith("kennzahlen"))
                    .toList();
            String joined = String.join("\n", text).strip();
            if (!joined.isEmpty()) {
                if (!summary.isEmpty()) {
                    summary.add("");
                }
                summary.addAll(joined.lines().toList());
            }
            if (spec.status() != null) {
                status = spec.status();
            }
        }
        return new Spec(summary, mergeFlow(specs), phases,
                oplHeader == null ? null : new MdTable(oplHeader, oplRows), status);
    }

    private static MdTable mergeFlow(List<Spec> specs) {
        List<String> header = null;
        Set<List<String>> seen = new LinkedHashSet<>();
        for (Spec spec : specs) {
            if (spec.flow() != null) {
                header = header == null ? spec.flow().header() : header;
                for (List<String> row : spec.flow().rows()) {
                    seen.add(align(spec.flow(), header, row));
                }
            }
        }
        return header == null ? null : new MdTable(header, new ArrayList<>(seen));
    }

    /** One attribute row, optionally followed by its helper rows. */
    private record Group(String key, List<List<String>> rows, boolean hasHelpers) {
    }

    private static MdTable mergeAttrs(SpecPhase phase, List<Spec> specs, List<Map<Integer, Integer>> oplMaps,
                                      TaskLists soll) {
        List<String> header = null;
        Map<String, List<Group>> merged = new LinkedHashMap<>();
        for (int i = 0; i < specs.size(); i++) {
            PhaseTables tables = specs.get(i).phases().get(phase);
            if (tables == null || tables.attrs() == null) {
                continue;
            }
            MdTable t = tables.attrs();
            header = header == null ? t.header() : header;
            int nrCol = t.colExact("nr");
            int srcKey = phase.keyColumn(t);
            Map<String, List<Group>> local = new LinkedHashMap<>();
            Group last = null;
            int index = 0;
            for (List<String> raw : t.rows()) {
                List<String> row = remapOpl(align(t, header, raw), header, i, oplMaps);
                String keyCell = MdTable.cell(raw, srcKey);
                // without a key column rows cannot be matched; each stays as it is
                String key = srcKey < 0 ? "#" + i + "-" + index++ : TaskLists.key(keyCell);
                if (isHelper(MdTable.cell(raw, nrCol), keyCell) && last != null) {
                    last.rows().add(row);
                    last = new Group(last.key(), last.rows(), true);
                    local.get(last.key()).set(local.get(last.key()).size() - 1, last);
                } else {
                    last = new Group(key, new ArrayList<>(List.of(row)), false);
                    local.computeIfAbsent(key, k -> new ArrayList<>()).add(last);
                }
            }
            for (Map.Entry<String, List<Group>> e : local.entrySet()) {
                List<Group> old = merged.get(e.getKey());
                List<Group> now = new ArrayList<>(e.getValue());
                if (old != null && now.stream().noneMatch(Group::hasHelpers)) {
                    for (Group g : old) {
                        if (g.hasHelpers()) {
                            now.getFirst().rows().addAll(g.rows().subList(1, g.rows().size()));
                            break;
                        }
                    }
                }
                merged.put(e.getKey(), now);
            }
        }
        if (header == null) {
            return null;
        }
        List<List<String>> rows = new ArrayList<>();
        Set<String> done = new LinkedHashSet<>();
        for (TaskLists.Soll s : soll.of(phase)) {
            if (merged.containsKey(s.key()) && done.add(s.key())) {
                merged.get(s.key()).forEach(g -> rows.addAll(g.rows()));
            }
        }
        for (Map.Entry<String, List<Group>> e : merged.entrySet()) {
            if (done.add(e.getKey())) {
                e.getValue().forEach(g -> rows.addAll(g.rows()));
            }
        }
        return new MdTable(header, rows);
    }

    private static MdTable mergeRules(SpecPhase phase, List<Spec> specs, List<Map<Integer, Integer>> oplMaps) {
        List<String> header = null;
        List<List<String>> rows = new ArrayList<>();
        Set<List<String>> seen = new LinkedHashSet<>();
        String prefix = null;
        for (int i = 0; i < specs.size(); i++) {
            PhaseTables tables = specs.get(i).phases().get(phase);
            if (tables == null || tables.rules() == null) {
                continue;
            }
            MdTable t = tables.rules();
            header = header == null ? t.header() : header;
            MdTable head = new MdTable(header, List.of());
            int nrCol = head.colExact("nr");
            for (List<String> raw : t.rows()) {
                List<String> row = remapOpl(align(t, header, raw), header, i, oplMaps);
                if (prefix == null) {
                    Matcher m = Pattern.compile("^([A-Za-z]+)\\d+$").matcher(MdTable.cell(row, nrCol));
                    if (m.matches()) {
                        prefix = m.group(1);
                    }
                }
                List<String> identity = new ArrayList<>(row);
                if (nrCol >= 0) {
                    identity.set(nrCol, "");
                }
                if (seen.add(identity)) {
                    rows.add(row);
                }
            }
        }
        if (header == null) {
            return null;
        }
        int nrCol = new MdTable(header, List.of()).colExact("nr");
        if (nrCol >= 0) {
            String p = prefix == null ? phase.ruleNrPrefix() : prefix;
            for (int n = 0; n < rows.size(); n++) {
                rows.get(n).set(nrCol, p + (n + 1));
            }
        }
        return new MdTable(header, rows);
    }

    /** The row's cells in the column order of {@code target} (matched by header name; missing columns empty). */
    private static List<String> align(MdTable source, List<String> target, List<String> row) {
        if (source.header().equals(target)) {
            return new ArrayList<>(row);
        }
        List<String> out = new ArrayList<>();
        for (int c = 0; c < target.size(); c++) {
            int from = source.header().indexOf(target.get(c));
            out.add(from < 0 ? "" : MdTable.cell(row, from));
        }
        return out;
    }

    private static List<String> remapOpl(List<String> row, List<String> header, int fileIndex,
                                         List<Map<Integer, Integer>> oplMaps) {
        int col = new MdTable(header, List.of()).colExact("opl");
        if (col < 0 || col >= row.size()) {
            return row;
        }
        Matcher m = OPL_REF.matcher(row.get(col));
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            int old = Integer.parseInt(m.group(1));
            String replacement = m.group();
            for (int j = fileIndex; j >= 0; j--) {
                Integer mapped = oplMaps.get(j).get(old);
                if (mapped != null) {
                    replacement = "OPL-" + mapped;
                    break;
                }
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        row.set(col, sb.toString());
        return row;
    }
}
