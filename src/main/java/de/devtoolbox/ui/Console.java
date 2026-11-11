package de.devtoolbox.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * "Protokoll / Live Terminal": log lines with time stamp and a coloured tag ([INFO], [WARN], [FEHLER], [OK]), the
 * state of the run, and Auto-Scroll / Kopieren / Leeren. Thread-safe: {@link #append} may be called from any thread.
 */
final class Console extends Ui.Card {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final Color INFO = new Color(0x5B9BF0);
    private static final Color ERROR = new Color(0xF0605B);

    private final JTextPane text = new JTextPane();
    private final JLabel state = new JLabel();
    private final JCheckBox autoScroll = new JCheckBox("Auto-Scroll", true);

    Console(JComponent extra) {
        super(new BorderLayout(), false);
        JPanel head = Ui.transparent(new BorderLayout(10, 0));
        head.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UiTheme.BORDER), new EmptyBorder(9, 14, 9, 14)));
        JPanel left = Ui.transparent(new FlowLayout(FlowLayout.LEFT, 6, 0));
        for (int i = 0; i < 3; i++) {
            JLabel dot = new JLabel("●");
            dot.setForeground(UiTheme.BORDER_STRONG);
            left.add(dot);
        }
        left.add(Box.createHorizontalStrut(4));
        left.add(Ui.strong("PROTOKOLL / LIVE TERMINAL", 12.5f));
        state.setFont(UiTheme.mono(11f));
        left.add(state);
        head.add(left, BorderLayout.WEST);

        JPanel right = Ui.transparent(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        autoScroll.setOpaque(false);
        autoScroll.setFont(UiTheme.text(Font.PLAIN, 12f));
        right.add(autoScroll);
        right.add(Ui.link("⧉ Kopieren", () -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text.getText()), null)));
        right.add(Ui.link("Leeren", this::clear));
        if (extra != null) {
            right.add(extra);
        }
        head.add(right, BorderLayout.EAST);
        add(head, BorderLayout.NORTH);

        text.setEditable(false);
        text.setOpaque(false);
        text.setFont(UiTheme.mono(12f));
        text.setBorder(new EmptyBorder(10, 14, 10, 14));
        JScrollPane scroll = new JScrollPane(text);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);
        setPreferredSize(new Dimension(10, 170));
        ready();
    }

    void ready() {
        setState("● Bereit", UiTheme.ACCENT);
    }

    void running() {
        setState("● Läuft …", UiTheme.WARNING);
    }

    void failed() {
        setState("● Fehler", ERROR);
    }

    private void setState(String label, Color color) {
        SwingUtilities.invokeLater(() -> {
            state.setText(label);
            state.setForeground(color);
        });
    }

    void clear() {
        SwingUtilities.invokeLater(() -> text.setText(""));
    }

    /** One line; the tag follows from the text: "WARNUNG…" → WARN, "FEHLER…" → FEHLER, "Fertig…" → OK, else INFO. */
    void append(String line) {
        String time = LocalTime.now().format(TIME);
        String tag;
        Color color;
        if (line.startsWith("WARNUNG")) {
            tag = "WARN";
            color = UiTheme.WARNING;
        } else if (line.startsWith("FEHLER")) {
            tag = "FEHLER";
            color = ERROR;
        } else if (line.startsWith("Fertig")) {
            tag = "OK";
            color = UiTheme.ACCENT;
        } else {
            tag = "INFO";
            color = INFO;
        }
        SwingUtilities.invokeLater(() -> {
            StyledDocument doc = text.getStyledDocument();
            try {
                doc.insertString(doc.getLength(), "[" + time + "] ", style(UiTheme.MUTED));
                doc.insertString(doc.getLength(), "[" + tag + "] ", style(color));
                doc.insertString(doc.getLength(), line + "\n", style(new Color(0xB8C2CF)));
            } catch (BadLocationException e) {
                // cannot happen: always appended at the end
            }
            if (autoScroll.isSelected()) {
                text.setCaretPosition(doc.getLength());
            }
        });
    }

    private static SimpleAttributeSet style(Color color) {
        SimpleAttributeSet s = new SimpleAttributeSet();
        StyleConstants.setForeground(s, color);
        return s;
    }
}
