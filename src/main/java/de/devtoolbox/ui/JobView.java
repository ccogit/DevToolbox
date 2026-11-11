package de.devtoolbox.ui;

import de.devtoolbox.core.Param;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * A job's configuration: the expected result ("Ergebnis") and one "Job-Konfiguration" card with all fields in two
 * columns (phases are just one of them); rarely needed ones are folded under "Erweiterte Optionen".
 */
final class JobView extends JPanel {

    private final ParamForm form;

    JobView(ParamForm form, String output) {
        super(new BorderLayout(0, 14));
        setOpaque(false);
        this.form = form;
        if (output != null && !output.isBlank()) {
            add(outputBox(output), BorderLayout.NORTH);
        }
        add(configCard(form.params()), BorderLayout.CENTER);
    }

    private static JComponent outputBox(String output) {
        Ui.Card box = new Ui.Card(new BorderLayout(10, 0), true);
        box.setBorder(new EmptyBorder(10, 16, 10, 16));
        JLabel title = new JLabel("Ergebnis:");
        title.setFont(UiTheme.text(Font.BOLD, 13f));
        title.setForeground(UiTheme.ACCENT);
        title.setVerticalAlignment(SwingConstants.TOP);
        box.add(title, BorderLayout.WEST);
        JTextArea text = new JTextArea(output);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setEditable(false);
        text.setFocusable(false);
        text.setOpaque(false);
        text.setBorder(null);
        text.setFont(UiTheme.text(Font.PLAIN, 13f));
        text.setForeground(UiTheme.TEXT);
        box.add(text, BorderLayout.CENTER);
        return box;
    }

    private JComponent configCard(List<Param> params) {
        Ui.Card card = new Ui.Card(new BorderLayout(0, 8), false);
        card.setBorder(new EmptyBorder(14, 18, 14, 18));
        JPanel head = Ui.transparent(new BorderLayout(12, 0));
        JLabel icon = new JLabel("⚙");
        icon.setFont(UiTheme.text(Font.PLAIN, 20f));
        icon.setForeground(UiTheme.MUTED);
        head.add(icon, BorderLayout.WEST);
        JPanel text = Ui.transparent(new GridLayout(2, 1, 0, 1));
        text.add(Ui.strong("Job-Konfiguration", 15f));
        text.add(Ui.muted("Parameter und Optionen für die Ausführung.", 12f, false));
        head.add(text, BorderLayout.CENTER);
        card.add(head, BorderLayout.NORTH);

        List<Param> main = params.stream().filter(p -> !p.isAdvanced()).toList();
        List<Param> advanced = params.stream().filter(Param::isAdvanced).toList();
        JPanel body = Ui.transparent(new BorderLayout(0, 6));
        body.add(fields(main), BorderLayout.NORTH);
        if (!advanced.isEmpty()) {
            JPanel more = fields(advanced);
            more.setVisible(false);
            JLabel[] toggle = new JLabel[1];
            toggle[0] = Ui.link("Erweiterte Optionen  ⌄", () -> {
                more.setVisible(!more.isVisible());
                toggle[0].setText(more.isVisible() ? "Erweiterte Optionen  ⌃" : "Erweiterte Optionen  ⌄");
                revalidate();
                repaint();
            });
            JPanel south = Ui.transparent(new BorderLayout(0, 4));
            south.add(toggle[0], BorderLayout.NORTH);
            south.add(more, BorderLayout.CENTER);
            body.add(south, BorderLayout.CENTER);
        }
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    /** Two columns, label above field (* = required, ⓘ = hint); flags as check boxes; the additional context spans both. */
    private JPanel fields(List<Param> params) {
        JPanel panel = Ui.transparent(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;
        int column = 0;
        int row = 0;
        for (Param p : params) {
            boolean wide = p.key().equals(de.devtoolbox.core.Keys.CONTEXT);
            if (wide && column == 1) { // start a new row
                column = 0;
                row += 2;
            }
            c.gridx = column;
            c.gridwidth = wide ? 2 : 1;
            c.insets = new Insets(0, column == 0 ? 0 : 12, 0, column == 0 && !wide ? 12 : 0);
            JComponent field = form.field(p.key());
            if (p.type() == Param.Type.FLAG) {
                ((JCheckBox) field).setOpaque(false);
                c.gridy = row;
                c.insets = new Insets(8, c.insets.left, 0, c.insets.right);
                panel.add(field, c);
            } else {
                JPanel label = Ui.transparent(new FlowLayout(FlowLayout.LEFT, 4, 0));
                label.setBorder(new EmptyBorder(8, 0, 2, 0));
                JLabel name = new JLabel(p.label() + (p.required() ? " *" : ""));
                name.setFont(UiTheme.text(Font.PLAIN, 12.5f));
                name.setForeground(UiTheme.TEXT);
                label.add(name);
                if (p.hint() != null && !p.hint().isBlank()) {
                    InfoButton info = new InfoButton(p.label());
                    info.setInfo(p.label(), p.hint(), false);
                    info.setForeground(UiTheme.MUTED);
                    label.add(info);
                }
                c.gridy = row;
                panel.add(label, c);
                c.gridy = row + 1;
                if (!(field instanceof JScrollPane) && p.type() != Param.Type.MULTI) {
                    field.setPreferredSize(new Dimension(10, 34));
                    field.setMinimumSize(new Dimension(10, 34));
                }
                panel.add(field, c);
            }
            if (wide || column == 1) {
                column = 0;
                row += 2;
            } else {
                column = 1;
            }
        }
        return panel;
    }
}
