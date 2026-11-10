package de.devtoolbox.ui;

import de.devtoolbox.core.Config;
import de.devtoolbox.core.Job;
import de.devtoolbox.core.Json;

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
 * The single screen: job selection, the job's configuration (incl. the params of the tools it uses),
 * the run log, and loading/saving the configuration as JSON.
 */
public final class MainWindow {

    private static final Preferences PREFS = Preferences.userNodeForPackage(MainWindow.class);

    private final JFrame frame = new JFrame("DevToolbox");
    private final JComboBox<Job> jobBox;
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

    private MainWindow(List<Job> jobs) {
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
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.add(new JLabel("Bitte wählen Sie einen Job aus:"), BorderLayout.WEST);
        row.add(jobBox, BorderLayout.CENTER);
        description.setForeground(UIManager.getColor("Label.disabledForeground"));
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.add(row, BorderLayout.NORTH);
        panel.add(description, BorderLayout.CENTER);
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
        if (values != null) {
            form.setValues(values);
        }
        description.setText("<html>" + job.description() + "</html>");
        formHost.removeAll();
        formHost.add(form, BorderLayout.CENTER);
        formHost.revalidate();
        formHost.repaint();
        PREFS.put("job", job.id());
    }

    private void run() {
        Job current = job;
        Config config = new Config(form.values());
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
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path path = chooser.getSelectedFile().toPath();
        if (!path.getFileName().toString().toLowerCase().endsWith(".json")) {
            path = path.resolveSibling(path.getFileName() + ".json");
        }
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("job", job.id());
        content.putAll(form.typedValues());
        try {
            Files.writeString(path, Json.write(content), StandardCharsets.UTF_8);
            PREFS.put("jsonDir", path.getParent().toString());
            append("Konfiguration gespeichert: " + path);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Speichern fehlgeschlagen:\n" + ex.getMessage());
        }
    }

    private static JFileChooser jsonChooser() {
        String dir = PREFS.get("jsonDir", "");
        JFileChooser chooser = new JFileChooser(dir.isEmpty() ? null : dir);
        chooser.setFileFilter(new FileNameExtensionFilter("JSON-Konfiguration (*.json)", "json"));
        return chooser;
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
