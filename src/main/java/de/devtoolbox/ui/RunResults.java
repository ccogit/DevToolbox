package de.devtoolbox.ui;

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
 * (start, each stage, continuation) and one link per produced file plus the run folder.
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
     * @param copy called with the prompt whose button was clicked
     * @param open called with the file or folder whose link was clicked
     */
    RunResults(List<Prompt> prompts, List<Path> files, Path folder, Consumer<Prompt> copy, Consumer<Path> open) {
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
        // the rows wrap: their height depends on the width
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                revalidate();
            }
        });
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
