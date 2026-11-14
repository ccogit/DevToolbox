package de.devtoolbox.ui;

import de.devtoolbox.core.Job;

import javax.swing.Timer;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a finished run offers, shown between the head of the log and its lines: one copy button per Copilot prompt
 * (start, each stage, continuation), one link per produced file plus the run folder, and one link per answer file
 * of Copilot as soon as it lies in the download folder.
 */
final class RunResults extends JPanel {

    /** A log line "Prompt<what>: <text>"; the first one of a run is "Prompt: …". */
    private static final Pattern PROMPT_LINE = Pattern.compile("^Prompt([^:]*): (.+)$", Pattern.DOTALL);
    private static final Pattern STAGE = Pattern.compile("^Etappe (\\d+) von \\d+(.*)$");

    /** A Copilot prompt of the run: the label of its button and the text to copy. */
    record Prompt(String label, String text) {
    }

    /**
     * The prompts among the log lines of a run, in their order. The label says what the prompt is for: "Start" (or
     * "Etappe 1" if the run has stages), "Etappe 2", "Etappe 3 (Abschluss)", "bei UNVOLLSTÄNDIG" …
     */
    static List<Prompt> prompts(List<String> logLines) {
        List<String[]> found = new ArrayList<>();
        for (String line : logLines) {
            Matcher m = PROMPT_LINE.matcher(line);
            if (m.matches()) {
                found.add(new String[]{m.group(1).replaceFirst("^,", "").strip(), m.group(2).strip()});
            }
        }
        boolean staged = found.stream().anyMatch(p -> STAGE.matcher(p[0]).matches());
        List<Prompt> prompts = new ArrayList<>();
        for (String[] p : found) {
            Matcher stage = STAGE.matcher(p[0]);
            String label = p[0].isEmpty() ? (staged ? "Etappe 1" : "Start")
                    : stage.matches() ? "Etappe " + stage.group(1) + stage.group(2) : p[0];
            prompts.add(new Prompt(label, p[1]));
        }
        return prompts;
    }

    /**
     * The files the AI is asked to deliver and where the browser saves them.
     *
     * @param names  the file names, in the order they are delivered
     * @param folder the download folder
     * @param since  only files changed at or after this time (epoch millis) belong to the run
     */
    record Answers(List<String> names, Path folder, long since) {
        static final Answers NONE = new Answers(List.of(), null, 0);
    }

    /** The names after {@link Job#ANSWERS_PREFIX} in the log lines of a run; empty if the job expects no files. */
    static List<String> answerNames(List<String> logLines) {
        for (String line : logLines) {
            if (line.startsWith(Job.ANSWERS_PREFIX)) {
                return Arrays.stream(line.substring(Job.ANSWERS_PREFIX.length()).split(","))
                        .map(String::strip).filter(s -> !s.isEmpty()).toList();
            }
        }
        return List.of();
    }

    /**
     * The downloaded file for {@code name}: the browser keeps the name or, if it exists already, appends a counter
     * ("name (2).md"). Of the candidates changed at or after {@code since} the newest wins.
     */
    static Optional<Path> download(Path folder, String name, long since) {
        if (folder == null || !Files.isDirectory(folder)) {
            return Optional.empty();
        }
        int dot = name.lastIndexOf('.');
        String base = dot < 0 ? name : name.substring(0, dot);
        String ext = dot < 0 ? "" : name.substring(dot);
        Pattern pattern = Pattern.compile(Pattern.quote(base) + "(?: ?\\(\\d+\\))?" + Pattern.quote(ext),
                Pattern.CASE_INSENSITIVE);
        Path newest = null;
        long newestTime = Long.MIN_VALUE;
        try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, base + "*")) {
            for (Path file : files) {
                if (!pattern.matcher(file.getFileName().toString()).matches() || !Files.isRegularFile(file)) {
                    continue;
                }
                long time = Files.getLastModifiedTime(file).toMillis();
                if (time >= since && time > newestTime) {
                    newest = file;
                    newestTime = time;
                }
            }
        } catch (IOException | RuntimeException e) {
            return Optional.empty(); // unreadable folder: simply no link
        }
        return Optional.ofNullable(newest);
    }

    /** One button per expected answer; it becomes a link once the file is in the download folder. */
    private final Map<String, JButton> answerButtons = new LinkedHashMap<>();
    private final Answers answers;
    private final Timer watch = new Timer(2000, _ -> refreshAnswers());

    RunResults(List<Prompt> prompts, List<Path> files, Path folder, Consumer<Prompt> copy, Consumer<Path> open) {
        this(prompts, files, folder, Answers.NONE, copy, open);
    }

    /**
     * @param copy called with the prompt whose button was clicked
     * @param open called with the file or folder whose link was clicked
     */
    RunResults(List<Prompt> prompts, List<Path> files, Path folder, Answers answers, Consumer<Prompt> copy,
               Consumer<Path> open) {
        this.answers = answers;
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(0, 16, 6, 14));
        if (!prompts.isEmpty()) {
            JPanel row = row("Prompt kopieren:");
            for (Prompt prompt : prompts) {
                JButton button = new JButton(prompt.label());
                button.setName("prompt:" + prompt.label());
                button.setToolTipText(tip(prompt.text()));
                button.setFocusable(false);
                button.setMargin(new Insets(2, 8, 2, 8));
                button.setFont(UiTheme.text(Font.PLAIN, 12f));
                button.addActionListener(_ -> {
                    copy.accept(prompt);
                    button.setText("✓ " + prompt.label()); // shows how far the stages have got
                });
                row.add(button);
            }
            add(row);
        }
        if (!files.isEmpty() || folder != null) {
            JPanel row = row("Öffnen:");
            for (Path file : files) {
                row.add(link(file.getFileName().toString(), file, open));
            }
            if (folder != null) {
                row.add(link("Ordner", folder, open));
            }
            add(row);
        }
        if (!answers.names().isEmpty()) {
            JPanel row = row("Antworten von Copilot:");
            for (String name : answers.names()) {
                JButton button = new JButton(name);
                button.setName("answer:" + name);
                button.putClientProperty("JButton.buttonType", "borderless");
                button.setFocusable(false);
                button.setMargin(new Insets(2, 4, 2, 4));
                button.setFont(UiTheme.text(Font.PLAIN, 12f));
                button.addActionListener(_ -> download(answers.folder(), name, answers.since()).ifPresent(open));
                answerButtons.put(name, button);
                row.add(button);
            }
            add(row);
            refreshAnswers();
        }
        // the rows wrap: their height depends on the width
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                revalidate();
            }
        });
    }

    /** Turns the button of every answer that has arrived in the download folder into a link. */
    void refreshAnswers() {
        answerButtons.forEach((name, button) -> {
            Optional<Path> file = download(answers.folder(), name, answers.since());
            boolean there = file.isPresent();
            if (there == button.isEnabled() && button.getToolTipText() != null) {
                return;
            }
            button.setEnabled(there);
            button.setText(there ? "<html><u>" + name + "</u></html>" : name + " (fehlt noch)");
            button.setForeground(there ? UiTheme.ACCENT : UiTheme.MUTED);
            button.setCursor(Cursor.getPredefinedCursor(there ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            button.setToolTipText(there ? file.get().toString()
                    : "Noch nicht im Download-Ordner " + answers.folder() + " – erscheint, sobald die Datei dort liegt");
        });
    }

    /** Looks for the answers while the strip is shown. */
    @Override
    public void addNotify() {
        super.addNotify();
        if (!answerButtons.isEmpty()) {
            watch.start();
        }
    }

    @Override
    public void removeNotify() {
        watch.stop();
        super.removeNotify();
    }

    boolean isEmpty() {
        return getComponentCount() == 0;
    }

    private static JPanel row(String title) {
        JPanel row = Ui.transparent(new Wrap());
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JComponent label = Ui.muted(title, 12f, false);
        label.setBorder(new EmptyBorder(0, 0, 0, 2));
        row.add(label);
        return row;
    }

    private static JButton link(String text, Path target, Consumer<Path> open) {
        JButton link = new JButton("<html><u>" + text + "</u></html>");
        link.setName("open:" + text);
        link.setToolTipText(target.toString());
        link.putClientProperty("JButton.buttonType", "borderless");
        link.setForeground(UiTheme.ACCENT);
        link.setFocusable(false);
        link.setMargin(new Insets(2, 4, 2, 4));
        link.setFont(UiTheme.text(Font.PLAIN, 12f));
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.addActionListener(_ -> open.accept(target));
        return link;
    }

    /** The prompt as a tool tip, wrapped and cut so it stays readable. */
    private static String tip(String text) {
        String shown = text.length() > 600 ? text.substring(0, 600) + " …" : text;
        return "<html><div style='width:420px'>" + shown.replace("&", "&amp;").replace("<", "&lt;") + "</div></html>";
    }

    /** A left-aligned flow layout whose preferred height grows with the number of lines it needs. */
    private static final class Wrap extends FlowLayout {

        Wrap() {
            super(FlowLayout.LEFT, 6, 4);
        }

        @Override
        public Dimension preferredLayoutSize(Container target) {
            synchronized (target.getTreeLock()) {
                Insets insets = target.getInsets();
                int available = target.getWidth() > 0 ? target.getWidth()
                        : target.getParent() != null && target.getParent().getWidth() > 0
                        ? target.getParent().getWidth() : Integer.MAX_VALUE;
                int max = available - insets.left - insets.right - 2 * getHgap();
                int width = 0;
                int height = 0;
                int lineWidth = 0;
                int lineHeight = 0;
                for (Component c : target.getComponents()) {
                    if (!c.isVisible()) {
                        continue;
                    }
                    Dimension d = c.getPreferredSize();
                    if (lineWidth > 0 && lineWidth + getHgap() + d.width > max) {
                        width = Math.max(width, lineWidth);
                        height += lineHeight + getVgap();
                        lineWidth = 0;
                        lineHeight = 0;
                    }
                    lineWidth += (lineWidth > 0 ? getHgap() : 0) + d.width;
                    lineHeight = Math.max(lineHeight, d.height);
                }
                width = Math.max(width, lineWidth);
                height += lineHeight;
                return new Dimension(width + insets.left + insets.right + 2 * getHgap(),
                        height + insets.top + insets.bottom + 2 * getVgap());
            }
        }

        @Override
        public Dimension minimumLayoutSize(Container target) {
            return preferredLayoutSize(target);
        }
    }
}
