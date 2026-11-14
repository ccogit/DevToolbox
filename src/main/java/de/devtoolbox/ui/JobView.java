package de.devtoolbox.ui;

import de.devtoolbox.core.JobInfo;
import de.devtoolbox.core.Param;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * A job's configuration: the job teaser ("Ziel", details unfoldable) and one "Job-Konfiguration" card with all fields in two
 * columns (phases are just one of them); rarely needed ones are folded under "Erweiterte Optionen".
 */
final class JobView extends JPanel {

    private final ParamForm form;

    JobView(ParamForm form, JobInfo info) {
        super(new BorderLayout(0, 14));
        setOpaque(false);
        this.form = form;
        if (info != null) {
            add(infoBox(info), BorderLayout.NORTH);
        }
        add(configCard(form.params()), BorderLayout.CENTER);
    }

    /** Teaser "Ziel:" with a link that unfolds inputs, prerequisites and output in place. */
    private JComponent infoBox(JobInfo info) {
        Ui.Card box = new Ui.Card(new BorderLayout(0, 6), true);
        box.setBorder(new EmptyBorder(10, 16, 10, 16));
        JPanel teaser = Ui.transparent(new BorderLayout(10, 0));
        teaser.add(label("Ziel:", UiTheme.ACCENT), BorderLayout.WEST);
        teaser.add(text(info.goal()), BorderLayout.CENTER);
        box.add(teaser, BorderLayout.NORTH);

        JPanel details = Ui.transparent(new GridBagLayout());
        String[][] rows = {{"Primäre Inputs:", info.inputs()}, {"Voraussetzungen:", info.prerequisites()},
                {"Ergebnis:", info.output()}};
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(4, 0, 0, 10);
        for (int i = 0; i < rows.length; i++) {
            c.gridy = i;
            c.gridx = 0;
            c.weightx = 0;
            details.add(label(rows[i][0], UiTheme.MUTED), c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            details.add(text(rows[i][1]), c);
            c.fill = GridBagConstraints.NONE;
        }
        JPanel link = Ui.transparent(new BorderLayout());
        link.add(Ui.disclosure("Details", details, this::refresh), BorderLayout.NORTH);
        teaser.add(link, BorderLayout.EAST);
        box.add(details, BorderLayout.CENTER);
        return box;
    }

    private void refresh() {
        revalidate();
        repaint();
    }

    private static JLabel label(String text, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(UiTheme.text(Font.BOLD, 13f));
        l.setForeground(color);
        l.setVerticalAlignment(SwingConstants.TOP);
        return l;
    }

    private static JTextArea text(String content) {
        JTextArea text = new JTextArea(content);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setEditable(false);
        text.setFocusable(false);
        text.setOpaque(false);
        text.setBorder(null);
        text.setFont(UiTheme.text(Font.PLAIN, 13f));
        text.setForeground(UiTheme.TEXT);
        return text;
    }

    private JComponent configCard(List<Param> params) {
        List<Param> main = params.stream().filter(p -> !p.isAdvanced()).toList();
        List<Param> advanced = params.stream().filter(Param::isAdvanced).toList();
        JPanel body = Ui.transparent(new BorderLayout(0, 6));
        body.add(fields(form, main, java.util.Map.of()), BorderLayout.NORTH);
        if (!advanced.isEmpty()) {
            JPanel more = fields(form, advanced, java.util.Map.of());
            JLabel toggle = Ui.disclosure("Erweiterte Optionen", more, this::refresh);
            JPanel south = Ui.transparent(new BorderLayout(0, 4));
            south.add(toggle, BorderLayout.NORTH);
            south.add(more, BorderLayout.CENTER);
            body.add(south, BorderLayout.CENTER);
        }
        return Ui.card("⚙", "Job-Konfiguration", "Parameter und Optionen für die Ausführung.", null, body);
    }

    /** Two columns, label above field (* = required, ⓘ = hint); flags as check boxes; the additional context spans both. */
    static JPanel fields(ParamForm form, List<Param> params, java.util.Map<String, JComponent> trailing) {
        return fields(form, params, trailing, java.util.Set.of());
    }

    /** Like above; the keys in {@code wideKeys} get a full-width row. */
    static JPanel fields(ParamForm form, List<Param> params, java.util.Map<String, JComponent> trailing,
                         java.util.Set<String> wideKeys) {
        JPanel panel = Ui.transparent(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;
        int column = 0;
        int row = 0;
        for (Param p : params) {
            boolean wide = p.key().equals(de.devtoolbox.core.Keys.CONTEXT) || wideKeys.contains(p.key());
            if (wide && column == 1) { // start a new row
                column = 0;
                row += 2;
            }
            c.gridx = column;
            c.gridwidth = wide ? 2 : 1;
            c.insets = new Insets(0, column == 0 ? 0 : 12, 0, column == 0 && !wide ? 12 : 0);
            JComponent field = form.field(p.key());
            JComponent extra = trailing.get(p.key());
            if (extra != null) {
                JPanel wrap = Ui.transparent(new BorderLayout(8, 0));
                wrap.add(field, BorderLayout.CENTER);
                wrap.add(extra, BorderLayout.EAST);
                field = wrap;
            }
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
                if (!(field instanceof JScrollPane)) {
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
