package de.devtoolbox.jobs.speccheck;

import de.devtoolbox.core.Config;
import de.devtoolbox.core.Job;
import de.devtoolbox.core.JobInfo;
import de.devtoolbox.core.Param;
import de.devtoolbox.tools.speccheck.SpecCheckTool;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Checks the result of a "phase-spec" run locally: merges several result files (main file, continuation,
 * corrections) and checks completeness, mandatory cells and evidence against the code context. Produces no
 * Copilot task and therefore does not extend {@code TaskJob}.
 */
public final class SpecCheckJob implements Job {

    /** Key of the run folder (only used by this job, therefore not in {@code Keys}). */
    public static final String RUN = "run";

    private final SpecCheckTool tool = new SpecCheckTool();

    @Override
    public String id() {
        return "spec-check";
    }

    @Override
    public String name() {
        return "Spezifikation prüfen";
    }

    @Override
    public String fileTag() {
        return "spezifikation-pruefen";
    }

    @Override
    public JobInfo info() {
        return new JobInfo(
                "Prüft das Ergebnis von Copilot auf Vollständigkeit und Belege und führt mehrere Ergebnisdateien zusammen – ohne Copilot, lokal.",
                "Der Ordner des Laufs (Job „Spezifikation aus Referenzprojekt“) mit der Ergebnisdatei von Copilot (spezifikation-*.md); bei Fortsetzung oder Korrektur auch die weiteren Dateien (…-teil2.md, …-korrektur.md).",
                "Der Ordner enthält die Aufgabe (aufgabe-*.md), den Code-Kontext (code-*.md) und mindestens ein Ergebnis von Copilot (spezifikation-*.md).",
                "Prüfbericht (pruefbericht-<Entität>.md) mit Kennzahlen und Befunden je Prüfung; bei mehreren Ergebnisdateien die zusammengeführte Spezifikation (spezifikation-<Entität>-gesamt.md). Das Protokoll nennt bei Befunden den Nachbesserungsprompt für denselben Chat. Geprüft sind Form und Auffindbarkeit der Belege, nicht die fachliche Richtigkeit.");
    }

    @Override
    public List<Param> params() {
        return List.of(Param.directory(RUN, "Ordner des Laufs", true,
                "Ordner mit aufgabe-*.md, code-*.md und den Ergebnissen spezifikation-*.md"));
    }

    @Override
    public List<Path> run(Config config, Consumer<String> log) throws Exception {
        return tool.run(config.path(RUN), log);
    }
}
