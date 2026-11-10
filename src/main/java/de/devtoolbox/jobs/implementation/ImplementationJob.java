package de.devtoolbox.jobs.implementation;

import de.devtoolbox.core.Config;
import de.devtoolbox.core.Job;
import de.devtoolbox.core.MigrationProject;
import de.devtoolbox.core.Param;
import de.devtoolbox.tools.codecollector.CodeCollectorTool;
import de.devtoolbox.tools.standards.StandardsTool;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Implementation of the selected batch phases of an entity from an approved specification (PDF, the reviewed result of the
 * specification job). The {@link StandardsTool} derives structural templates from entities already implemented in
 * the target project; the task tells the AI to implement by example. Output for Copilot: skill + task (incl.
 * standards) + the specification PDF.
 */
public final class ImplementationJob implements Job {

    private static final String SPEC = "spec";
    private static final String SKILL_FILE = "skill-implementierung.md";

    private final StandardsTool standards = new StandardsTool();

    @Override
    public String id() {
        return "implementation";
    }

    @Override
    public String name() {
        return "Implementierung nach Spezifikation (je Phase, fachliche Entität)";
    }

    @Override
    public String fileTag() {
        return "implementierung";
    }

    @Override
    public String description() {
        return "Nimmt die abgestimmte Spezifikation (PDF) einer Entität und sucht im Zielprojekt bereits umgesetzte "
                + "Entitäten als Vorlagen für die gewählten Phasen (Read: TO, PO, Entity, Einleselogik; Process: Mapping; "
                + "Write: Schreiblogik). Daraus entsteht eine Aufgabe für die KI, nach diesen Beispielen zu implementieren. Ergebnis zum Hochladen in Copilot: "
                + "Skill + Aufgabe (mit Standards) + Spezifikation.";
    }

    @Override
    public List<Param> params() {
        Map<String, Param> tool = standards.params().stream().collect(Collectors.toMap(Param::key, Function.identity()));
        return List.of(
                tool.get(StandardsTool.TARGET),
                Param.file(SPEC, "Spezifikation (PDF)", true,
                        "Vom Fachbereich abgestimmtes Ergebnis der Spezifikation", "pdf"),
                tool.get(StandardsTool.ENTITY),
                tool.get(CodeCollectorTool.PHASES),
                tool.get(StandardsTool.EXAMPLE),
                Param.text("context", "Zusatzkontext (KI)", "", false,
                        "Optional, z. B. Ziel-Package, Besonderheiten, bekannte Abweichungen"),
                tool.get("output"));
    }

    @Override
    public List<Path> run(Config config, Consumer<String> log) throws Exception {
        Path spec = config.path(SPEC);
        if (spec == null || !Files.isRegularFile(spec)) {
            throw new IllegalArgumentException("Spezifikation nicht gefunden: " + config.text(SPEC));
        }
        if (!isPdf(spec)) {
            throw new IllegalArgumentException("Spezifikation ist keine PDF-Datei: " + spec);
        }
        StandardsTool.Result result = standards.collect(config, log);

        String slug = CodeCollectorTool.slug(config.text(StandardsTool.ENTITY));
        Path dir = StandardsTool.outputDir(config);
        Files.createDirectories(dir);
        Path skill = dir.resolve(SKILL_FILE);
        Path task = dir.resolve("aufgabe-implementierung-" + slug + ".md");
        Path specCopy = dir.resolve("spezifikation-" + slug + ".pdf");
        if (!spec.toAbsolutePath().normalize().equals(specCopy.toAbsolutePath().normalize())) {
            Files.copy(spec, specCopy, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.writeString(skill, resource("/skills/implementation/skill.md"), StandardCharsets.UTF_8);
        Files.writeString(task, renderTask(config, result, specCopy.getFileName().toString()), StandardCharsets.UTF_8);

        List<Path> files = new ArrayList<>(List.of(skill, task, specCopy));
        log.accept("Aufgabe geschrieben: " + task + " (" + result.templates() + " Vorlagen)");
        log.accept("Hochladen (3 Dateien): " + files.stream().map(p -> p.getFileName().toString())
                .collect(Collectors.joining(", ")));
        log.accept("Prompt: Bearbeite die Aufgabe in " + task.getFileName() + " mit dem Skill " + SKILL_FILE
                + "; die Spezifikation steht in " + specCopy.getFileName() + ".");
        return files;
    }

    private String renderTask(Config config, StandardsTool.Result result, String specFile) throws IOException {
        String entity = config.text(StandardsTool.ENTITY);
        String context = config.text("context");
        String project = MigrationProject.find(config.text(MigrationProject.KEY))
                .map(p -> p.label() + (p.sparte().isEmpty() ? "" : " (" + p.sparte() + ")")).orElse("–");
        String task = resource("/aufgaben/implementation/aufgabe.md")
                .replace("{{ARTIFACTS}}", result.artifactsTable().strip())
                .replace("{{CONTEXT}}", context.isEmpty() ? "" : "**Zusatzkontext:** " + context)
                .replace("{{SKILL_FILE}}", SKILL_FILE)
                .replace("{{SPEC_FILE}}", specFile)
                .replace("{{PROJECT}}", project + " · `" + config.text(StandardsTool.TARGET) + "`")
                .replace("{{EXAMPLE}}", result.example() == null ? "– (keine gefunden)" : result.example())
                .replace("{{PHASES}}", phaseNames(config))
                .replace("{{ENTITY}}", entity)
                .replaceAll("\n{3,}", "\n\n");
        return task.strip() + "\n\n" + result.markdown();
    }

    private static String phaseNames(Config config) {
        List<String> selected = config.list(CodeCollectorTool.PHASES).stream().map(String::toUpperCase).toList();
        return java.util.stream.Stream.of("READ", "PROCESS", "WRITE").filter(selected::contains)
                .map(p -> p.charAt(0) + p.substring(1).toLowerCase()).collect(Collectors.joining(", "));
    }

    private static boolean isPdf(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            return new String(in.readNBytes(5), StandardCharsets.ISO_8859_1).equals("%PDF-");
        }
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = ImplementationJob.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Vorlage fehlt: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }
}
