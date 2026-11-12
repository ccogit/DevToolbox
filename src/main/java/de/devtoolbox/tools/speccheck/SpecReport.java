package de.devtoolbox.tools.speccheck;

import de.devtoolbox.tools.speccheck.Spec.PhaseTables;
import de.devtoolbox.tools.speccheck.SpecChecker.Finding;
import de.devtoolbox.tools.speccheck.SpecChecker.Result;
import de.devtoolbox.tools.speccheck.SpecChecker.Stats;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** The texts the check produces: test report, merged specification and correction prompt. */
public final class SpecReport {

    /** Maximum length of a prompt (starting value, see the specification, section 5.2). */
    public static final int MAX_PROMPT_LENGTH = 1500;

    /** The correction prompt; {@code truncated} if findings were left out to fit {@link #MAX_PROMPT_LENGTH}. */
    public record Prompt(String text, boolean truncated) {
    }

    private static final Map<String, String> CHECKS = new LinkedHashMap<>();

    static {
        CHECKS.put("D1", "Soll-Schlüssel gegen Schlüssel im Ergebnis");
        CHECKS.put("D2", "Pflichtzellen");
        CHECKS.put("D3", "Kennzeichen Prüfbedarf");
        CHECKS.put("D4", "Belege im Code-Kontext");
        CHECKS.put("D5", "OPL-Verweise");
        CHECKS.put("D6", "Auslassungen in Zellen");
        CHECKS.put("D7", "Statuszeile");
    }

    private SpecReport() {
    }

    /** One line per phase with the key figures, e.g. for the log. */
    public static List<String> statLines(Result result) {
        List<String> lines = new ArrayList<>();
        result.stats().forEach((phase, s) -> lines.add(phase.label() + ": Soll " + s.soll() + " · geliefert "
                + s.delivered() + " · fehlend " + s.missing() + " · doppelt " + s.duplicate() + " · unbekannt "
                + s.unknown() + " · Hilfswerte " + s.helpers() + " · Zeilen ohne gültigen Beleg "
                + s.withoutEvidence() + " · OPL-Punkte " + s.openPoints()));
        return lines;
    }

    /** "D1 3 · D2 0 · …" */
    public static String countLine(Result result) {
        List<String> parts = new ArrayList<>();
        for (String check : CHECKS.keySet()) {
            parts.add(check + " " + result.of(check).stream().filter(f -> !f.notice()).count());
        }
        return String.join(" · ", parts);
    }

    public static String report(String entity, List<String> inputs, Result result, boolean merged) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Prüfbericht Spezifikation „").append(entity).append("“\n\n");
        sb.append("Geprüft sind Form und Auffindbarkeit der Belege, nicht die fachliche Richtigkeit.\n\n");
        sb.append("Ergebnisdateien (in dieser Reihenfolge")
                .append(merged ? ", zusammengeführt zu `spezifikation-" + entity + "-gesamt.md`" : "").append("): ")
                .append(String.join(", ", inputs.stream().map(i -> "`" + i + "`").toList())).append("\n\n");
        sb.append("Status der Prüfung: ").append(result.findings().stream().noneMatch(f -> !f.notice())
                ? "keine Befunde" : "Befunde vorhanden").append("\n\n");
        sb.append("## Kennzahlen\n\n");
        sb.append(kennzahlen(result)).append("\n\n");
        sb.append("OPL-Punkte insgesamt: ").append(result.openPointCount()).append("\n");
        for (Map.Entry<String, String> check : CHECKS.entrySet()) {
            sb.append("\n## ").append(check.getKey()).append(" – ").append(check.getValue()).append("\n\n");
            List<Finding> findings = result.of(check.getKey());
            if (findings.isEmpty()) {
                sb.append("Keine Befunde.\n");
                continue;
            }
            sb.append("| Phase | Nr | Schlüssel | Befund |\n|---|---|---|---|\n");
            for (Finding f : findings) {
                sb.append("| ").append(f.phase() == null ? "–" : f.phase().label()).append(" | ")
                        .append(cell(f.nr())).append(" | ").append(cell(f.key())).append(" | ")
                        .append(f.notice() ? "Hinweis: " : "").append(f.detail().replace("|", "\\|")).append(" |\n");
            }
        }
        return sb.toString();
    }

    private static String cell(String text) {
        return text.isEmpty() ? "–" : text.replace("|", "\\|");
    }

    private static String kennzahlen(Result result) {
        StringBuilder sb = new StringBuilder("| Phase | Soll | Geliefert | Fehlend | Doppelt | Unbekannt | Hilfswerte "
                + "| Zeilen ohne gültigen Beleg | OPL-Punkte (referenziert) |\n|---|---|---|---|---|---|---|---|---|");
        for (Map.Entry<SpecPhase, Stats> e : result.stats().entrySet()) {
            Stats s = e.getValue();
            sb.append("\n| ").append(e.getKey().label()).append(" | ").append(s.soll()).append(" | ")
                    .append(s.delivered()).append(" | ").append(s.missing()).append(" | ").append(s.duplicate())
                    .append(" | ").append(s.unknown()).append(" | ").append(s.helpers()).append(" | ")
                    .append(s.withoutEvidence()).append(" | ").append(s.openPoints()).append(" |");
        }
        return sb.toString();
    }

    /** The merged specification in the outline of the skill; status and key figures are recomputed. */
    public static String merged(String entity, Spec spec, Result result) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Spezifikation „").append(entity).append("“ (zusammengeführt)\n\n");
        sb.append("## Zusammenfassung\n\n");
        sb.append("Status: ").append(result.complete() ? "VOLLSTÄNDIG"
                : "UNVOLLSTÄNDIG – letzte vollständige Zeile: "
                + (result.lastCompleteNr() == null ? "keine" : result.lastCompleteNr())).append("\n\n");
        String text = String.join("\n", spec.summary()).strip();
        if (!text.isEmpty()) {
            sb.append(text).append("\n\n");
        }
        sb.append("Kennzahlen (neu berechnet):\n\n").append(kennzahlen(result)).append("\n\n");
        sb.append("OPL-Punkte: ").append(result.openPointCount()).append("\n");
        if (spec.flow() != null) {
            sb.append("\n## Ablaufübersicht\n\n").append(spec.flow().render()).append("\n");
        }
        for (SpecPhase phase : SpecPhase.values()) {
            PhaseTables tables = spec.phases().get(phase);
            if (tables == null) {
                continue;
            }
            sb.append("\n## ").append(phase.section()).append("\n");
            if (tables.attrs() != null) {
                sb.append("\n").append(tables.attrs().render()).append("\n");
            }
            if (tables.rules() != null) {
                sb.append("\n").append(tables.rules().render()).append("\n");
            }
        }
        if (spec.opl() != null) {
            sb.append("\n## Offene-Punkte-Liste (OPL)\n\n").append(spec.opl().render()).append("\n");
        }
        return sb.toString();
    }

    /**
     * The correction prompt of section 5.3, or empty if D1 (missing, duplicate), D2 and D4 have no findings.
     * Categories without findings are omitted; if the text exceeds {@link #MAX_PROMPT_LENGTH}, the last items of
     * the longest list are dropped (marked "u. a.") until it fits.
     */
    public static Optional<Prompt> correctionPrompt(String entity, Result result) {
        List<String> missing = distinct(result.findings().stream()
                .filter(f -> f.check().equals("D1") && f.detail().equals("fehlend")).map(SpecReport::nr).toList());
        List<String> duplicate = distinct(result.findings().stream()
                .filter(f -> f.check().equals("D1") && f.detail().startsWith("doppelt")).map(SpecReport::nr).toList());
        List<String> evidence = distinct(result.of("D4").stream().map(f -> nr(f) + ": " + f.extra()).toList());
        List<String> empty = distinct(result.of("D2").stream().map(SpecReport::nr).toList());
        if (missing.isEmpty() && duplicate.isEmpty() && evidence.isEmpty() && empty.isEmpty()) {
            return Optional.empty();
        }
        List<List<String>> lists = new ArrayList<>(List.of(new ArrayList<>(missing), new ArrayList<>(duplicate),
                new ArrayList<>(evidence), new ArrayList<>(empty)));
        Set<Integer> cut = new LinkedHashSet<>();
        String text = prompt(entity, lists, cut);
        while (text.length() > MAX_PROMPT_LENGTH) {
            int longest = -1;
            int length = 0;
            for (int i = 0; i < lists.size(); i++) {
                int size = lists.get(i).stream().mapToInt(s -> s.length() + 2).sum();
                if (lists.get(i).size() > 1 && size > length) {
                    longest = i;
                    length = size;
                }
            }
            if (longest < 0) {
                break;
            }
            lists.get(longest).removeLast();
            cut.add(longest);
            text = prompt(entity, lists, cut);
        }
        return Optional.of(new Prompt(text, !cut.isEmpty()));
    }

    private static String prompt(String e, List<List<String>> lists, Set<Integer> cut) {
        String[] labels = {"Fehlend: ", "Doppelt: ", "Beleg steht nicht im Code-Kontext: ", "Pflichtzelle leer: "};
        StringBuilder sb = new StringBuilder("In spezifikation-" + e + ".md sind diese Zeilen fehlerhaft.");
        for (int i = 0; i < lists.size(); i++) {
            if (!lists.get(i).isEmpty()) {
                sb.append(' ').append(labels[i]).append(String.join(i == 2 ? "; " : ", ", lists.get(i)))
                        .append(cut.contains(i) ? " u. a." : "").append('.');
            }
        }
        return sb.append(" Liefere ausschließlich diese Zeilen neu, im selben Tabellenformat und unter denselben "
                + "Überschriften, als Markdown-Datei spezifikation-" + e + "-korrektur.md zum Download. Ändere sonst "
                + "nichts. Findest du im Code-Kontext keinen Beleg, schreibe „nicht belegt“ und ergänze einen "
                + "OPL-Punkt.").toString();
    }

    private static String nr(Finding f) {
        return f.nr().isEmpty() ? f.key() : f.nr();
    }

    private static List<String> distinct(List<String> items) {
        return new ArrayList<>(new LinkedHashSet<>(items));
    }
}
