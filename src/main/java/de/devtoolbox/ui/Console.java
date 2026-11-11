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

    /**
     * @param extra  e.g. "Ordner öffnen", shown in the header
     * @param expand action of the expand symbol (null = none)
     */
    Console(JComponent extra, Runnable expand) {
        super(new BorderLayout(), false);
        state.setFont(UiTheme.mono(11f));
        add(headB(extra, expand), BorderLayout.NORTH);

        text.setEditable(false);
        text.setOpaque(false);
        text.setFont(UiTheme.mono(12f));
        text.setBorder(new EmptyBorder(10, 14, 10, 14));
        JScrollPane scroll = new JScrollPane(text);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);
        setPreferredSize(new Dimension(10, 150));
        ready();
    }

    /** The window shows the state in its status bar instead of the header. */
    JLabel stateLabel() {
        return state;
    }

    /** "Ausführungsprotokoll" with symbol buttons: Leeren, Kopieren, Vergrößern. */
    private JComponent headB(JComponent extra, Runnable expand) {
        JPanel head = Ui.transparent(new BorderLayout(10, 0));
        head.setBorder(new EmptyBorder(10, 16, 6, 14));
        JLabel title = new JLabel(">_   Ausführungsprotokoll");
        title.setFont(UiTheme.text(Font.BOLD, 15f));
        title.setForeground(UiTheme.TEXT);
        head.add(title, BorderLayout.WEST);
        JPanel right = Ui.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        if (extra != null) {
            right.add(extra);
        }
        right.add(symbol("🗑", "Leeren", this::clear));
        right.add(symbol("⧉", "Kopieren", () -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text.getText()), null)));
        if (expand != null) {
            right.add(symbol("⤢", "Vergrößern / verkleinern", expand));
        }
        head.add(right, BorderLayout.EAST);
        return head;
    }

    private static JButton symbol(String glyph, String tip, Runnable action) {
        JButton b = new JButton(glyph);
        b.setToolTipText(tip);
        b.setFocusable(false);
        b.setMargin(new Insets(2, 8, 2, 8));
        b.addActionListener(e -> action.run());
        return b;
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
                // tag as a small coloured badge
                SimpleAttributeSet badge = style(color.brighter());
                StyleConstants.setBackground(badge, new Color(color.getRed(), color.getGreen(), color.getBlue(), 70));
                doc.insertString(doc.getLength(), " " + tag + " ", badge);
                doc.insertString(doc.getLength(), "  ", style(color));
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
