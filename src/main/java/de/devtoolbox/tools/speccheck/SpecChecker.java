package de.devtoolbox.tools.speccheck;

import de.devtoolbox.tools.speccheck.Spec.PhaseTables;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The checks D1 to D7 on a (merged) {@link Spec}. Checks form and findability of evidence, not correctness. */
public final class SpecChecker {

    /**
     * One finding. {@code extra} is the evidence text for D4 (used in the correction prompt).
     *
     * @param notice true for hints that are no errors (open point without reference)
     */
    public record Finding(String check, SpecPhase phase, String nr, String key, String detail, String extra,
                          boolean notice) {
    }

    /** Key figures of one phase. */
    public record Stats(int soll, int delivered, int missing, int duplicate, int unknown, int helpers,
                        int withoutEvidence, int openPoints) {
    }

    /** All findings and key figures. {@code complete} is true if there is no D1 finding. */
    public record Result(List<Finding> findings, Map<SpecPhase, Stats> stats, int openPointCount,
                         boolean complete, String lastCompleteNr) {

        public List<Finding> of(String check) {
            return findings.stream().filter(f -> f.check().equals(check)).toList();
        }
    }

    private static final Pattern TAG = Pattern.compile("^[\\s*_`]*\\[(PRÜFEN|TECHNISCH|1:1)]", Pattern.CASE_INSENSITIVE);
    private static final Pattern NO_EVIDENCE_OK = Pattern.compile("nicht gelesen|nicht befüllt|nicht belegt",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ELLIPSIS = Pattern.compile(
            "…|\\.\\.\\.|\\busw\\b\\.?|\\banalog\\b|\\bwie oben\\b|\\bsiehe oben\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern OPL_REF = Pattern.compile("OPL-(\\d+)");
    private static final Pattern FILE_NAME = Pattern.compile(
            "([\\w\\-./\\\\]+\\.(?:sql|xml|properties|json|ya?ml|ddl|txt|csv|xsd|xslt|ftl|vm))\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLASS_METHOD = Pattern.compile(
            "(?:[a-z][\\w$]*\\.)*([A-Z][\\w$]*)[.#]([A-Za-z_$][\\w$]*)");
    private static final Pattern STEP_PREFIX = Pattern.compile("^\\s*\\d+\\s*:\\s*");

    private SpecChecker() {
    }

    /** One table row seen by the checks. */
    private record Row(SpecPhase phase, boolean rule, String nr, String key, boolean helper, MdTable table,
                       List<String> cells) {
    }

    public static Result check(Spec spec, TaskLists soll, CodeContext code) {
        List<Finding> findings = new ArrayList<>();
        Map<SpecPhase, Stats> stats = new EnumMap<>(SpecPhase.class);
        Map<SpecPhase, List<Row>> rowsByPhase = new EnumMap<>(SpecPhase.class);
        Map<SpecPhase, Set<Row>> badEvidence = new EnumMap<>(SpecPhase.class);
        Set<String> referenced = new LinkedHashSet<>();
        Map<SpecPhase, Set<String>> referencedByPhase = new EnumMap<>(SpecPhase.class);
        String lastCompleteNr = null;
        boolean firstGap = true;

        // D1
        for (SpecPhase phase : SpecPhase.values()) {
            PhaseTables tables = spec.phases().get(phase);
            List<TaskLists.Soll> target = soll.of(phase);
            if (tables == null && target.isEmpty()) {
                continue;
            }
            Map<String, String> nrByKey = new LinkedHashMap<>();
            target.forEach(s -> nrByKey.put(s.key(), s.nr()));
            List<Row> rows = new ArrayList<>();
            if (tables != null && tables.attrs() != null) {
                rows.addAll(rows(phase, tables.attrs(), false, nrByKey));
            }
            if (tables != null && tables.rules() != null) {
                rows.addAll(rows(phase, tables.rules(), true, nrByKey));
            }
            rowsByPhase.put(phase, rows);

            Map<String, Integer> counts = new LinkedHashMap<>();
            Map<String, Row> firstRow = new LinkedHashMap<>();
            int helpers = 0;
            for (Row r : rows) {
                if (r.rule()) {
                    continue;
                }
                if (r.helper()) {
                    helpers++;
                    continue;
                }
                counts.merge(r.key(), 1, Integer::sum);
                firstRow.putIfAbsent(r.key(), r);
            }
            int missing = 0;
            int duplicate = 0;
            int unknown = 0;
            if (tables == null || tables.attrs() == null || phase.keyColumn(tables.attrs()) < 0) {
                if (!target.isEmpty()) {
                    findings.add(new Finding("D1", phase, "", "", "Attributtabelle der Phase fehlt oder hat keine "
                            + "Schlüsselspalte", "", true));
                }
            }
            boolean gap = false;
            for (TaskLists.Soll s : target) {
                if (counts.getOrDefault(s.key(), 0) == 0) {
                    findings.add(new Finding("D1", phase, s.nr(), s.key(), "fehlend", "", false));
                    missing++;
                    gap = true;
                } else if (!gap && firstGap) {
                    lastCompleteNr = s.nr();
                }
            }
            if (gap) {
                firstGap = false;
            }
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                if (e.getValue() > 1) {
                    findings.add(new Finding("D1", phase, nrByKey.getOrDefault(e.getKey(), firstRow.get(e.getKey()).nr()),
                            e.getKey(), "doppelt (" + e.getValue() + "x)", "", false));
                    duplicate++;
                }
                if (phase != SpecPhase.WRITE && !target.isEmpty() && !nrByKey.containsKey(e.getKey())) {
                    findings.add(new Finding("D1", phase, firstRow.get(e.getKey()).nr(), e.getKey(),
                            "unbekannt (nicht in der Soll-Liste)", "", false));
                    unknown++;
                }
            }
            stats.put(phase, new Stats(target.size(), counts.size(), missing, duplicate, unknown, helpers, 0, 0));
        }
        boolean complete = findings.stream().noneMatch(f -> f.check().equals("D1") && !f.notice());

        // D2, D3, D4, D6 per row
        for (Map.Entry<SpecPhase, List<Row>> entry : rowsByPhase.entrySet()) {
            SpecPhase phase = entry.getKey();
            Set<Row> bad = new HashSet<>();
            badEvidence.put(phase, bad);
            Set<String> refs = new LinkedHashSet<>();
            referencedByPhase.put(phase, refs);
            for (Row r : entry.getValue()) {
                MdTable t = r.table();
                String ruleText = MdTable.cell(r.cells(), r.rule() ? t.col("regel") : phase.ruleColumn(t));
                String need = MdTable.cell(r.cells(), t.col("prüfbedarf", "pruefbedarf"));
                String evidence = MdTable.cell(r.cells(), t.col("beleg"));
                String ruleLabel = r.rule() ? "Regel" : switch (phase) {
                    case READ -> "Einlesen";
                    case PROCESS -> "Mapping";
                    case WRITE -> "Schreiben";
                };

                // D2
                if (ruleText.isBlank()) {
                    findings.add(new Finding("D2", phase, r.nr(), r.key(), "Pflichtzelle leer: " + ruleLabel, "", false));
                }
                if (need.isBlank()) {
                    findings.add(new Finding("D2", phase, r.nr(), r.key(), "Pflichtzelle leer: Prüfbedarf", "", false));
                }
                boolean noEvidence = evidence.isBlank() || evidence.strip().matches("[-–—]+");
                if (noEvidence && !NO_EVIDENCE_OK.matcher(ruleText).find()) {
                    findings.add(new Finding("D2", phase, r.nr(), r.key(), "Pflichtzelle leer: Beleg", "", false));
                    bad.add(r);
                }
                // D3
                if (!need.isBlank() && !TAG.matcher(need).find()) {
                    findings.add(new Finding("D3", phase, r.nr(), r.key(), "Prüfbedarf ohne gültiges Kennzeichen: "
                            + shorten(need), "", false));
                }
                // D4
                if (!noEvidence) {
                    for (String problem : checkEvidence(evidence, code)) {
                        String[] parts = problem.split("\u0000", 2);
                        findings.add(new Finding("D4", phase, r.nr(), r.key(), parts[1], parts[0], false));
                        bad.add(r);
                    }
                }
                // D6
                for (int c = 0; c < r.cells().size(); c++) {
                    Matcher m = ELLIPSIS.matcher(r.cells().get(c));
                    if (m.find()) {
                        String column = c < t.header().size() ? t.header().get(c) : "Spalte " + (c + 1);
                        findings.add(new Finding("D6", phase, r.nr(), r.key(),
                                "Auslassung „" + m.group() + "“ in Spalte " + column, "", false));
                        break;
                    }
                }
                // D5 (references)
                int opl = t.colExact("opl");
                Matcher m = OPL_REF.matcher(MdTable.cell(r.cells(), opl));
                while (m.find()) {
                    referenced.add(m.group());
                    refs.add(m.group());
                }
            }
        }

        // D5
        Set<String> points = new LinkedHashSet<>();
        if (spec.opl() != null) {
            for (List<String> row : spec.opl().rows()) {
                Matcher m = OPL_REF.matcher(MdTable.cell(row, 0));
                if (m.find()) {
                    points.add(m.group());
                }
            }
        }
        for (Map.Entry<SpecPhase, List<Row>> entry : rowsByPhase.entrySet()) {
            for (Row r : entry.getValue()) {
                Matcher m = OPL_REF.matcher(MdTable.cell(r.cells(), r.table().colExact("opl")));
                while (m.find()) {
                    if (!points.contains(m.group())) {
                        findings.add(new Finding("D5", entry.getKey(), r.nr(), r.key(),
                                "Verweis auf " + m.group() + ", den die OPL nicht enthält", "", false));
                    }
                }
            }
        }
        for (String point : points) {
            if (!referenced.contains(point)) {
                findings.add(new Finding("D5", null, "", point, "OPL-Punkt ohne Verweis aus einer Tabelle", "", true));
            }
        }

        // D7
        Boolean declared = Spec.complete(spec.status());
        if (spec.status() == null) {
            findings.add(new Finding("D7", null, "", "", "Keine Statuszeile in der Zusammenfassung", "", false));
        } else if (declared == null) {
            findings.add(new Finding("D7", null, "", "", "Status nicht erkennbar: " + shorten(spec.status()), "", false));
        } else if (declared && !complete) {
            findings.add(new Finding("D7", null, "", "", "Status VOLLSTÄNDIG trotz Befund in D1 (fehlende, doppelte "
                    + "oder unbekannte Schlüssel)", "", false));
        }

        // key figures
        Map<SpecPhase, Stats> finalStats = new EnumMap<>(SpecPhase.class);
        stats.forEach((phase, s) -> finalStats.put(phase, new Stats(s.soll(), s.delivered(), s.missing(),
                s.duplicate(), s.unknown(), s.helpers(), badEvidence.getOrDefault(phase, Set.of()).size(),
                referencedByPhase.getOrDefault(phase, Set.of()).size())));
        return new Result(List.copyOf(findings), finalStats, points.size(), complete, lastCompleteNr);
    }

    private static List<Row> rows(SpecPhase phase, MdTable table, boolean rule, Map<String, String> nrByKey) {
        int nrCol = table.colExact("nr");
        int keyCol = rule ? -1 : phase.keyColumn(table);
        int ruleCol = table.col("regel");
        List<Row> rows = new ArrayList<>();
        for (List<String> cells : table.rows()) {
            String nr = MdTable.cell(cells, nrCol);
            String keyCell = MdTable.cell(cells, keyCol);
            String key = rule ? shorten(MdTable.cell(cells, ruleCol)) : TaskLists.key(keyCell);
            if (!rule && nr.isBlank()) {
                nr = nrByKey.getOrDefault(key, key);
            }
            boolean helper = !rule && SpecMerger.isHelper(nr, keyCell);
            rows.add(new Row(phase, rule, nr, key, helper, table, cells));
        }
        return rows;
    }

    /** Problems of an evidence cell as "token NUL description"; empty if every item is found in the code context. */
    private static List<String> checkEvidence(String evidence, CodeContext code) {
        List<String> problems = new ArrayList<>();
        for (String raw : evidence.split("·|;|,(?![^()]*\\))|<br>|\\n| und ")) {
            String part = STEP_PREFIX.matcher(raw.replace("`", "").strip()).replaceFirst("").strip();
            if (part.isEmpty()) {
                continue;
            }
            Matcher file = FILE_NAME.matcher(part);
            if (file.find()) {
                String name = file.group(1);
                if (!code.hasFile(name.replace('\\', '/'))) {
                    problems.add(part + "\u0000Datei " + name + " nicht im Code-Kontext");
                }
                continue;
            }
            Matcher cm = CLASS_METHOD.matcher(part);
            if (!cm.find()) {
                problems.add(part + "\u0000Beleg nicht prüfbar (anderes Format als Klasse.Methode): " + shorten(part));
                continue;
            }
            String cls = cm.group(1);
            String method = cm.group(2);
            List<String> sources = code.classSources(cls);
            if (sources.isEmpty()) {
                problems.add(cls + "." + method + "\u0000Klasse " + cls + " nicht im Code-Kontext");
            } else if (!method.equals("java") && sources.stream().noneMatch(s -> CodeContext.hasMethod(s, method))) {
                problems.add(cls + "." + method + "\u0000Methode " + method + " nicht in " + cls);
            }
        }
        return problems;
    }

    private static String shorten(String text) {
        String t = text.replaceAll("\\s+", " ").strip();
        return t.length() > 60 ? t.substring(0, 57) + "..." : t;
    }
}
