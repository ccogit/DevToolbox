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
            new MigrationProject("", "– kein Migrationsprojekt (Pfade manuell) –", "", "", null, null, "", null, "", null);

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
        projectBox.addActionListener(e -> projectChanged());
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
        panel.add(projectBox, c);
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
        if (p == NO_PROJECT) {
            projectInfo.setText(MigrationProject.directory() == null
                    ? "Keine Migrationsprojekte gefunden (Ordner 'migrationsprojekte')." : " ");
            return;
        }
        String sparte = p.sparte().isEmpty() ? "" : p.sparte() + " · ";
        String reference = p.reference() == null
                ? "<html>" + sparte + "<b>Referenzprojekt nicht konfiguriert</b> (referenz in "
                        + p.folder().resolve("projekt.json") + ")</html>"
                : !Files.isDirectory(p.reference())
                ? "<html>" + sparte + "<b>Referenzprojekt fehlt:</b> " + p.reference()
                        + (p.referenceLabel().equals(p.reference().toString()) ? "" : " (" + p.referenceLabel() + ")") + "</html>"
                : sparte + "Referenz: " + (p.referenceLabel().equals(p.reference().toString())
                        ? p.reference() : p.referenceLabel() + " · " + p.reference());
        projectInfo.setText(reference);
        projectInfo.setToolTipText(p.description().isEmpty() ? null : p.description());
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
