package de.devtoolbox;

import de.devtoolbox.core.*;
import de.devtoolbox.ui.MainWindow;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Without arguments: opens the window. With arguments: runs a job headless, e.g.
 * <pre>
 *   java -jar devtoolbox.jar phase-spec base=C:\proj terms=Risiko,Risk phases=READ,PROCESS
 *   java -jar devtoolbox.jar risiko.json                 (job id is taken from the file's "job" entry)
 *   java -jar devtoolbox.jar risiko.json terms=Wagnis    (key=value overrides the file)
 * </pre>
 */
public final class App {

    static void main(String[] args) throws Exception {
        if (args.length == 0) {
            MainWindow.show(JobRegistry.all());
            return;
        }
        Map<String, String> file = new HashMap<>();
        String jobId = args[0];
        if (jobId.toLowerCase().endsWith(".json")) {
            file.putAll(Json.parse(Files.readString(Path.of(jobId))));
            jobId = file.remove("job");
        }
        Job job = jobId == null ? null : JobRegistry.find(jobId).orElse(null);
        if (job == null) {
            printUsage();
            System.exit(1);
        }
        Map<String, String> cli = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            int eq = args[i].indexOf('=');
            if (eq > 0) {
                cli.put(args[i].substring(0, eq), args[i].substring(eq + 1));
            }
        }
        // precedence: defaults < migration project < JSON file < command line
        String projectId = cli.getOrDefault(Keys.PROJECT, file.getOrDefault(Keys.PROJECT, ""));
        Map<String, String> project = new HashMap<>();
        if (!projectId.isBlank()) {
            MigrationProject p = MigrationProject.find(projectId).orElse(null);
            if (p == null) {
                System.err.println("Unbekanntes Migrationsprojekt: " + projectId + " (vorhanden: "
                        + String.join(", ", MigrationProject.all().stream().map(MigrationProject::id).toList()) + ")");
                System.exit(1);
            }
            project.putAll(p.values());
            project.put(Keys.PROJECT, p.id());
        }
        Config config = Config.of(job.params(), project, file, cli);
        List<String> missing = config.missing(job.params());
        if (!missing.isEmpty()) {
            System.err.println("Fehlende Angaben: " + String.join(", ", missing));
            System.exit(1);
        }
        try {
            job.run(config, System.out::println);
        } catch (IllegalArgumentException e) {
            System.err.println("FEHLER: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("\nMigrationsprojekte (" + MigrationProject.directory() + "):");
        for (MigrationProject p : MigrationProject.all()) {
            System.out.printf("  %-9s %-45s %s%n", p.id(), p.label() + " (" + p.sparte() + ")",
                    p.reference() == null && p.referenceUrl().isEmpty() ? "[referenz nicht konfiguriert]"
                            : "Referenz: " + de.devtoolbox.core.CodeSource.mask(p.referenceLabel()));
        }
        List<Job> listed = new java.util.ArrayList<>(JobRegistry.all());
        listed.addAll(JobRegistry.hidden());
        for (Job job : listed) {
            System.out.printf("%n%s  (%s)%n", job.id(), job.name()
                    + (JobRegistry.hidden().contains(job) ? " – alle oder gewählte Phasen in einem Lauf, nur Kommandozeile" : ""));
            for (Param p : job.params()) {
                String options = p.options().isEmpty() ? ""
                        : " [" + String.join("|", p.options().stream().map(Param.Option::value).toList()) + "]";
                System.out.printf("  %-18s %s%s%s%n", p.key(), p.label(), options, p.required() ? " (Pflicht)" : "");
            }
        }
    }
}
