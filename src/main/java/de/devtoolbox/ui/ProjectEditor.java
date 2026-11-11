package de.devtoolbox.ui;

import de.devtoolbox.core.EntitySuggestions;
import de.devtoolbox.core.Json;
import de.devtoolbox.core.MigrationProject;
import de.devtoolbox.core.Param;
import de.devtoolbox.core.Param.Option;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dialog to create, edit or delete a migration project ({@code migrationsprojekte/<id>/projekt.json}) with the same
 * kind of form as the job configuration. Unknown entries of an existing file are kept.
 */
final class ProjectEditor {

    /** Outcome of the dialog: the saved project's id, or {@code deleted}; {@code null} result = cancelled. */
    record Result(String id, boolean deleted) {
    }

    private static final String ID = "id";
    private static final String REFERENCE_PROJECT = "referenzProjekt";
    private static final String REFERENCE_PATH = "referenzPfad";

    private ProjectEditor() {
    }

    /**
     * @param project the project to edit, or {@code null} to create a new one
     * @return what happened, or {@code null} if cancelled
     */
    static Result open(Component parent, MigrationProject project) {
        Path projectsDir = MigrationProject.directory();
        if (projectsDir == null) {
            JOptionPane.showMessageDialog(parent, "Kein Ordner 'migrationsprojekte' gefunden.");
            return null;
        }
        List<MigrationProject> others = MigrationProject.all().stream()
                .filter(p -> project == null || !p.id().equals(project.id())).toList();
        List<Param> params = params(project == null, others);
        ParamForm form = new ParamForm("projekt-editor", params);
        // root is a module of the line's parent project: target model and mapping templates lie in its
        // designer-services module – prefilled, but the user may overwrite them
        form.deriveFrom("root", Map.of(
                "zielmodell", r -> string(de.devtoolbox.core.ProjectLayout.targetModel(pathOrNull(r))),
                "mappingvorlagen", r -> string(de.devtoolbox.core.ProjectLayout.mappingTemplates(pathOrNull(r))),
                "sparte", r -> de.devtoolbox.core.ProjectLayout.sparte(pathOrNull(r))));
        // all projects lie in C:/firma-develop: the migration module follows from insurance line and id
        String fixedId = project == null ? null : project.id();
        java.util.function.Function<String, String> rootOf = ignored -> string(de.devtoolbox.core.ProjectLayout.root(
                form.value("sparte"), fixedId != null ? fixedId : form.value(ID)));
        form.deriveFrom("sparte", Map.of("root", rootOf));
        if (project == null) {
            form.deriveFrom(ID, Map.of("root", rootOf));
        }
        form.setValues(initialValues(project, others));

        // editable entity list: stored, or suggested (target model / reference code / other projects)
        JTextArea entities = new JTextArea(project == null ? "" : String.join("\n", project.entities()), 6, 30);
        JLabel entityHint = new JLabel(" ");
        entityHint.setForeground(UIManager.getColor("Label.disabledForeground"));
        JTextArea provenance = new JTextArea(6, 30);
        provenance.setEditable(false);
        provenance.setOpaque(false);
        Runnable suggest = () -> {
            EntitySuggestions.Suggestion s = EntitySuggestions.suggest(
                    pathOrDerived(form.value("ddl"), project == null ? null : project.ddl()),
                    pathOrDerived(form.value("zielmodell"), project == null ? null : project.targetModel()),
                    pathOrDerived(form.value("mappingvorlagen"), project == null ? null : project.mappingTemplates()),
                    referencePath(form, others), project == null ? null : project.id());
            entities.setText(String.join("\n", s.names()));
            provenance.setText(s.isEmpty() ? "Nichts zum Ableiten gefunden – bitte Entitäten eintragen."
                    : s.entries().stream().map(EntitySuggestions.Entry::label)
                            .collect(java.util.stream.Collectors.joining("\n")));
            provenance.setCaretPosition(0);
            entityHint.setText(s.names().size() + " Vorschläge – Altdaten-Namen bitte prüfen und anpassen");
        };
        if (project == null) {
            suggest.run();
        }
        JButton suggestButton = new JButton("Vorschlagen");
        suggestButton.setToolTipText("Liste aus Schema (DDL), Zielmodell, Mapping-Vorlagen und Referenzcode "
                + "zusammenführen (ersetzt die aktuelle Liste)");
        suggestButton.addActionListener(e -> suggest.run());
        JPanel entityPanel = new JPanel(new BorderLayout(8, 4));
        entityPanel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        entityPanel.add(new JLabel("<html>Entitäten<br>(eine je Zeile)</html>"), BorderLayout.WEST);
        entityPanel.add(new JScrollPane(entities), BorderLayout.CENTER);
        JPanel entitySide = new JPanel(new BorderLayout(0, 4));
        entitySide.add(suggestButton, BorderLayout.NORTH);
        JScrollPane provenanceScroll = new JScrollPane(provenance);
        provenanceScroll.setBorder(BorderFactory.createTitledBorder("Herkunft des letzten Vorschlags"));
        entityPanel.add(provenanceScroll, BorderLayout.EAST);
        entityPanel.add(suggestButton, BorderLayout.NORTH);
        entityPanel.add(entityHint, BorderLayout.SOUTH);

        Window owner = parent instanceof Window w ? w : parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner,
                project == null ? "Migrationsprojekt anlegen" : "Migrationsprojekt bearbeiten – " + project.label(),
                Dialog.ModalityType.APPLICATION_MODAL);
        Result[] result = {null};
        JButton save = new JButton("Speichern");
        JButton cancel = new JButton("Abbrechen");
        cancel.addActionListener(e -> dialog.dispose());
        save.addActionListener(e -> {
            try {
                result[0] = new Result(save(projectsDir, project, form.values(), entities.getText()), false);
                dialog.dispose();
                if (project == null) {
                    // initial step for a new project: derive the standards from its code right away
                    MigrationProject created = MigrationProject.find(result[0].id()).orElse(null);
                    if (created != null && (created.root() != null || created.reference() != null)
                            && JOptionPane.showConfirmDialog(parent, "Standards für die Implementierung jetzt aus "
                                    + "einer bereits umgesetzten Entität erzeugen?\n(Später jederzeit über "
                                    + "„Bearbeiten…“ → „Standards erzeugen…“.)", "Standards erzeugen",
                            JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                        generateStandards(parent, created);
                    }
                }
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(dialog, ex.getMessage());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Speichern fehlgeschlagen:\n" + ex.getMessage());
            }
        });

        JLabel info = new JLabel("<html>Gespeichert in <code>" + (project == null
                ? projectsDir.resolve("<id>").resolve("projekt.json") : project.folder().resolve("projekt.json"))
                + "</code>. Pfade im Projektordner werden relativ abgelegt.</html>");
        info.setForeground(UIManager.getColor("Label.disabledForeground"));
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.add(save);
        right.add(cancel);
        JPanel buttons = new JPanel(new BorderLayout());
        buttons.add(right, BorderLayout.EAST);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        if (project != null) {
            JButton standards = new JButton("Standards erzeugen...");
            standards.setToolTipText("Vorlagen aus einer bereits umgesetzten Entität erzeugen (gespeicherter Stand des Projekts)");
            standards.addActionListener(e -> generateStandards(dialog, project));
            left.add(standards);
            JButton delete = new JButton("Löschen...");
            delete.setToolTipText("Projektordner mit projekt.json und Schema-Beschreibung löschen");
            delete.addActionListener(e -> {
                try {
                    if (delete(dialog, project)) {
                        result[0] = new Result(project.id(), true);
                        dialog.dispose();
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(dialog, "Löschen fehlgeschlagen:\n" + ex.getMessage());
                }
            });
            left.add(delete);
        }
        buttons.add(left, BorderLayout.WEST);
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));
        root.add(info, BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout());
        center.add(form, BorderLayout.NORTH);
        center.add(entityPanel, BorderLayout.CENTER);
        root.add(center, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.getRootPane().setDefaultButton(save);
        dialog.pack();
        dialog.setSize(820, dialog.getHeight()); // long paths must not widen the dialog
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
        return result[0];
    }

    /**
     * Derives the project's standards: choose the source (own code or reference project) and the example entity,
     * confirm replacing existing (possibly edited) standards – they are kept as a backup – and write them.
     */
    static void generateStandards(Component parent, MigrationProject p) {
        try {
            Map<String, Path> sources = new LinkedHashMap<>();
            if (p.root() != null && Files.isDirectory(p.root())) {
                sources.put("Code dieser Migration – " + p.root(), p.root());
            }
            if (p.reference() != null && Files.isDirectory(p.reference())) {
                sources.put("Referenzprojekt – " + p.referenceLabel(), p.reference());
            }
            for (MigrationProject other : MigrationProject.all()) {
                if (!other.id().equals(p.id()) && other.root() != null && Files.isDirectory(other.root())
                        && !sources.containsValue(other.root())) {
                    sources.put("Anderes Migrationsprojekt – " + other.label(), other.root());
                }
            }
            String choose = "Anderes Verzeichnis wählen…";
            sources.put(choose, null);
            String sourceKey = (String) JOptionPane.showInputDialog(parent, "Woher sollen die Standards kommen?\n"
                            + "Üblich ist der Code dieser Migration; sonst ein Projekt, in dem die Entitäten bereits "
                            + "nach den gewünschten Konventionen umgesetzt sind.", "Standards erzeugen",
                    JOptionPane.QUESTION_MESSAGE, null, sources.keySet().toArray(), sources.keySet().iterator().next());
            if (sourceKey == null) {
                return;
            }
            Path source = sources.get(sourceKey);
            if (source == null) {
                JFileChooser chooser = new JFileChooser(p.root() != null ? p.root().toFile() : null);
                chooser.setDialogTitle("Code-Verzeichnis (Root) für die Standards wählen");
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
                    return;
                }
                source = chooser.getSelectedFile().toPath();
            }
            List<de.devtoolbox.tools.standards.StandardsTool.Candidate> candidates =
                    de.devtoolbox.tools.standards.StandardsTool.candidates(source);
            if (candidates.isEmpty()) {
                JOptionPane.showMessageDialog(parent, "In " + source + " wurden keine umgesetzten Entitäten "
                        + "(TO, PO, Entity, Repository, Logik) gefunden.");
                return;
            }
            String[] labels = candidates.stream().map(de.devtoolbox.tools.standards.StandardsTool.Candidate::label)
                    .toArray(String[]::new);
            String chosen = (String) JOptionPane.showInputDialog(parent, "Beispiel-Entität für die Standards "
                            + "(vorgeschlagen: die vollständigste):", "Standards erzeugen", JOptionPane.QUESTION_MESSAGE,
                    null, labels, labels[0]);
            if (chosen == null) {
                return;
            }
            String example = candidates.get(java.util.Arrays.asList(labels).indexOf(chosen)).name();
            Path dir = p.standardsDirectory();
            if (Files.exists(dir.resolve(de.devtoolbox.tools.standards.StandardsTool.INDEX))
                    && JOptionPane.showConfirmDialog(parent, "Es gibt bereits Standards (ggf. bearbeitet). Ersetzen?\n"
                            + "Die bisherigen bleiben als Sicherung „standards-<Zeitpunkt>“ im Projektordner.",
                    "Standards erzeugen", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
                return;
            }
            de.devtoolbox.tools.standards.StandardsTool.Summary s = de.devtoolbox.tools.standards.StandardsTool
                    .generate(source, example, dir, p.label(), m -> { });
            String text = "Standards erzeugt: " + s.templates() + " Vorlagen und " + s.baseTypes()
                    + " Basisklassen aus „" + s.example() + "“.\n" + dir
                    + (s.backup() == null ? "" : "\nSicherung der bisherigen: " + s.backup().getFileName())
                    + "\n\nDu kannst STANDARDS.md und die Vorlagen jetzt überarbeiten. Ordner öffnen?";
            if (JOptionPane.showConfirmDialog(parent, text, "Standards erzeugen", JOptionPane.YES_NO_OPTION)
                    == JOptionPane.YES_OPTION) {
                Desktop.getDesktop().open(dir.toFile());
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, "Standards konnten nicht erzeugt werden:\n" + ex.getMessage());
        }
    }

    /**
     * Deletes the project folder after a confirmation that names what is lost. Code, reference and target model
     * outside the folder are not touched.
     */
    private static boolean delete(Component parent, MigrationProject project) throws Exception {
        List<Path> files;
        try (java.util.stream.Stream<Path> walk = Files.walk(project.folder())) {
            files = walk.filter(Files::isRegularFile).toList();
        }
        List<String> users = MigrationProject.all().stream()
                .filter(p -> !p.id().equals(project.id()) && p.referenceLabel().equals(project.label()))
                .map(MigrationProject::label).toList();
        StringBuilder text = new StringBuilder("Migrationsprojekt „" + project.label() + "“ löschen?\n\n")
                .append("Gelöscht wird der Ordner\n").append(project.folder()).append("\nmit ").append(files.size())
                .append(files.size() == 1 ? " Datei" : " Dateien").append(" (projekt.json, Schema-Beschreibung, Standards …).\n")
                .append("Code, Referenzprojekt und Zielmodell außerhalb dieses Ordners bleiben erhalten.");
        if (!users.isEmpty()) {
            text.append("\n\nAchtung: Als Referenzprojekt verwendet von\n  ").append(String.join("\n  ", users))
                    .append("\nDort ist die Referenz danach nicht mehr konfiguriert.");
        }
        int answer = JOptionPane.showConfirmDialog(parent, text.toString(), "Migrationsprojekt löschen",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return false;
        }
        deleteFolder(project.folder());
        return true;
    }

    static void deleteFolder(Path folder) throws java.io.IOException {
        try (java.util.stream.Stream<Path> walk = Files.walk(folder)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }

    private static List<Param> params(boolean create, List<MigrationProject> others) {
        List<Option> references = new ArrayList<>();
        references.add(new Option("", "– keins (Pfad unten angeben) –"));
        others.forEach(p -> references.add(new Option(p.id(), p.label())));
        List<Param> params = new ArrayList<>();
        if (create) {
            params.add(Param.text(ID, "Id", "", true, "Ordnername, Kleinbuchstaben, z. B. vhv – nicht mehr änderbar"));
        }
        params.add(Param.text("name", "Kurzname", "", true, "z. B. VHV"));
        params.add(Param.text("produkt", "Produkt", "", false, "z. B. Hausrat"));
        params.add(Param.text("sparte", "Sparte", "", false, "z. B. Sachversicherung, Haftpflichtversicherung"));
        params.add(Param.directory("root", "Code dieser Migration", false,
                "Migrationsmodul (Zielprojekt der Implementierung). Wird aus Sparte und Id vorbelegt: Modul mit der Id "
                        + "im Namen im Sparten-Projekt unter C:\\firma-develop (firma-haftpflichtvertrag, firma-sachvertrag)"));
        params.add(new Param(REFERENCE_PROJECT, "Referenzprojekt", Param.Type.CHOICE, "", false,
                "Bereits umgesetzte Migration, deren Code für die Spezifikation durchsucht wird", List.copyOf(references)));
        params.add(Param.directory(REFERENCE_PATH, "Referenz als Pfad", false,
                "Nur wenn oben kein Referenzprojekt gewählt ist: Code der Referenz direkt angeben"));
        params.add(Param.directory("ddl", "Schema-Beschreibung (DDL)", false,
                "Ordner oder Datei mit .ddl/.sql; Standard: Ordner ddl im Projekt (Import über „Schema importieren…“)"));
        params.add(Param.directory("zielmodell", "Zielmodell (JSON je Entität)", false,
                "Ordner mit einer JSON-Datei je Entität – Pflicht für die Phase Process"));
        params.add(Param.directory("mappingvorlagen", "Mapping-Vorlagen", false,
                "Ordner mit einer Vorlage der Mapping-Dokumentation je Entität (Confluence-Tabelle) – für den Job Mapping-Dokumentation"));
        params.add(Param.text("beschreibung", "Beschreibung", "", false, "Optional"));
        return params;
    }

    private static Map<String, String> initialValues(MigrationProject p, List<MigrationProject> others) {
        Map<String, String> v = new HashMap<>();
        if (p == null) {
            for (String key : List.of(ID, "name", "produkt", "sparte", "root", REFERENCE_PATH, "ddl", "zielmodell", "mappingvorlagen", "beschreibung")) {
                v.put(key, "");
            }
            v.put(REFERENCE_PROJECT, "");
            return v;
        }
        v.put("name", p.name());
        v.put("produkt", p.produkt());
        v.put("sparte", p.sparte());
        v.put("root", p.root() == null ? "" : p.root().toString());
        String referenceId = others.stream().filter(o -> o.label().equals(p.referenceLabel())).map(MigrationProject::id)
                .findFirst().orElse("");
        v.put(REFERENCE_PROJECT, referenceId);
        v.put(REFERENCE_PATH, referenceId.isEmpty() && p.reference() != null ? p.reference().toString() : "");
        v.put("ddl", p.ddl() == null ? "" : p.ddl().toString());
        v.put("zielmodell", p.targetModel() == null ? "" : p.targetModel().toString());
        v.put("mappingvorlagen", p.mappingTemplates() == null ? "" : p.mappingTemplates().toString());
        v.put("beschreibung", p.description());
        return v;
    }

    private static Path pathOrDerived(String value, Path derived) {
        Path p = pathOrNull(value);
        return p != null ? p : derived;
    }

    /** Code of the reference as currently entered: chosen project's code, else the path. */
    private static Path referencePath(ParamForm form, List<MigrationProject> others) {
        String id = form.value(REFERENCE_PROJECT);
        if (id != null && !id.isBlank()) {
            return others.stream().filter(o -> o.id().equals(id)).map(MigrationProject::root).findFirst().orElse(null);
        }
        return pathOrNull(form.value(REFERENCE_PATH));
    }

    private static String save(Path projectsDir, MigrationProject project, Map<String, String> values,
                               String entities) throws Exception {
        String id = project != null ? project.id() : values.getOrDefault(ID, "").strip().toLowerCase();
        if (id.isEmpty() || !id.matches("[a-z0-9][a-z0-9_-]*")) {
            throw new IllegalArgumentException("Id fehlt oder ungültig (Kleinbuchstaben, Ziffern, - und _).");
        }
        if (values.getOrDefault("name", "").isBlank()) {
            throw new IllegalArgumentException("Kurzname fehlt.");
        }
        Path folder = project != null ? project.folder() : projectsDir.resolve(id);
        Path file = folder.resolve("projekt.json");
        if (project == null && Files.exists(folder)) {
            throw new IllegalArgumentException("Ein Migrationsprojekt mit der Id '" + id + "' gibt es schon.");
        }
        Map<String, Object> json = new LinkedHashMap<>();
        Map<String, String> old = Files.exists(file) ? Json.parse(Files.readString(file)) : Map.of();
        json.putAll(old); // keep entries this form does not know
        json.put("name", values.get("name").strip());
        json.put("sparte", values.getOrDefault("sparte", "").strip());
        json.put("produkt", values.getOrDefault("produkt", "").strip());
        // paths equal to the derived ones (ProjectLayout) are not stored, so they follow the layout
        String sparte = values.getOrDefault("sparte", "").strip();
        json.put("root", unlessDerived(values.get("root"), de.devtoolbox.core.ProjectLayout.root(sparte, id))
                ? keep(folder, old.get("root"), relative(folder, values.get("root"))) : "");
        String reference = values.getOrDefault(REFERENCE_PROJECT, "");
        json.put("referenz", !reference.isBlank() ? reference
                : keep(folder, old.get("referenz"), relative(folder, values.get(REFERENCE_PATH))));
        String ddl = keep(folder, old.get("ddl"), relative(folder, values.get("ddl")));
        json.put("ddl", ddl.isEmpty() && project == null ? "ddl" : ddl);
        // a path equal to the one derived from root is not stored, so it follows root
        Path designer = de.devtoolbox.core.ProjectLayout.designer(pathOrNull(values.get("root")), sparte);
        json.put("zielmodell", unlessDerived(values.get("zielmodell"), de.devtoolbox.core.ProjectLayout.targetModelIn(designer))
                ? keep(folder, old.get("zielmodell"), relative(folder, values.get("zielmodell"))) : "");
        json.put("mappingvorlagen", unlessDerived(values.get("mappingvorlagen"), de.devtoolbox.core.ProjectLayout.mappingTemplatesIn(designer))
                ? keep(folder, old.get("mappingvorlagen"), relative(folder, values.get("mappingvorlagen"))) : "");
        json.put("beschreibung", values.getOrDefault("beschreibung", "").strip());
        json.put(MigrationProject.ENTITIES_KEY, String.join(", ", MigrationProject.parseEntities(entities)));

        Files.createDirectories(folder);
        if ("ddl".equals(json.get("ddl"))) {
            Files.createDirectories(folder.resolve("ddl"));
        }
        Files.writeString(file, Json.write(json), StandardCharsets.UTF_8);
        return id;
    }

    /** Keeps the previous spelling (e.g. "../../testprojekt") if it still points to the same place. */
    private static Path pathOrNull(String value) {
        try {
            return value == null || value.isBlank() ? null : Path.of(value.strip());
        } catch (java.nio.file.InvalidPathException e) {
            return null;
        }
    }

    private static String string(Path path) {
        return path == null ? "" : path.toString();
    }

    /** True if {@code value} is set and differs from the derived path (then it is an explicit setting). */
    private static boolean unlessDerived(String value, Path derived) {
        if (value == null || value.isBlank()) {
            return false;
        }
        Path given = pathOrNull(value);
        return derived == null || given == null
                || !given.toAbsolutePath().normalize().equals(derived.toAbsolutePath().normalize());
    }

    private static String keep(Path folder, String previous, String now) {
        if (previous == null || previous.isBlank() || now.isEmpty()) {
            return now;
        }
        Path a = folder.resolve(previous.strip()).toAbsolutePath().normalize();
        Path b = folder.resolve(now).toAbsolutePath().normalize();
        return a.equals(b) ? previous.strip() : now;
    }

    /** Paths inside the project folder are stored relative (e.g. "ddl"), others absolute with forward slashes. */
    static String relative(Path folder, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        Path path = folder.resolve(value.strip()).toAbsolutePath().normalize();
        Path base = folder.toAbsolutePath().normalize();
        return (path.startsWith(base) ? base.relativize(path).toString() : path.toString()).replace('\\', '/');
    }
}
