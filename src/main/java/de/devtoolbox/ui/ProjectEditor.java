package de.devtoolbox.ui;

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
        form.setValues(initialValues(project, others));

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
                result[0] = new Result(save(projectsDir, project, form.values()), false);
                dialog.dispose();
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
        if (project != null) {
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
            buttons.add(delete, BorderLayout.WEST);
        }
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));
        root.add(info, BorderLayout.NORTH);
        root.add(form, BorderLayout.CENTER);
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
                .append(files.size() == 1 ? " Datei" : " Dateien").append(" (projekt.json, Schema-Beschreibung …).\n")
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
                "Projektverzeichnis der Migrationsanwendung (Zielprojekt der Implementierung)"));
        params.add(new Param(REFERENCE_PROJECT, "Referenzprojekt", Param.Type.CHOICE, "", false,
                "Bereits umgesetzte Migration, deren Code für die Spezifikation durchsucht wird", List.copyOf(references)));
        params.add(Param.directory(REFERENCE_PATH, "Referenz als Pfad", false,
                "Nur wenn oben kein Referenzprojekt gewählt ist: Code der Referenz direkt angeben"));
        params.add(Param.directory("ddl", "Schema-Beschreibung (DDL)", false,
                "Ordner oder Datei mit .ddl/.sql; Standard: Ordner ddl im Projekt (Import über „Schema importieren…“)"));
        params.add(Param.directory("zielmodell", "Zielmodell (JSON je Entität)", false,
                "Ordner mit einer JSON-Datei je Entität – Pflicht für die Phase Process"));
        params.add(Param.text("beschreibung", "Beschreibung", "", false, "Optional"));
        return params;
    }

    private static Map<String, String> initialValues(MigrationProject p, List<MigrationProject> others) {
        Map<String, String> v = new HashMap<>();
        if (p == null) {
            for (String key : List.of(ID, "name", "produkt", "sparte", "root", REFERENCE_PATH, "ddl", "zielmodell", "beschreibung")) {
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
        v.put("beschreibung", p.description());
        return v;
    }

    private static String save(Path projectsDir, MigrationProject project, Map<String, String> values) throws Exception {
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
        json.put("root", keep(folder, old.get("root"), relative(folder, values.get("root"))));
        String reference = values.getOrDefault(REFERENCE_PROJECT, "");
        json.put("referenz", !reference.isBlank() ? reference
                : keep(folder, old.get("referenz"), relative(folder, values.get(REFERENCE_PATH))));
        String ddl = keep(folder, old.get("ddl"), relative(folder, values.get("ddl")));
        json.put("ddl", ddl.isEmpty() && project == null ? "ddl" : ddl);
        json.put("zielmodell", keep(folder, old.get("zielmodell"), relative(folder, values.get("zielmodell"))));
        json.put("beschreibung", values.getOrDefault("beschreibung", "").strip());

        Files.createDirectories(folder);
        if ("ddl".equals(json.get("ddl"))) {
            Files.createDirectories(folder.resolve("ddl"));
        }
        Files.writeString(file, Json.write(json), StandardCharsets.UTF_8);
        return id;
    }

    /** Keeps the previous spelling (e.g. "../../testprojekt") if it still points to the same place. */
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
