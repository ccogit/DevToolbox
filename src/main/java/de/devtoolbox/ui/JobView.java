package de.devtoolbox.ui;

import de.devtoolbox.core.Config;
import de.devtoolbox.core.Keys;
import de.devtoolbox.core.Param;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The configuration of a job as cards, filled generically from its params:
 * <ul>
 *   <li>"Pfade &amp; Dateiquellen" – folders and files, collapsed to a summary;</li>
 *   <li>the core card – texts and choices on the left, phase tiles and flags on the right;</li>
 *   <li>"Dateifilter &amp; Ausschlusslisten" and "Zusatzkontext &amp; KI Copilot" – edited in a dialog.</li>
 * </ul>
 */
final class JobView extends JPanel {

    private static final Set<String> FILTER_KEYS = Set.of(Keys.EXTENSIONS, Keys.EXCLUDE);
    private static final Set<String> AI_KEYS = Set.of(Keys.CONTEXT, Keys.COPILOT_LIMIT, Keys.MAX_TOKENS);
    /** Business entity fields: shown large and green. */
    private static final Set<String> ENTITY_KEYS = Set.of(Keys.TERMS, Keys.ENTITY);

    private final ParamForm form;
    private final JPanel pathDetails;
    private final JLabel pathsBadge = Ui.greenBadge("", true);
    private final JPanel pathsIcon = Ui.transparent(new BorderLayout());
    private JLabel pathsToggle;

    JobView(ParamForm form, String jobTitle, JComponent pipeline) {
        super(new GridBagLayout());
        setOpaque(false);
        this.form = form;
        List<Param> params = form.params();
        List<Param> paths = select(params, p -> p.type() == Param.Type.DIRECTORY || p.type() == Param.Type.FILE);
        List<Param> left = select(params, p -> (p.type() == Param.Type.TEXT || p.type() == Param.Type.CHOICE)
                && !FILTER_KEYS.contains(p.key()) && !AI_KEYS.contains(p.key()));
        List<Param> right = select(params, p -> p.type() == Param.Type.MULTI || p.type() == Param.Type.FLAG);
        List<Param> filters = select(params, p -> FILTER_KEYS.contains(p.key()));
        List<Param> ai = select(params, p -> AI_KEYS.contains(p.key()));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 18, 0);
        pathDetails = grid(paths);
        pathDetails.setVisible(false);
        if (!paths.isEmpty()) {
            add(pathsCard(), c);
        }
        add(coreCard(left, right, jobTitle, pipeline), c);
        JPanel bottom = Ui.transparent(new GridLayout(1, 0, 18, 0));
        if (!filters.isEmpty()) {
            bottom.add(dialogCard("⏷", UiTheme.VIOLET, "Dateifilter & Ausschlusslisten", filters,
                    () -> filterSummary(filters)));
        }
        if (!ai.isEmpty()) {
            bottom.add(dialogCard("ϟ", UiTheme.CYAN, "Zusatzkontext & KI Copilot", ai, () -> aiSummary(ai)));
        }
        if (bottom.getComponentCount() > 0) {
            add(bottom, c);
        }
        form.onAnyChange(this::updatePaths);
        updatePaths();
    }

    private static List<Param> select(List<Param> params, Predicate<Param> filter) {
        return params.stream().filter(filter).toList();
    }

    // ---- paths ----

    private JComponent pathsCard() {
        Ui.Card card = new Ui.Card(new BorderLayout(0, 14), false);
        card.setBorder(new EmptyBorder(14, 16, 14, 18));
        JPanel head = Ui.transparent(new BorderLayout(14, 0));
        head.add(pathsIcon, BorderLayout.WEST);
        JPanel titleRow = Ui.transparent(new GridBagLayout());
        GridBagConstraints tc = new GridBagConstraints();
        tc.anchor = GridBagConstraints.WEST;
        titleRow.add(Ui.strong("PFADE & DATEIQUELLEN", 13f), tc);
        tc.insets = new Insets(0, 10, 0, 0);
        tc.weightx = 1;
        titleRow.add(pathsBadge, tc);
        head.add(titleRow, BorderLayout.CENTER);
        pathsToggle = Ui.link("Pfade anpassen  ⌄", () -> {
            pathDetails.setVisible(!pathDetails.isVisible());
            pathsToggle.setText(pathDetails.isVisible() ? "Pfade einklappen  ⌃" : "Pfade anpassen  ⌄");
            revalidate();
            repaint();
        });
        head.add(pathsToggle, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);
        card.add(pathDetails, BorderLayout.CENTER);
        return card;
    }

    /** Badge (configured / missing paths), icon ✓ or ! if a path is missing or does not exist. */
    private void updatePaths() {
        List<Param> paths = select(form.params(), p -> p.type() == Param.Type.DIRECTORY || p.type() == Param.Type.FILE);
        int configured = 0;
        int missing = 0;
        for (Param p : paths) {
            String value = form.value(p.key()).strip();
            if (value.isEmpty()) {
                missing += p.required() ? 1 : 0;
                continue;
            }
            configured++;
            if (!exists(value)) {
                missing++;
            }
        }
        pathsBadge.setText(configured + (configured == 1 ? " Pfad" : " Pfade") + " konfiguriert"
                + (missing == 0 ? "" : " · " + missing + " fehl" + (missing == 1 ? "t" : "en")));
        pathsIcon.removeAll();
        pathsIcon.add(missing == 0 ? Ui.iconTile("✓", UiTheme.ACCENT, 34) : Ui.iconTile("!", UiTheme.WARNING, 34),
                BorderLayout.CENTER);
        pathsIcon.revalidate();
        pathsIcon.repaint();
    }

    private static boolean exists(String value) {
        try {
            return Files.exists(Path.of(value));
        } catch (InvalidPathException e) {
            return false;
        }
    }

    // ---- core ----

    private JComponent coreCard(List<Param> left, List<Param> right, String jobTitle, JComponent pipeline) {
        Ui.Card card = new Ui.Card(new BorderLayout(), true);
        card.headerHeight = 58;
        JPanel head = Ui.transparent(new BorderLayout(12, 0));
        head.setBorder(new EmptyBorder(12, 16, 12, 18));
        head.setPreferredSize(new Dimension(10, 58));
        JPanel title = Ui.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        title.add(Ui.iconTile("☰", UiTheme.ACCENT, 32));
        title.add(Box.createHorizontalStrut(12));
        JLabel name = Ui.strong(jobTitle, 15f);
        name.setBorder(new EmptyBorder(6, 0, 0, 0));
        title.add(name);
        title.add(Box.createHorizontalStrut(10));
        JLabel badge = Ui.greenBadge("Kern-Konfiguration", true);
        JPanel badgeBox = Ui.transparent(new BorderLayout());
        badgeBox.setBorder(new EmptyBorder(5, 0, 0, 0));
        badgeBox.add(badge);
        title.add(badgeBox);
        head.add(title, BorderLayout.WEST);
        JPanel east = Ui.transparent(new BorderLayout());
        east.setBorder(new EmptyBorder(4, 0, 0, 0));
        east.add(pipeline, BorderLayout.CENTER);
        head.add(east, BorderLayout.EAST);
        card.add(head, BorderLayout.NORTH);

        JPanel body = Ui.transparent(new GridLayout(1, 0, 28, 0));
        body.setBorder(new EmptyBorder(18, 22, 22, 22));
        if (!left.isEmpty()) {
            body.add(column(Ui.sectionTitle("☰", UiTheme.ACCENT, "Fachliche Zielentität"), left));
        }
        if (!right.isEmpty()) {
            body.add(column(Ui.sectionTitle("⚙", UiTheme.WARNING, "Betrachtete Phasen & Pipeline-Flags"), right));
        }
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    /** Title, then per param: label row (with * and ⓘ) and field; flags as bordered rows. */
    private JComponent column(JComponent title, List<Param> params) {
        JPanel col = Ui.transparent(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTH;
        col.add(title, c);
        boolean flags = false;
        for (Param p : params) {
            JComponent field = form.field(p.key());
            if (p.type() == Param.Type.FLAG) {
                c.insets = new Insets(flags ? 6 : 14, 0, 0, 0);
                col.add(flagRow(p, (JCheckBox) field), c);
                flags = true;
                continue;
            }
            c.insets = new Insets(8, 0, 6, 0);
            col.add(labelRow(p), c);
            c.insets = new Insets(0, 0, 10, 0);
            if (ENTITY_KEYS.contains(p.key()) && field instanceof JComboBox<?> box) {
                JTextField editor = (JTextField) box.getEditor().getEditorComponent();
                editor.setFont(UiTheme.text(Font.BOLD, 15f));
                editor.setForeground(UiTheme.ACCENT);
                editor.putClientProperty("JTextField.selectAllOnFocusPolicy", "never");
                box.setPreferredSize(new Dimension(10, 40));
                box.setMinimumSize(new Dimension(10, 40));
            } else if (p.type() != Param.Type.MULTI) {
                field.setPreferredSize(new Dimension(10, 36));
                field.setMinimumSize(new Dimension(10, 36));
            }
            col.add(field, c);
        }
        c.weighty = 1;
        col.add(Box.createGlue(), c);
        return col;
    }

    private static JComponent labelRow(Param p) {
        JPanel row = Ui.transparent(new BorderLayout());
        JLabel label = new JLabel("<html>" + p.label() + (p.required() ? " <font color='#34C77B'>*</font>" : "") + "</html>");
        label.setFont(UiTheme.text(Font.PLAIN, 13f));
        label.setForeground(UiTheme.TEXT);
        row.add(label, BorderLayout.WEST);
        if (p.type() == Param.Type.MULTI) {
            row.add(Ui.muted("Mindestens 1 Auswahl erforderlich", 11.5f, false), BorderLayout.EAST);
        } else if (p.hint() != null && !p.hint().isBlank()) {
            row.add(info(p), BorderLayout.EAST);
        }
        return row;
    }

    private static JComponent flagRow(Param p, JCheckBox box) {
        Ui.Card row = new Ui.Card(new BorderLayout(), false, 10);
        row.fill = new Color(0x0F151D);
        row.setBorder(new EmptyBorder(6, 8, 6, 8));
        box.setOpaque(false);
        box.setFont(UiTheme.text(Font.PLAIN, 13f));
        row.add(box, BorderLayout.CENTER);
        if (p.hint() != null && !p.hint().isBlank()) {
            row.add(info(p), BorderLayout.EAST);
        }
        return row;
    }

    private static InfoButton info(Param p) {
        InfoButton info = new InfoButton(p.label());
        info.setInfo(p.label(), p.hint(), false);
        info.setForeground(UiTheme.MUTED);
        return info;
    }

    // ---- filter / AI cards ----

    private JComponent dialogCard(String glyph, Color color, String title, List<Param> params,
                                  java.util.function.Supplier<String> summary) {
        Ui.Card card = new Ui.Card(new BorderLayout(14, 0), false);
        card.setBorder(new EmptyBorder(16, 16, 16, 18));
        card.add(Ui.iconTile(glyph, color, 32), BorderLayout.WEST);
        JPanel text = Ui.transparent(new GridLayout(2, 1, 0, 4));
        text.add(Ui.strong(title, 13f));
        JLabel sub = Ui.muted(summary.get(), 11.5f, true);
        text.add(sub);
        card.add(text, BorderLayout.CENTER);
        JPanel content = grid(params);
        card.add(Ui.link("Vorschau / Bearbeiten", () -> {
            JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), title, Dialog.ModalityType.APPLICATION_MODAL);
            JPanel root = new JPanel(new BorderLayout(0, 14));
            root.setBorder(new EmptyBorder(16, 18, 14, 18));
            root.add(content, BorderLayout.CENTER);
            // values apply immediately - there is nothing to confirm or cancel
            JButton close = new JButton("Schließen");
            close.addActionListener(e -> dialog.dispose());
            JPanel buttons = Ui.transparent(new FlowLayout(FlowLayout.RIGHT, 0, 0));
            buttons.add(close);
            root.add(buttons, BorderLayout.SOUTH);
            dialog.setContentPane(root);
            dialog.getRootPane().setDefaultButton(close);
            dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                    KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
            dialog.pack();
            dialog.setSize(Math.max(620, dialog.getWidth()), dialog.getHeight());
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);
            root.remove(content); // the fields stay with this card for the next opening
        }), BorderLayout.EAST);
        form.onAnyChange(() -> sub.setText(summary.get()));
        return card;
    }

    private String filterSummary(List<Param> params) {
        StringBuilder s = new StringBuilder();
        for (Param p : params) {
            int n = Config.split(form.value(p.key())).size();
            s.append(s.isEmpty() ? "" : " • ")
                    .append(n).append(p.key().equals(Keys.EXTENSIONS) ? " Endungen" : p.key().equals(Keys.EXCLUDE)
                            ? " Ausschlüsse" : " " + p.label());
        }
        return s.toString();
    }

    private String aiSummary(List<Param> params) {
        String context = form.value(Keys.CONTEXT).strip();
        String limit = form.value(Keys.COPILOT_LIMIT).strip();
        if (context.isEmpty() && limit.isEmpty()) {
            return "Tokenlimit & Prompt Tuning";
        }
        String c = context.isEmpty() ? "kein Zusatzkontext" : "Kontext: " + (context.length() > 28 ? context.substring(0, 27) + "…" : context);
        return c + (limit.isEmpty() ? "" : " • Limit " + limit);
    }

    /** Label, field and info per row (paths, dialog contents). */
    private JPanel grid(List<Param> params) {
        JPanel grid = Ui.transparent(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        int row = 0;
        for (Param p : params) {
            c.gridy = row++;
            c.gridx = 0;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            c.insets = new Insets(6, 0, 0, 14);
            JLabel label = new JLabel("<html>" + p.label() + (p.required() ? " <font color='#34C77B'>*</font>" : "") + "</html>");
            label.setForeground(UiTheme.MUTED);
            grid.add(label, c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(6, 0, 0, 6);
            grid.add(form.field(p.key()), c);
            c.gridx = 2;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            c.insets = new Insets(6, 0, 0, 0);
            grid.add(p.hint() == null || p.hint().isBlank() ? new JLabel() : info(p), c);
        }
        return grid;
    }
}
