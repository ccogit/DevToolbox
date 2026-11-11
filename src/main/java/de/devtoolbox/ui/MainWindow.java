package de.devtoolbox.ui;

import de.devtoolbox.core.Config;
import de.devtoolbox.core.Job;
import de.devtoolbox.core.Json;
import de.devtoolbox.core.Keys;
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
    /** Models of the selections; the boxes themselves are not shown (header pill and pipeline link instead). */
    private final JComboBox<Job> jobBox;
    private final JComboBox<MigrationProject> projectBox;
    private final InfoButton projectInfo = new InfoButton("Migrationsprojekt");
    private final JMenuItem schemaButton = new JMenuItem("Schema importieren …");
    private final JMenuItem editProjectButton = new JMenuItem("Projekt bearbeiten …");
    private final JMenuItem newProjectButton = new JMenuItem("Neues Migrationsprojekt …");
    private boolean reloadingProjects;
    private final InfoButton description = new InfoButton("Job");
    private final JPanel formHost = new WidthTrackingPanel();
    private final JButton runButton = new JButton("▶  Ausführen");
    private final JButton folderButton = new JButton("Ordner öffnen");
    private final JLabel projectPill = new JLabel();
    private final JLabel pipelineLink = new JLabel();
    private Console console;
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
            new MigrationProject("", "– kein Migrationsprojekt (Pfade manuell) –", "", "", null, null, "", null, null, null,
                    "", null);

    private MainWindow(List<Job> jobs) {
        List<MigrationProject> projects = new java.util.ArrayList<>(List.of(NO_PROJECT));
        projects.addAll(MigrationProject.all());
        projectBox = new JComboBox<>(projects.toArray(MigrationProject[]::new));
        String lastProject = PREFS.get(Keys.PROJECT, "");
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

        jobBox = new JComboBox<>(jobs.toArray(Job[]::new));
        jobBox.addActionListener(e -> select((Job) jobBox.getSelectedItem(), null));

        runButton.addActionListener(e -> run());
        runButton.putClientProperty("JButton.buttonType", "default");
        runButton.setFont(UiTheme.text(Font.BOLD, 14f));
        runButton.setPreferredSize(new Dimension(150, 40));
        folderButton.addActionListener(e -> open(lastFolder));
        folderButton.setEnabled(false);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UiTheme.BACKGROUND);
        root.add(content(), BorderLayout.CENTER);
        // the console sits below the cards, above the footer - always visible
        folderButton.putClientProperty("JButton.buttonType", "borderless");
        folderButton.setFont(UiTheme.text(Font.PLAIN, 12f));
        console = new Console(folderButton);
        JPanel consoleBox = Ui.transparent(new BorderLayout());
        consoleBox.setBorder(new javax.swing.border.EmptyBorder(0, 64, 18, 64));
        consoleBox.add(console);
        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(UiTheme.BACKGROUND);
        south.add(consoleBox, BorderLayout.NORTH);
        south.add(footer(), BorderLayout.SOUTH);
        root.add(south, BorderLayout.SOUTH);

        frame.setJMenuBar(header());
        frame.getRootPane().putClientProperty("JRootPane.titleBarShowTitle", false);
        frame.getRootPane().putClientProperty("JRootPane.titleBarShowIcon", false);
        frame.getRootPane().putClientProperty("JRootPane.titleBarBackground", UiTheme.HEADER);
        frame.setContentPane(root);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        // Strg+Enter runs the job (Enter alone stays with the text fields)
        frame.getRootPane().registerKeyboardAction(e -> {
                    if (runButton.isEnabled()) {
                        run();
                    }
                }, KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, java.awt.event.InputEvent.CTRL_DOWN_MASK),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        frame.setSize(Math.min(1360, screen.width), Math.min(1000, screen.height));
        frame.setMinimumSize(new Dimension(980, 640));
        frame.setLocationRelativeTo(null);

        String lastJob = PREFS.get("job", "");
        Job initial = jobs.stream().filter(j -> j.id().equals(lastJob)).findFirst().orElse(jobs.getFirst());
        jobBox.setSelectedItem(initial);
        if (form == null) {
            select(initial, null); // no change event if the item was already selected
        }
        updateProjectInfo();
        append("Bereit für die Jobausführung – „Ausführen“ (Strg+Enter).");
    }

    // ---- layout ----

    /** Header in the title bar: logo + version, project pill (selection and project actions), status. */
    private JMenuBar header() {
        // glue in the menu bar lets FlatLaf stretch it over the whole title bar, so the project sits in the middle
        JMenuBar bar = new JMenuBar();
        bar.setOpaque(false);
        bar.setBorder(new javax.swing.border.EmptyBorder(10, 14, 10, 14));

        JPanel logo = Ui.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        logo.add(Ui.logo(34));
        logo.add(Box.createHorizontalStrut(10));
        JPanel name = Ui.transparent(new GridLayout(2, 1, 0, 0));
        JLabel title = new JLabel("<html><b>DEV</b><b><font color='#34C77B'>TOOLBOX</font></b></html>");
        title.setFont(UiTheme.text(Font.BOLD, 13f));
        title.setForeground(UiTheme.TEXT);
        name.add(title);
        JLabel suite = Ui.muted("E N G I N E E R I N G   S U I T E", 7f, true);
        suite.setForeground(new Color(0x4A5A70));
        name.add(suite);
        logo.add(name);
        logo.add(Box.createHorizontalStrut(18));
        String version = MainWindow.class.getPackage().getImplementationVersion();
        JPanel versionBox = Ui.transparent(new BorderLayout());
        versionBox.setBorder(new javax.swing.border.EmptyBorder(5, 0, 0, 0));
        versionBox.add(Ui.greenBadge("v" + (version == null ? "1.0.0" : version), true));
        logo.add(versionBox);
        bar.add(logo);
        bar.add(Box.createHorizontalGlue());

        JPanel center = Ui.transparent(new FlowLayout(FlowLayout.CENTER, 8, 4));
        center.add(Ui.muted("Migrationsprojekt  /", 12.5f, true));
        projectPill.setFont(UiTheme.mono(12.5f));
        projectPill.setForeground(UiTheme.TEXT);
        projectPill.setBorder(new javax.swing.border.EmptyBorder(5, 12, 5, 12));
        projectPill.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        projectPill.setToolTipText("Migrationsprojekt wählen, anlegen, bearbeiten, Schema importieren");
        projectPill.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                projectMenu().show(projectPill, 0, projectPill.getHeight() + 4);
            }
        });
        center.add(pill(projectPill));
        bar.add(center);
        bar.add(Box.createHorizontalGlue());

        JPanel status = Ui.transparent(new FlowLayout(FlowLayout.RIGHT, 0, 4));
        status.add(projectInfo);
        bar.add(status);
        return bar;
    }

    /** Rounded, green-tinted frame around the project name. */
    private static JComponent pill(JLabel label) {
        Ui.Card pill = new Ui.Card(new BorderLayout(), false, 28) {
            @Override
            protected void paintComponent(Graphics g) {
                fill = UiTheme.ACCENT_TINT;
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UiTheme.ACCENT_BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 28, 28);
                g2.dispose();
            }
        };
        pill.add(label);
        return pill;
    }

    private JPopupMenu projectMenu() {
        JPopupMenu menu = new JPopupMenu();
        ButtonGroup group = new ButtonGroup();
        for (int i = 0; i < projectBox.getItemCount(); i++) {
            MigrationProject p = projectBox.getItemAt(i);
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(p == NO_PROJECT ? NO_PROJECT.name() : p.label(),
                    p == project());
            item.addActionListener(e -> projectBox.setSelectedItem(p));
            group.add(item);
            menu.add(item);
        }
        menu.addSeparator();
        menu.add(newProjectButton);
        menu.add(editProjectButton);
        menu.add(schemaButton);
        return menu;
    }

    private JPopupMenu jobMenu() {
        JPopupMenu menu = new JPopupMenu();
        ButtonGroup group = new ButtonGroup();
        for (int i = 0; i < jobBox.getItemCount(); i++) {
            Job j = jobBox.getItemAt(i);
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(j.name(), j == job);
            item.setToolTipText(j.description());
            item.addActionListener(e -> jobBox.setSelectedItem(j));
            group.add(item);
            menu.add(item);
        }
        return menu;
    }

    /** "Pipeline: <Job>" – opens the job selection. */
    private JComponent pipeline() {
        JPanel row = Ui.transparent(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        pipelineLink.setFont(UiTheme.mono(12.5f));
        pipelineLink.setForeground(UiTheme.MUTED);
        pipelineLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        pipelineLink.setToolTipText("Job (Pipeline) wählen");
        for (var l : pipelineLink.getMouseListeners()) {
            pipelineLink.removeMouseListener(l);
        }
        pipelineLink.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                jobMenu().show(pipelineLink, 0, pipelineLink.getHeight() + 4);
            }

            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                pipelineLink.setForeground(UiTheme.TEXT);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                pipelineLink.setForeground(UiTheme.MUTED);
            }
        });
        row.add(pipelineLink);
        description.setForeground(UiTheme.MUTED);
        row.add(description);
        return row;
    }

    /** The job's cards - scrollable, with generous margins. */
    private JComponent content() {
        JPanel column = new WidthTrackingPanel();
        column.setBackground(UiTheme.BACKGROUND);
        column.setBorder(new javax.swing.border.EmptyBorder(24, 64, 18, 64));
        formHost.setOpaque(false);
        column.add(formHost, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(column);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(UiTheme.BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    /** Save/load on the left, Strg + Enter and the run button on the right. */
    private JComponent footer() {
        JButton save = new JButton("⤓  Konfiguration speichern");
        save.addActionListener(e -> saveJson());
        JButton load = new JButton("⤒  Konfiguration laden");
        load.addActionListener(e -> loadJson());
        JPanel left = Ui.transparent(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.add(save);
        left.add(load);
        JPanel right = Ui.transparent(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.add(Ui.key("Strg"));
        right.add(Ui.muted("+", 12f, false));
        right.add(Ui.key("Enter"));
        right.add(Box.createHorizontalStrut(14));
        right.add(runButton);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(UiTheme.HEADER);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UiTheme.BORDER),
                new javax.swing.border.EmptyBorder(14, 22, 14, 26)));
        footer.add(left, BorderLayout.WEST);
        footer.add(right, BorderLayout.EAST);
        return footer;
    }

    // ---- behaviour ----

    /** Builds the form for {@code newJob}; {@code values} (e.g. from a JSON file) win over the remembered ones. */
    private void select(Job newJob, Map<String, String> values) {
        if (newJob == null) {
            return;
        }
        job = newJob;
        form = new ParamForm(job.id(), job.params(), false);
        // code of the migration entered by hand: target model and mapping templates from its designer-services module
        form.deriveFrom(Keys.TARGET, Map.of(
                Keys.TARGET_MODEL, t -> derived(t, de.devtoolbox.core.ProjectLayout::targetModel),
                Keys.MAPPING_TEMPLATES, t -> derived(t, de.devtoolbox.core.ProjectLayout::mappingTemplates)));
        form.setValues(project().values()); // the project's paths win over remembered ones …
        if (values != null) {               // … and a loaded file wins over both
            form.setValues(values);
        }
        description.setInfo(job.name(), job.description(), false);
        description.setForeground(UiTheme.MUTED);
        // "Spezifikation aus Referenzprojekt (je Phase, fachliche Entität)" → "Spezifikation aus Referenzprojekt"
        String shortName = job.name().replaceFirst("\\s*\\(.*\\)\\s*$", "");
        pipelineLink.setText("Pipeline: " + shortName + "  ⌄");
        formHost.removeAll();
        formHost.add(new JobView(form, "Entität, Umfang & Phasen", pipeline()), BorderLayout.CENTER);
        formHost.revalidate();
        formHost.repaint();
        PREFS.put("job", job.id());
    }

    private static String derived(String root, java.util.function.Function<Path, Path> derive) {
        try {
            Path p = root == null || root.isBlank() ? null : derive.apply(Path.of(root.strip()));
            return p == null ? "" : p.toString();
        } catch (java.nio.file.InvalidPathException e) {
            return "";
        }
    }

    private MigrationProject project() {
        return (MigrationProject) projectBox.getSelectedItem();
    }

    private void projectChanged() {
        PREFS.put(Keys.PROJECT, project().id());
        if (form != null) {
            form.setValues(project().values());
        }
        updateProjectInfo();
    }

    private void updateProjectInfo() {
        MigrationProject p = project();
        projectPill.setText("<html><font color='#34C77B'>●</font>&nbsp; "
                + (p == NO_PROJECT ? "kein Projekt" : p.label()) + "&nbsp; <font color='#8B96A5'>⌄</font></html>");
        schemaButton.setEnabled(p != NO_PROJECT);
        editProjectButton.setEnabled(p != NO_PROJECT);
        if (p == NO_PROJECT) {
            projectInfo.setInfo("Kein Migrationsprojekt", MigrationProject.directory() == null
                    ? "Keine Migrationsprojekte gefunden (Ordner 'migrationsprojekte')."
                    : "Pfade werden in der Job-Konfiguration manuell angegeben.", false);
            return;
        }
        List<String> parts = new java.util.ArrayList<>();
        if (!p.sparte().isEmpty()) {
            parts.add(p.sparte());
        }
        parts.add(schemaSummary(p));
        parts.add(targetModelSummary(p));
        parts.add(mappingTemplatesSummary(p));
        parts.add(standardsSummary(p));
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
        if (!p.description().isEmpty()) {
            parts.add("<i>" + p.description() + "</i>");
        }
        // bold parts are problems (missing schema, reference …) – they turn the symbol into a warning
        projectInfo.setInfo(p.label(), String.join("<br>", parts), parts.stream().anyMatch(t -> t.contains("<b>")));
    }

    /** e.g. "Standards: 8 Vorlagen (Vertrag)". */
    private static String standardsSummary(MigrationProject p) {
        if (!Files.exists(p.standardsDirectory().resolve(de.devtoolbox.tools.standards.StandardsTool.INDEX))) {
            return "keine Standards (Bearbeiten… → Standards erzeugen…)";
        }
        try {
            de.devtoolbox.tools.standards.StandardsTool.Stored s =
                    de.devtoolbox.tools.standards.StandardsTool.load(p.standardsDirectory());
            return "Standards: " + s.rows().size() + " Vorlagen (" + s.example() + ")";
        } catch (Exception e) {
            return "<b>Standards nicht lesbar</b>";
        }
    }

    /** e.g. "Zielmodell: 12 Entitäten" – JSON files in the project's target model folder. */
    private static String targetModelSummary(MigrationProject p) {
        if (p.targetModel() == null) {
            return "kein Zielmodell (Bearbeiten…)";
        }
        try {
            int n = de.devtoolbox.tools.codecollector.TargetModel.entityCount(p.targetModel());
            return n == 0 ? "<b>Zielmodell leer/fehlt:</b> " + p.targetModel() : "Zielmodell: " + n + " Entitäten";
        } catch (Exception e) {
            return "<b>Zielmodell nicht lesbar</b>";
        }
    }

    /** e.g. "Mapping-Vorlagen: 12" – templates of the mapping documentation. */
    private static String mappingTemplatesSummary(MigrationProject p) {
        if (p.mappingTemplates() == null) {
            return "keine Mapping-Vorlagen (Bearbeiten…)";
        }
        try {
            int n = de.devtoolbox.tools.mappingdoc.MappingTemplate.all(p.mappingTemplates()).size();
            return n == 0 ? "<b>Mapping-Vorlagen leer/fehlen:</b> " + p.mappingTemplates() : "Mapping-Vorlagen: " + n;
        } catch (Exception e) {
            return "<b>Mapping-Vorlagen nicht lesbar</b>";
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
                                + " enthält kein CREATE TABLE / CREATE VIEW. Trotzdem übernehmen?", "Schema importieren",
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
            reloadProjects(p == NO_PROJECT ? "" : p.id()); // standards may have been generated
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
        values.put(Keys.PROJECT, project().id());
        Config config = new Config(values);
        List<String> missing = config.missing(current.params());
        if (!missing.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Bitte ausfüllen: " + String.join(", ", missing));
            return;
        }
        form.remember();
        runButton.setEnabled(false);
        folderButton.setEnabled(false);
        console.clear();
        console.running();
        Thread.ofVirtual().start(() -> {
            long start = System.nanoTime();
            List<Path> files = List.of();
            try {
                files = current.run(config, this::append);
                append("Fertig in %,d ms.".formatted((System.nanoTime() - start) / 1_000_000));
                console.ready();
            } catch (Exception ex) {
                append("FEHLER: " + ex.getMessage());
                console.failed();
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
            String projectId = values.remove(Keys.PROJECT);
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
            content.put(Keys.PROJECT, project().id());
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
        String entity = values.getOrDefault(Keys.TERMS, values.getOrDefault(Keys.ENTITY, ""));
        List<String> names = Config.split(entity);
        if (!names.isEmpty()) {
            parts.add(names.getFirst());
        }
        if (values.containsKey(Keys.PHASES)) {
            List<String> phases = Config.split(values.get(Keys.PHASES));
            parts.add(phases.size() == 3 ? "alle-phasen" : String.join("-", phases));
        }
        if ("compact".equals(values.get(Keys.DETAIL))) {
            parts.add("kompakt");
        }
        return parts.stream().map(de.devtoolbox.tools.codecollector.CodeCollectorTool::slug).filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.joining("-")) + ".json";
    }

    private void append(String line) {
        console.append(line);
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
