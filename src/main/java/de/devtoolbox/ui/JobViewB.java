package de.devtoolbox.ui;

import de.devtoolbox.core.Config;
import de.devtoolbox.core.Param;
import de.devtoolbox.core.Param.Option;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Layout B of a job's configuration: "Phasen" (the options of a multiple choice as a list with check box, symbol and
 * explanation) next to "Job-Konfiguration" (all other fields; rarely needed ones folded under "Erweiterte Optionen").
 */
final class JobViewB extends JPanel {

    private static final Map<String, String> SYMBOLS = Map.of("READ", "⤓", "PROCESS", "⚙", "WRITE", "⤒");
    private static final Map<String, Color> COLORS = Map.of(
            "READ", new Color(0x5B9BF0), "PROCESS", new Color(0xB48CF5), "WRITE", new Color(0xE8B04A));

    private final ParamForm form;

    JobViewB(ParamForm form) {
        super(new GridBagLayout());
        setOpaque(false);
        this.form = form;
        List<Param> multi = form.params().stream().filter(p -> p.type() == Param.Type.MULTI).toList();
        List<Param> rest = form.params().stream().filter(p -> p.type() != Param.Type.MULTI).toList();
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.anchor = GridBagConstraints.NORTH;
        c.weighty = 1;
        c.weightx = 1;
        if (!multi.isEmpty()) {
            c.insets = new Insets(0, 0, 0, 16);
            add(phasesCard(multi), c);
        }
        c.insets = new Insets(0, 0, 0, 0);
        add(configCard(rest), c);
    }

    private static Ui.Card card(String glyph, String title, String subtitle) {
        Ui.Card card = new Ui.Card(new BorderLayout(0, 14), false);
        card.setBorder(new EmptyBorder(16, 18, 18, 18));
        JPanel head = Ui.transparent(new BorderLayout(14, 0));
        JLabel icon = new JLabel(glyph);
        icon.setFont(UiTheme.text(Font.PLAIN, 22f));
        icon.setForeground(UiTheme.MUTED);
        head.add(icon, BorderLayout.WEST);
        JPanel text = Ui.transparent(new GridLayout(2, 1, 0, 2));
        text.add(Ui.strong(title, 16f));
        text.add(Ui.muted(subtitle, 12.5f, false));
        head.add(text, BorderLayout.CENTER);
        card.add(head, BorderLayout.NORTH);
        return card;
    }

    // ---- phases ----

    private JComponent phasesCard(List<Param> multi) {
        Param p = multi.getFirst();
        Ui.Card card = card("◈", p.label(), "Wähle die zu betrachtenden Phasen für diesen Job.");
        JPanel list = Ui.transparent(new GridLayout(0, 1, 0, 6));
        for (Option option : p.options()) {
            list.add(phaseRow(p, option));
        }
        JPanel top = Ui.transparent(new BorderLayout());
        top.add(list, BorderLayout.NORTH);
        card.add(top, BorderLayout.CENTER);
        return card;
    }

    /** A row bound to the multi-choice value of {@code p}: ticking adds or removes {@code option}. */
    private JComponent phaseRow(Param p, Option option) {
        JCheckBox box = new JCheckBox();
        box.setOpaque(false);
        Ui.Card row = new Ui.Card(new BorderLayout(14, 0), false, 10) {
            @Override
            protected void paintComponent(Graphics g) {
                fill = box.isSelected() ? UiTheme.ACCENT_TINT : new Color(0x1A2134);
                super.paintComponent(g);
            }
        };
        row.setBorder(new EmptyBorder(9, 14, 9, 14));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        JPanel west = Ui.transparent(new FlowLayout(FlowLayout.LEFT, 12, 0));
        west.add(box);
        JLabel symbol = new JLabel(SYMBOLS.getOrDefault(option.value(), "•"));
        symbol.setFont(UiTheme.text(Font.BOLD, 18f));
        symbol.setForeground(COLORS.getOrDefault(option.value(), UiTheme.ACCENT));
        west.add(symbol);
        row.add(west, BorderLayout.WEST);
        JPanel text = Ui.transparent(new GridLayout(2, 1, 0, 1));
        text.add(Ui.strong(option.label(), 13.5f));
        text.add(Ui.muted(ParamForm.OPTION_HINTS.getOrDefault(option.value(), ""), 12f, false));
        row.add(text, BorderLayout.CENTER);

        Runnable sync = () -> {
            box.setSelected(Config.split(form.value(p.key())).stream().anyMatch(option.value()::equalsIgnoreCase));
            row.repaint();
        };
        Runnable toggle = () -> {
            List<String> selected = new ArrayList<>(Config.split(form.value(p.key())));
            selected.removeIf(option.value()::equalsIgnoreCase);
            if (box.isSelected()) {
                selected.add(option.value());
            }
            // keep the order of the options
            List<String> ordered = p.options().stream().map(Option::value)
                    .filter(v -> selected.stream().anyMatch(v::equalsIgnoreCase)).toList();
            form.setValues(Map.of(p.key(), String.join(",", ordered)));
        };
        box.addActionListener(e -> toggle.run());
        row.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                box.setSelected(!box.isSelected());
                toggle.run();
            }
        });
        form.onAnyChange(sync);
        sync.run();
        return row;
    }

    // ---- configuration ----

    private JComponent configCard(List<Param> params) {
        Ui.Card card = card("⚙", "Job-Konfiguration", "Parameter und Optionen für die Ausführung.");
        List<Param> main = params.stream().filter(p -> !p.isAdvanced()).toList();
        List<Param> advanced = params.stream().filter(Param::isAdvanced).toList();
        JPanel body = Ui.transparent(new BorderLayout(0, 10));
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
            JPanel south = Ui.transparent(new BorderLayout(0, 8));
            south.add(toggle[0], BorderLayout.NORTH);
            south.add(more, BorderLayout.CENTER);
            body.add(south, BorderLayout.CENTER);
        }
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    /** Label above field; flags as check boxes; required fields marked with *. */
    private JPanel fields(List<Param> params) {
        JPanel panel = Ui.transparent(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        for (Param p : params) {
            JComponent field = form.field(p.key());
            if (p.type() == Param.Type.FLAG) {
                ((JCheckBox) field).setOpaque(false);
                c.insets = new Insets(6, 0, 2, 0);
                panel.add(field, c);
                continue;
            }
            JPanel label = Ui.transparent(new BorderLayout());
            JLabel text = new JLabel(p.label() + (p.required() ? " *" : ""));
            text.setFont(UiTheme.text(Font.PLAIN, 13f));
            text.setForeground(UiTheme.TEXT);
            label.add(text, BorderLayout.WEST);
            if (p.hint() != null && !p.hint().isBlank()) {
                InfoButton info = new InfoButton(p.label());
                info.setInfo(p.label(), p.hint(), false);
                info.setForeground(UiTheme.MUTED);
                label.add(info, BorderLayout.EAST);
            }
            c.insets = new Insets(10, 0, 4, 0);
            panel.add(label, c);
            c.insets = new Insets(0, 0, 0, 0);
            field.setPreferredSize(new Dimension(10, 34));
            field.setMinimumSize(new Dimension(10, 34));
            panel.add(field, c);
        }
        return panel;
    }
}
