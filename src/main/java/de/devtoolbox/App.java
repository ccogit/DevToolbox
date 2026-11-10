package de.devtoolbox;

import de.devtoolbox.core.Config;
import de.devtoolbox.core.Job;
import de.devtoolbox.core.JobRegistry;
import de.devtoolbox.core.Json;
import de.devtoolbox.core.MigrationProject;
import de.devtoolbox.core.Param;
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

    public static void main(String[] args) throws Exception {
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
        String projectId = cli.getOrDefault(MigrationProject.KEY, file.getOrDefault(MigrationProject.KEY, ""));
        Map<String, String> project = new HashMap<>();
        if (!projectId.isBlank()) {
            MigrationProject p = MigrationProject.find(projectId).orElse(null);
            if (p == null) {
                System.err.println("Unbekanntes Migrationsprojekt: " + projectId + " (vorhanden: "
                        + String.join(", ", MigrationProject.all().stream().map(MigrationProject::id).toList()) + ")");
                System.exit(1);
            }
            project.putAll(p.values());
            project.put(MigrationProject.KEY, p.id());
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
        System.out.println("Aufruf: java -jar devtoolbox.jar <job-id | konfig.json> [project=<id>] [key=wert ...]   (ohne Argumente: Oberfläche)");
        System.out.println("\nMigrationsprojekte (" + MigrationProject.directory() + "):");
        for (MigrationProject p : MigrationProject.all()) {
            System.out.printf("  %-8s %s%s%n", p.id(), p.label(), p.root() == null ? "  [root nicht konfiguriert]" : "");
        }
        for (Job job : JobRegistry.all()) {
            System.out.printf("%n%s  (%s)%n", job.id(), job.name());
            for (Param p : job.params()) {
                String options = p.options().isEmpty() ? ""
                        : " [" + String.join("|", p.options().stream().map(Param.Option::value).toList()) + "]";
                System.out.printf("  %-18s %s%s%s%n", p.key(), p.label(), options, p.required() ? " (Pflicht)" : "");
            }
        }
    }
}
