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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.time.format.DateTimeFormatter;

/**
 * "Protokoll / Live Terminal": log lines with time stamp and a coloured tag ([INFO], [WARN], [FEHLER], [OK]), the
 * state of the run, and Auto-Scroll / Kopieren / Leeren. Thread-safe: {@link #append} may be called from any thread.
 */
final class Console extends Ui.Card {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final Color INFO = new Color(0x5B9BF0);
    private static final Color ERROR = new Color(0xF0605B);

    private static final int MAX_PENDING_LINES = 5_000;
    private static final int FLUSH_BATCH_SIZE = 200;
    private static final int MAX_DOCUMENT_CHARS = 250_000;
    private final ArrayDeque<LogLine> pending = new ArrayDeque<>();
    private boolean flushScheduled;
    private int discardedLines;

    private record LogLine(String time, String tag, Color color, String message) { }

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
        synchronized (pending) {
            pending.clear();
            discardedLines = 0;
        }
        SwingUtilities.invokeLater(() -> text.setText(""));
    }

    /** Queues one line without scheduling an EDT callback per message. */
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
        synchronized (pending) {
            if (pending.size() >= MAX_PENDING_LINES) {
                pending.removeFirst();
                discardedLines++;
            }
            pending.addLast(new LogLine(time, tag, color, line));
            if (!flushScheduled) {
                flushScheduled = true;
                SwingUtilities.invokeLater(this::flushPending);
            }
        }
    }

    /** Processes a limited batch to keep Swing responsive even under heavy logging. */
    private void flushPending() {
        List<LogLine> batch = new ArrayList<>(FLUSH_BATCH_SIZE);
        int skipped;
        synchronized (pending) {
            skipped = discardedLines;
            discardedLines = 0;
            while (batch.size() < FLUSH_BATCH_SIZE && !pending.isEmpty()) {
                batch.add(pending.removeFirst());
            }
            if (pending.isEmpty()) {
                flushScheduled = false;
            } else {
                SwingUtilities.invokeLater(this::flushPending);
            }
        }

        StyledDocument doc = text.getStyledDocument();
        try {
            if (skipped > 0) {
                doc.insertString(doc.getLength(), "[WARNUNG] " + skipped + " Protokollzeilen wegen Überlast verworfen.\n",
                        style(UiTheme.WARNING));
            }
            for (LogLine entry : batch) {
                doc.insertString(doc.getLength(), "[" + entry.time() + "] ", style(UiTheme.MUTED));
                SimpleAttributeSet badge = style(entry.color().brighter());
                Color color = entry.color();
                StyleConstants.setBackground(badge, new Color(color.getRed(), color.getGreen(), color.getBlue(), 70));
                doc.insertString(doc.getLength(), " " + entry.tag() + " ", badge);
                doc.insertString(doc.getLength(), "  ", style(color));
                doc.insertString(doc.getLength(), entry.message() + "\n", style(new Color(0xB8C2CF)));
            }
            if (doc.getLength() > MAX_DOCUMENT_CHARS) {
                doc.remove(0, doc.getLength() - MAX_DOCUMENT_CHARS);
            }
            if (autoScroll.isSelected()) {
                text.setCaretPosition(doc.getLength());
            }
        } catch (BadLocationException e) {
            throw new IllegalStateException("Protokoll konnte nicht aktualisiert werden.", e);
        }
    }

    private static SimpleAttributeSet style(Color color) {
        SimpleAttributeSet s = new SimpleAttributeSet();
        StyleConstants.setForeground(s, color);
        return s;
    }
}
