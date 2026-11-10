package de.devtoolbox.ui;

import de.devtoolbox.core.Config;
import de.devtoolbox.core.Job;
import de.devtoolbox.core.Json;
import de.devtoolbox.core.MigrationProject;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * The single screen: migration project and job selection, the job's configuration (incl. the params of the tools it uses),
 * the run log, and loading/saving the configuration as JSON.
 */
public final class MainWindow {

    private static final Preferences PREFS = Preferences.userNodeForPackage(MainWindow.class);

    private final JFrame frame = new JFrame("DevToolbox");
    private final JComboBox<Job> jobBox;
    private final JComboBox<MigrationProject> projectBox;
    private final JLabel projectInfo = new JLabel();
    private final JButton schemaButton = new JButton("Schema importieren...");
    private final JButton editProjectButton = new JButton("Bearbeiten...");
    private final JButton newProjectButton = new JButton("Neu...");
    private boolean reloadingProjects;
    private final JLabel description = new JLabel();
    private final JPanel formHost = new WidthTrackingPanel();
    private final JTextArea log = new JTextArea(5, 60);
    private final JButton runButton = new JButton("Ausführen");
    private final JButton folderButton = new JButton("Ordner öffnen");
    private Job job;
    private ParamForm form;
    private Path lastFolder;

    public static void show(List<Job> jobs) {
        SwingUtilities.invokeLater(() -> {
            UiTheme.install();
            new MainWindow(jobs).frame.setVisible(true);
        });
    }

    /** Stands for "no project": paths are entered by hand. */
    private static final MigrationProject NO_PROJECT =
            new MigrationProject("", "– kein Migrationsprojekt (Pfade manuell) –", "", "", null, null, "", null, null, "", null);

    private MainWindow(List<Job> jobs) {
        List<MigrationProject> projects = new java.util.ArrayList<>(List.of(NO_PROJECT));
        projects.addAll(MigrationProject.all());
        projectBox = new JComboBox<>(projects.toArray(MigrationProject[]::new));
        projectBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int i, boolean sel, boolean focus) {
                String text = value == null ? "" : value == NO_PROJECT ? NO_PROJECT.name() : ((MigrationProject) value).label();
                return super.getListCellRendererComponent(l, text, i, sel, focus);
            }
        });
        String lastProject = PREFS.get("project", "");
        projects.stream().filter(p -> p.id().equals(lastProject)).findFirst().ifPresent(projectBox::setSelectedItem);
        projectBox.addActionListener(e -> {
            if (!reloadingProjects) {
                projectChanged();
            }
        });
        schemaButton.setToolTipText("DDL (*.sql, *.ddl) der Alt-Tabellen, aus denen die Entitäten gelesen werden, "
                + "in die Schema-Beschreibung des Migrationsprojekts übernehmen");
        schemaButton.addActionListener(e -> importSchema());
        editProjectButton.setToolTipText("projekt.json des gewählten Migrationsprojekts bearbeiten oder löschen");
        editProjectButton.addActionListener(e -> editProject(project()));
        newProjectButton.setToolTipText("Neues Migrationsprojekt anlegen");
        newProjectButton.addActionListener(e -> editProject(null));
        projectInfo.setForeground(UIManager.getColor("Label.disabledForeground"));
        Path projectDir = MigrationProject.directory();
        projectBox.setToolTipText(projectDir == null
                ? "Kein Ordner 'migrationsprojekte' gefunden" : "Definitionen aus " + projectDir);

        jobBox = new JComboBox<>(jobs.toArray(Job[]::new));
        jobBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int i, boolean sel, boolean focus) {
                return super.getListCellRendererComponent(l, value == null ? "" : ((Job) value).name(), i, sel, focus);
            }
        });
        jobBox.addActionListener(e -> select((Job) jobBox.getSelectedItem(), null));

        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        runButton.addActionListener(e -> run());
        folderButton.addActionListener(e -> open(lastFolder));
        folderButton.setEnabled(false);
        JButton load = new JButton("Konfiguration laden...");
        load.addActionListener(e -> loadJson());
        JButton save = new JButton("Konfiguration speichern...");
        save.addActionListener(e -> saveJson());

        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBorder(BorderFactory.createEmptyBorder(18, 20, 16, 20));
        root.add(top(), BorderLayout.NORTH);
        root.add(center(), BorderLayout.CENTER);
        root.add(bottom(load, save), BorderLayout.SOUTH);

        frame.setContentPane(root);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.getRootPane().setDefaultButton(runButton);
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        frame.setSize(Math.min(820, screen.width), Math.min(940, screen.height));
        frame.setLocationRelativeTo(null);

        String lastJob = PREFS.get("job", "");
        Job initial = jobs.stream().filter(j -> j.id().equals(lastJob)).findFirst().orElse(jobs.getFirst());
        jobBox.setSelectedItem(initial);
        if (form == null) {
            select(initial, null); // no change event if the item was already selected
        }
    }

    // ---- layout ----

    private JComponent top() {
        description.setForeground(UIManager.getColor("Label.disabledForeground"));
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 4, 12);
        panel.add(new JLabel("Migrationsprojekt:"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 4, 0);
        JPanel projectButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        projectButtons.add(newProjectButton);
        projectButtons.add(editProjectButton);
        projectButtons.add(schemaButton);
        JPanel projectRow = new JPanel(new BorderLayout(4, 0));
        projectRow.add(projectBox, BorderLayout.CENTER);
        projectRow.add(projectButtons, BorderLayout.EAST);
        panel.add(projectRow, c);
        c.gridy = 1;
        c.insets = new Insets(0, 2, 12, 0);
        panel.add(projectInfo, c);
        c.gridx = 0;
        c.gridy = 2;
        c.weightx = 0;
        c.insets = new Insets(0, 0, 8, 12);
        panel.add(new JLabel("Job:"), c);
        c.gridx = 1;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 8, 0);
        panel.add(jobBox, c);
        c.gridy = 3;
        c.insets = new Insets(0, 2, 0, 0);
        panel.add(description, c);
        updateProjectInfo();
        return panel;
    }

    private JComponent center() {
        JScrollPane scroll = new JScrollPane(formHost);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Konfiguration"),
                BorderFactory.createEmptyBorder(6, 8, 8, 8)));
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private JComponent bottom(JButton load, JButton save) {
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.setBorder(BorderFactory.createTitledBorder("Protokoll"));
        logPanel.add(new JScrollPane(log), BorderLayout.CENTER);

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.add(load);
        left.add(Box.createHorizontalStrut(10));
        left.add(save);
        left.add(Box.createHorizontalStrut(10));
        left.add(folderButton);
        JPanel buttons = new JPanel(new BorderLayout());
        buttons.add(left, BorderLayout.WEST);
        buttons.add(runButton, BorderLayout.EAST);

        JPanel panel = new JPanel(new BorderLayout(0, 14));
        panel.add(logPanel, BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    // ---- behaviour ----

    /** Builds the form for {@code newJob}; {@code values} (e.g. from a JSON file) win over the remembered ones. */
    private void select(Job newJob, Map<String, String> values) {
        if (newJob == null) {
            return;
        }
        job = newJob;
        form = new ParamForm(job.id(), job.params());
        form.setValues(project().values()); // the project's paths win over remembered ones …
        if (values != null) {               // … and a loaded file wins over both
            form.setValues(values);
        }
        description.setText("<html>" + job.description() + "</html>");
        formHost.removeAll();
        formHost.add(form, BorderLayout.CENTER);
        formHost.revalidate();
        formHost.repaint();
        PREFS.put("job", job.id());
    }

    private MigrationProject project() {
        return (MigrationProject) projectBox.getSelectedItem();
    }

    private void projectChanged() {
        PREFS.put("project", project().id());
        if (form != null) {
            form.setValues(project().values());
        }
        updateProjectInfo();
    }

    private void updateProjectInfo() {
        MigrationProject p = project();
        schemaButton.setEnabled(p != NO_PROJECT);
        editProjectButton.setEnabled(p != NO_PROJECT);
        if (p == NO_PROJECT) {
            projectInfo.setText(MigrationProject.directory() == null
                    ? "Keine Migrationsprojekte gefunden (Ordner 'migrationsprojekte')." : " ");
            return;
        }
        List<String> parts = new java.util.ArrayList<>();
        if (!p.sparte().isEmpty()) {
            parts.add(p.sparte());
        }
        parts.add(schemaSummary(p));
        parts.add(targetModelSummary(p));
        if (p.reference() == null && !p.referenceLabel().isEmpty()) {
            parts.add("<b>Referenzprojekt „" + p.referenceLabel() + "“ nicht gefunden</b> (Bearbeiten…)");
        } else if (p.reference() == null) {
            parts.add("<b>Referenzprojekt nicht konfiguriert</b> (Bearbeiten…)");
        } else if (!Files.isDirectory(p.reference())) {
            parts.add("<b>Referenzprojekt fehlt:</b> " + p.reference());
        } else {
            parts.add("Referenz: " + (p.referenceLabel().equals(p.reference().toString())
                    ? p.reference() : p.referenceLabel() + " · " + p.reference()));
        }
        projectInfo.setText("<html>" + String.join(" · ", parts) + "</html>");
        projectInfo.setToolTipText(p.description().isEmpty() ? null : p.description());
    }

    /** e.g. "Zielmodell: 12 Entitäten" – JSON files in the project's target model folder. */
    private static String targetModelSummary(MigrationProject p) {
        if (p.targetModel() == null) {
            return "kein Zielmodell (Bearbeiten…)";
        }
        try {
            int n = de.devtoolbox.tools.codecollector.TargetModel.files(p.targetModel()).size();
            return n == 0 ? "<b>Zielmodell leer/fehlt:</b> " + p.targetModel() : "Zielmodell: " + n + " Entitäten";
        } catch (Exception e) {
            return "<b>Zielmodell nicht lesbar</b>";
        }
    }

    /** e.g. "Schema: 4 Tabellen, 21 Spalten" – read from the project's DDL. */
    private static String schemaSummary(MigrationProject p) {
        if (p.ddl() == null || !Files.exists(p.ddl())) {
            return "<b>kein Schema</b> (Schema importieren…)";
        }
        try {
            List<de.devtoolbox.tools.codecollector.DdlReader.Table> tables =
                    de.devtoolbox.tools.codecollector.DdlReader.read(p.ddl());
            if (tables.isEmpty()) {
                return "<b>kein Schema</b> (Schema importieren…)";
            }
            int columns = tables.stream().mapToInt(t -> t.columns().size()).sum();
            return "Schema: " + tables.size() + " Tabellen, " + columns + " Spalten";
        } catch (Exception e) {
            return "<b>Schema nicht lesbar:</b> " + e.getMessage();
        }
    }

    /**
     * Copies DDL files into the schema folder of the selected migration project – the folder next to its
     * projekt.json – after checking that they contain table definitions.
     */
    private void importSchema() {
        MigrationProject p = project();
        if (p == NO_PROJECT) {
            return;
        }
        JFileChooser chooser = new JFileChooser(PREFS.get("schemaDir", ""));
        chooser.setDialogTitle("Schema (DDL) für " + p.label() + " importieren");
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter("DDL / SQL (*.sql, *.ddl)", "sql", "ddl"));
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path target = p.schemaDirectory();
        try {
            Files.createDirectories(target);
            int imported = 0;
            for (java.io.File selected : chooser.getSelectedFiles()) {
                Path source = selected.toPath();
                PREFS.put("schemaDir", source.getParent().toString());
                List<de.devtoolbox.tools.codecollector.DdlReader.Table> tables =
                        de.devtoolbox.tools.codecollector.DdlReader.read(source);
                if (tables.isEmpty() && JOptionPane.showConfirmDialog(frame, source.getFileName()
                                + " enthält kein CREATE TABLE. Trotzdem übernehmen?", "Schema importieren",
                        JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
                    continue;
                }
                Path dest = target.resolve(source.getFileName());
                if (Files.exists(dest) && !Files.isSameFile(source, dest) && JOptionPane.showConfirmDialog(frame,
                        dest.getFileName() + " ist im Schema bereits vorhanden. Ersetzen?", "Schema importieren",
                        JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
                    continue;
                }
                if (!Files.exists(dest) || !Files.isSameFile(source, dest)) {
                    Files.copy(source, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                int columns = tables.stream().mapToInt(t -> t.columns().size()).sum();
                append("Schema importiert: " + source.getFileName() + " → " + dest + " (" + tables.size()
                        + " Tabellen, " + columns + " Spalten: "
                        + tables.stream().map(de.devtoolbox.tools.codecollector.DdlReader.Table::name)
                        .collect(java.util.stream.Collectors.joining(", ")) + ")");
                imported++;
            }
            if (imported > 0) {
                p.ensureDdlEntry(target);
                reloadProjects(p.id());
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Import fehlgeschlagen:\n" + ex.getMessage());
        }
    }

    /** Opens the project editor; {@code null} creates a new project. Reloads and selects the saved project. */
    private void editProject(MigrationProject p) {
        ProjectEditor.Result result = ProjectEditor.open(frame, p == NO_PROJECT ? null : p);
        if (result == null) {
            return;
        }
        if (result.deleted()) {
            reloadProjects("");
            append("Migrationsprojekt gelöscht: " + p.folder());
        } else {
            reloadProjects(result.id());
            append("Migrationsprojekt gespeichert: " + project().folder().resolve("projekt.json"));
        }
    }

    /** Re-reads the project definitions (e.g. after an import) and keeps the selection. */
    private void reloadProjects(String selectId) {
        reloadingProjects = true;
        try {
            projectBox.removeAllItems();
            projectBox.addItem(NO_PROJECT);
            MigrationProject.all().forEach(projectBox::addItem);
            for (int i = 0; i < projectBox.getItemCount(); i++) {
                if (projectBox.getItemAt(i).id().equals(selectId)) {
                    projectBox.setSelectedIndex(i);
                }
            }
        } finally {
            reloadingProjects = false;
        }
        projectChanged();
    }

    private void run() {
        Job current = job;
        Map<String, String> values = new LinkedHashMap<>(form.values());
        values.put(MigrationProject.KEY, project().id());
        Config config = new Config(values);
        List<String> missing = config.missing(current.params());
        if (!missing.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Bitte ausfüllen: " + String.join(", ", missing));
            return;
        }
        form.remember();
        runButton.setEnabled(false);
        folderButton.setEnabled(false);
        log.setText("");
        Thread.ofVirtual().start(() -> {
            long start = System.nanoTime();
            List<Path> files = List.of();
            try {
                files = current.run(config, this::append);
                append("Fertig in %,d ms.".formatted((System.nanoTime() - start) / 1_000_000));
            } catch (Exception ex) {
                append("FEHLER: " + ex.getMessage());
            } finally {
                Path folder = files.isEmpty() ? null : files.getFirst().getParent();
                SwingUtilities.invokeLater(() -> {
                    runButton.setEnabled(true);
                    lastFolder = folder;
                    folderButton.setEnabled(folder != null);
                });
            }
        });
    }

    /** The file's "job" entry selects the job; all other entries fill the form. */
    private void loadJson() {
        JFileChooser chooser = jsonChooser();
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path path = chooser.getSelectedFile().toPath();
        try {
            Map<String, String> values = new HashMap<>(Json.parse(Files.readString(path)));
            String jobId = values.remove("job");
            String projectId = values.remove(MigrationProject.KEY);
            if (projectId != null) {
                for (int i = 0; i < projectBox.getItemCount(); i++) {
                    if (projectBox.getItemAt(i).id().equalsIgnoreCase(projectId)) {
                        projectBox.setSelectedIndex(i);
                    }
                }
            }
            Job target = job;
            for (int i = 0; i < jobBox.getItemCount(); i++) {
                if (jobBox.getItemAt(i).id().equals(jobId)) {
                    target = jobBox.getItemAt(i);
                }
            }
            if (target != jobBox.getSelectedItem()) {
                jobBox.setSelectedItem(target); // fires select(target, null) …
            }
            select(target, values);              // … then apply the file's values
            PREFS.put("jsonDir", path.getParent().toString());
            append("Konfiguration geladen: " + path);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Konfiguration kann nicht geladen werden:\n" + ex.getMessage());
        }
    }

    private void saveJson() {
        JFileChooser chooser = jsonChooser();
        chooser.setSelectedFile(chooser.getCurrentDirectory().toPath().resolve(defaultConfigName()).toFile());
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path path = chooser.getSelectedFile().toPath();
        if (!path.getFileName().toString().toLowerCase().endsWith(".json")) {
            path = path.resolveSibling(path.getFileName() + ".json");
        }
        if (Files.exists(path) && JOptionPane.showConfirmDialog(frame, path.getFileName() + " existiert bereits. Überschreiben?",
                "Konfiguration speichern", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("job", job.id());
        if (project() != NO_PROJECT) {
            content.put(MigrationProject.KEY, project().id());
        }
        content.putAll(form.typedValues());
        try {
            Files.writeString(path, Json.write(content), StandardCharsets.UTF_8);
            PREFS.put("jsonDir", path.getParent().toString());
            append("Konfiguration gespeichert: " + path);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Speichern fehlgeschlagen:\n" + ex.getMessage());
        }
    }

    /** Opens in the project's configuration folder (falls back to the last used folder). */
    private static JFileChooser jsonChooser() {
        Path dir = configDirectory();
        String last = PREFS.get("jsonDir", "");
        JFileChooser chooser = new JFileChooser(dir != null ? dir.toFile() : last.isEmpty() ? null : new java.io.File(last));
        chooser.setFileFilter(new FileNameExtensionFilter("JSON-Konfiguration (*.json)", "json"));
        return chooser;
    }

    /**
     * {@code src/main/resources/konfigurationen} of the DevToolbox project (the folder that also holds
     * {@code migrationsprojekte}); without sources (jar only) {@code konfigurationen} next to {@code migrationsprojekte}.
     * Created on first use; {@code null} if neither can be determined.
     */
    static Path configDirectory() {
        Path projects = MigrationProject.directory();
        if (projects == null || projects.getParent() == null) {
            return null;
        }
        Path root = projects.getParent();
        Path resources = root.resolve("src/main/resources");
        Path dir = Files.isDirectory(resources) ? resources.resolve("konfigurationen") : root.resolve("konfigurationen");
        try {
            return Files.createDirectories(dir);
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /** e.g. "vgb-spezifikation-risiko-alle-phasen.json" or "plg-implementierung-risiko-read-process.json". */
    private String defaultConfigName() {
        Map<String, String> values = form.values();
        List<String> parts = new java.util.ArrayList<>();
        parts.add(project() == NO_PROJECT ? "ohne-projekt" : project().id());
        parts.add(job.fileTag());
        String entity = values.getOrDefault("terms", values.getOrDefault("entity", ""));
        List<String> names = Config.split(entity);
        if (!names.isEmpty()) {
            parts.add(names.getFirst());
        }
        if (values.containsKey("phases")) {
            List<String> phases = Config.split(values.get("phases"));
            parts.add(phases.size() == 3 ? "alle-phasen" : String.join("-", phases));
        }
        if ("compact".equals(values.get("detail"))) {
            parts.add("kompakt");
        }
        return parts.stream().map(de.devtoolbox.tools.codecollector.CodeCollectorTool::slug).filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.joining("-")) + ".json";
    }

    private void append(String line) {
        SwingUtilities.invokeLater(() -> log.append(line + "\n"));
    }

    private void open(Path path) {
        try {
            Desktop.getDesktop().open(path.toFile());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Kann nicht geöffnet werden: " + ex.getMessage());
        }
    }

    /** Lets the form shrink to the viewport width instead of forcing a horizontal scroll bar. */
    private static final class WidthTrackingPanel extends JPanel implements Scrollable {
        WidthTrackingPanel() {
            super(new BorderLayout());
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return visible.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
