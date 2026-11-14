package de.devtoolbox.ui;

import de.devtoolbox.core.Config;
import de.devtoolbox.core.Param;
import de.devtoolbox.core.Param.Option;
import de.devtoolbox.tools.codecollector.DdlReader;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.prefs.Preferences;

/**
 * Input fields generated from a job's params; remembers the last values per job. As a grid (label, field, info per
 * row - used by the project editor) or, with {@code grid = false}, only the fields, which the main window arranges in
 * its cards ({@link #field}); the additional context is a multi-line text area.
 */
final class ParamForm extends JPanel {

    /** The business entity: a drop-down of the target model's entities (still editable, e.g. for synonyms). */
    static final java.util.Set<String> ENTITY_KEYS = java.util.Set.of(de.devtoolbox.core.Keys.TERMS,
            de.devtoolbox.core.Keys.ENTITY);

    private final Preferences prefs;
    private final List<Param> params;
    private final Map<String, Supplier<String>> getters = new LinkedHashMap<>();
    private final Map<String, Consumer<String>> setters = new HashMap<>();
    private final Map<String, List<Consumer<String>>> listeners = new HashMap<>();
    private final Map<String, JComponent> fields = new LinkedHashMap<>();
    private final List<Runnable> anyChange = new ArrayList<>();
    private final Map<String, JComboBox<String>> entityBoxes = new HashMap<>();
    private final boolean grid;

    ParamForm(String jobId, List<Param> params) {
        this(jobId, params, true);
    }

    ParamForm(String jobId, List<Param> params, boolean grid) {
        super(new GridBagLayout());
        this.params = params;
        this.grid = grid;
        prefs = Preferences.userNodeForPackage(ParamForm.class).node(jobId);
        if (!grid) {
            for (Param p : params) {
                JComponent field = create(p);
                field.setToolTipText(p.hint());
                fields.put(p.key(), field);
                setters.get(p.key()).accept(prefs.get(p.key(), p.defaultValue()));
            }
            return;
        }
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        int row = 0;
        for (Param p : params) {
            c.gridy = row++;
            c.gridx = 0;
            c.weightx = 0;
            c.fill = GridBagConstraints.NONE;
            c.insets = new Insets(4, 4, 0, 8);
            add(new JLabel(p.label() + (p.required() ? " *" : "") + ":"), c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            JComponent field = create(p);
            field.setToolTipText(p.hint());
            add(field, c);
            setters.get(p.key()).accept(prefs.get(p.key(), p.defaultValue()));
            if (p.hint() != null && !p.hint().isBlank()) {
                InfoButton info = new InfoButton(p.label());
                info.setInfo(p.label(), p.hint(), false);
                c.gridx = 2;
                c.weightx = 0;
                c.fill = GridBagConstraints.NONE;
                c.insets = new Insets(4, 4, 0, 0);
                add(info, c);
            }
        }
        // push everything to the top
        c.gridy = row;
        c.weighty = 1;
        add(Box.createGlue(), c);
    }

    private JComponent create(Param p) {
        return switch (p.type()) {
            case FLAG -> checkBox(p);
            case TEXT -> grid ? textField(p) : ENTITY_KEYS.contains(p.key()) ? entityBox(p)
                    : p.key().equals(de.devtoolbox.core.Keys.CONTEXT) ? textArea(p)
                    : p.key().equals(de.devtoolbox.core.Keys.TABLES) ? tablePicker(p) : textField(p);
            case DIRECTORY, FILE -> pathField(p);
            case CHOICE -> comboBox(p);
            case MULTI -> checkBoxes(p);
        };
    }

    /** The field of {@code key} (only without grid), null if the job has no such param. */
    JComponent field(String key) {
        return fields.get(key);
    }

    List<Param> params() {
        return params;
    }

    /** Called after any value changed (typing, ticking, choosing). */
    void onAnyChange(Runnable listener) {
        anyChange.add(listener);
    }

    private void changed() {
        SwingUtilities.invokeLater(() -> anyChange.forEach(Runnable::run));
    }

    Map<String, String> values() {
        Map<String, String> values = new LinkedHashMap<>();
        getters.forEach((key, getter) -> values.put(key, getter.get()));
        return values;
    }

    /** Values for a JSON file: flags as booleans, multi-selections as arrays. */
    Map<String, Object> typedValues() {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Param p : params) {
            String value = getters.get(p.key()).get();
            values.put(p.key(), switch (p.type()) {
                case FLAG -> Boolean.parseBoolean(value);
                case MULTI -> Config.split(value);
                default -> value;
            });
        }
        return values;
    }

    /** Applies the known keys; unknown keys are ignored. */
    void setValues(Map<String, String> values) {
        values.forEach((key, value) -> {
            Consumer<String> setter = setters.get(key);
            if (setter != null) {
                setter.accept(value);
            }
        });
    }

    /** Calls {@code listener} with the new value whenever the text or path field {@code key} changes. */
    void onChange(String key, Consumer<String> listener) {
        listeners.computeIfAbsent(key, k -> new ArrayList<>()).add(listener);
    }

    /**
     * Prefills fields from the value of {@code sourceKey}: whenever it changes, each target field that is empty or
     * still holds the previously derived value gets the newly derived one (null/"" = nothing derived). Values the
     * user typed in are never overwritten.
     */
    void deriveFrom(String sourceKey, Map<String, java.util.function.Function<String, String>> derivations) {
        Map<String, String> last = new HashMap<>();
        onChange(sourceKey, source -> derivations.forEach((key, derive) -> {
            if (!setters.containsKey(key)) {
                return;
            }
            String current = value(key);
            String derived = derive.apply(source);
            if (current.isBlank() || current.equals(last.getOrDefault(key, ""))) {
                setters.get(key).accept(derived == null ? "" : derived);
            }
            last.put(key, derived == null ? "" : derived);
        }));
    }

    /** The current value of {@code key}, "" if the form has no such field. */
    String value(String key) {
        Supplier<String> getter = getters.get(key);
        return getter == null ? "" : getter.get();
    }

    private void notifyOnEdit(String key, javax.swing.text.JTextComponent field) {
        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void changed() {
                // after the current edit: listeners may set other fields
                SwingUtilities.invokeLater(() -> listeners.getOrDefault(key, List.of()).forEach(l -> l.accept(field.getText())));
                ParamForm.this.changed();
            }

            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                changed();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                changed();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                changed();
            }
        });
    }

    void remember() {
        values().forEach(prefs::put);
    }

    private JComponent checkBox(Param p) {
        JCheckBox box = new JCheckBox(grid ? "" : p.label());
        box.addActionListener(e -> changed());
        getters.put(p.key(), () -> String.valueOf(box.isSelected()));
        setters.put(p.key(), v -> box.setSelected(Boolean.parseBoolean(v.trim())));
        return box;
    }

    private JComponent textField(Param p) {
        JTextField field = new JTextField(10);
        getters.put(p.key(), field::getText);
        setters.put(p.key(), field::setText);
        notifyOnEdit(p.key(), field);
        return field;
    }

    /** Multi-line field (3 rows, wrapping) in a scroll pane. */
    private JComponent textArea(Param p) {
        JTextArea area = new JTextArea(3, 10);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        getters.put(p.key(), area::getText);
        setters.put(p.key(), area::setText);
        notifyOnEdit(p.key(), area);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(10, 84));
        scroll.setMinimumSize(new Dimension(10, 84));
        return scroll;
    }

    private JComponent comboBox(Param p) {
        JComboBox<Option> box = new JComboBox<>(p.options().toArray(Option[]::new));
        box.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int i, boolean sel, boolean focus) {
                return super.getListCellRendererComponent(l, value == null ? "" : ((Option) value).label(), i, sel, focus);
            }
        });
        box.addActionListener(e -> changed());
        getters.put(p.key(), () -> box.getSelectedItem() == null ? "" : ((Option) box.getSelectedItem()).value());
        setters.put(p.key(), v -> p.options().stream().filter(o -> o.value().equalsIgnoreCase(v.trim()))
                .findFirst().ifPresent(box::setSelectedItem));
        return box;
    }

    private JComponent checkBoxes(Param p) {
        JPanel panel = grid ? new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0)) : new JPanel(new GridLayout(1, 0, 6, 0));
        panel.setOpaque(false);
        List<AbstractButton> boxes = new ArrayList<>();
        for (Option option : p.options()) {
            AbstractButton box;
            if (grid) {
                box = new JCheckBox(option.label());
                box.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 16));
                box.setOpaque(false);
            } else {
                box = new Segment(option.label());
            }
            box.addActionListener(e -> changed());
            boxes.add(box);
            panel.add(box);
        }
        getters.put(p.key(), () -> {
            List<String> selected = new ArrayList<>();
            for (int i = 0; i < boxes.size(); i++) {
                if (boxes.get(i).isSelected()) {
                    selected.add(p.options().get(i).value());
                }
            }
            return String.join(",", selected);
        });
        setters.put(p.key(), v -> {
            List<String> selected = Config.split(v);
            for (int i = 0; i < boxes.size(); i++) {
                String value = p.options().get(i).value();
                boxes.get(i).setSelected(selected.stream().anyMatch(value::equalsIgnoreCase));
            }
        });
        return panel;
    }

    /** Editable drop-down; the value is the editor's text, so "Risiko, Risk" can still be typed. */
    private JComponent entityBox(Param p) {
        JComboBox<String> box = new JComboBox<>();
        box.setEditable(true);
        JTextField editor = (JTextField) box.getEditor().getEditorComponent();
        getters.put(p.key(), editor::getText);
        setters.put(p.key(), v -> {
            box.setSelectedItem(v);
            editor.setText(v);
        });
        notifyOnEdit(p.key(), editor);
        box.addActionListener(e -> changed());
        entityBoxes.put(p.key(), box);
        return box;
    }

    /** Offers {@code names} in the entity drop-down(s); the current text stays. */
    void setEntityChoices(List<String> names) {
        entityBoxes.forEach((key, box) -> {
            String current = value(key);
            box.setModel(new DefaultComboBoxModel<>(names.toArray(String[]::new)));
            box.setSelectedItem(current);
            ((JTextField) box.getEditor().getEditorComponent()).setText(current);
        });
    }

    /** Tables of the DDL offered by the table picker (read by the main window from the DDL field). */
    private List<DdlReader.Table> tableChoices = List.of();
    /** Shown in the picker when there are no tables, e.g. why the DDL could not be read. */
    private String tableChoicesNote = "";

    /** Offers {@code tables} in the drop-down of "Tabellen der Entität"; {@code note} is shown if there are none. */
    void setTableChoices(List<DdlReader.Table> tables, String note) {
        tableChoices = List.copyOf(tables);
        tableChoicesNote = note == null ? "" : note;
    }

    /**
     * Text field (comma-separated table names, still typed freely) with a drop-down button: a filterable list of
     * the DDL's tables with their label/comment; tables matching the business entity first. A click adds the table to
     * the field or removes it again; the list stays open for further tables.
     */
    private JComponent tablePicker(Param p) {
        JTextField field = new JTextField(10);
        getters.put(p.key(), field::getText);
        setters.put(p.key(), field::setText);
        notifyOnEdit(p.key(), field);
        field.putClientProperty("JTextField.placeholderText", "leer = automatisch (Name/Kommentar enthält Suchbegriff)");
        JButton open = new JButton("▾");
        open.putClientProperty("JButton.buttonType", "toolBarButton");
        open.setFocusable(false);
        open.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        open.setToolTipText("Tabellen aus der DDL auswählen …");
        open.addActionListener(e -> showTablePopup(field));
        field.putClientProperty("JTextField.trailingComponent", open);
        return field;
    }

    private void showTablePopup(JTextField field) {
        List<String> terms = Config.split(value(de.devtoolbox.core.Keys.TERMS));
        List<DdlReader.Table> sorted = new ArrayList<>(tableChoices);
        sorted.sort(java.util.Comparator.comparing((DdlReader.Table t) -> !t.matches(terms))
                .thenComparing(DdlReader.Table::name, String.CASE_INSENSITIVE_ORDER));
        JPopupMenu popup = new JPopupMenu();
        popup.setLayout(new BorderLayout(0, 6));
        popup.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        if (sorted.isEmpty()) {
            JLabel empty = new JLabel("<html><div style='width:320px'>" + (tableChoicesNote.isEmpty()
                    ? "Keine Tabellen: zuerst im Projekt „Schema importieren…“ oder „Struktur der Altdaten (DDL)“ angeben."
                    : tableChoicesNote) + "</div></html>");
            empty.setForeground(UiTheme.MUTED);
            popup.add(empty, BorderLayout.CENTER);
            popup.show(field, 0, field.getHeight());
            return;
        }
        JTextField filter = new JTextField();
        filter.putClientProperty("JTextField.placeholderText", "Filtern nach Name oder Bezeichnung …");
        DefaultListModel<DdlReader.Table> model = new DefaultListModel<>();
        sorted.forEach(model::addElement);
        JList<DdlReader.Table> list = new JList<>(model);
        list.setVisibleRowCount(Math.min(12, sorted.size()));
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int i, boolean sel, boolean focus) {
                DdlReader.Table t = (DdlReader.Table) value;
                boolean chosen = containsTable(field.getText(), t);
                String text = "<html>" + (chosen ? "☑" : "☐") + "&nbsp;&nbsp;<b>" + escape(t.name()) + "</b>"
                        + (t.isView() ? " <i>(View)</i>" : "")
                        + (t.description().isEmpty() ? "" : " – " + escape(t.description()))
                        + (t.matches(terms) ? "&nbsp;&nbsp;<font color='#2e9d5b'>★ passt zur Entität</font>" : "")
                        + "</html>";
                return super.getListCellRendererComponent(l, text, i, sel, focus);
            }
        });
        Runnable toggleSelected = () -> {
            DdlReader.Table t = list.getSelectedValue();
            if (t != null) {
                field.setText(toggleTable(field.getText(), t));
                list.repaint();
            }
        };
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                int index = list.locationToIndex(e.getPoint());
                if (index >= 0 && list.getCellBounds(index, index).contains(e.getPoint())) {
                    list.setSelectedIndex(index);
                    toggleSelected.run();
                }
            }
        });
        list.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "toggle");
        list.getActionMap().put("toggle", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                toggleSelected.run();
            }
        });
        filter.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void apply() {
                String q = filter.getText().strip().toLowerCase(java.util.Locale.ROOT);
                model.clear();
                sorted.stream().filter(t -> q.isEmpty() || (t.qualifiedName() + " " + t.description())
                        .toLowerCase(java.util.Locale.ROOT).contains(q)).forEach(model::addElement);
            }

            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                apply();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                apply();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                apply();
            }
        });
        filter.addActionListener(e -> {
            if (!model.isEmpty()) {
                list.setSelectedIndex(0);
                toggleSelected.run();
            }
        });
        long matching = sorted.stream().filter(t -> t.matches(terms)).count();
        JLabel footer = new JLabel(sorted.size() + " Tabellen aus der DDL" + (matching > 0
                ? " · " + matching + " passen zur Entität (oben)" : " · keine passt per Name/Kommentar zur Entität")
                + " · Klick = hinzufügen/entfernen");
        footer.setForeground(UiTheme.MUTED);
        footer.setFont(UiTheme.text(Font.PLAIN, 12f));
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(Math.max(field.getWidth(), 520),
                list.getPreferredScrollableViewportSize().height + 4));
        popup.add(filter, BorderLayout.NORTH);
        popup.add(scroll, BorderLayout.CENTER);
        popup.add(footer, BorderLayout.SOUTH);
        popup.show(field, 0, field.getHeight());
        filter.requestFocusInWindow();
    }

    /** Whether the comma-separated {@code text} names the table (with or without schema, any case). */
    static boolean containsTable(String text, DdlReader.Table t) {
        return Config.split(text).stream().anyMatch(n -> n.equalsIgnoreCase(t.name()) || n.equalsIgnoreCase(t.qualifiedName()));
    }

    /** Adds the table to the comma-separated {@code text}, or removes it if it is already named. */
    static String toggleTable(String text, DdlReader.Table t) {
        List<String> names = new ArrayList<>(Config.split(text));
        boolean removed = names.removeIf(n -> n.equalsIgnoreCase(t.name()) || n.equalsIgnoreCase(t.qualifiedName()));
        if (!removed) {
            names.add(t.name());
        }
        return String.join(", ", names);
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private JComponent pathField(Param p) {
        JTextField field = new JTextField(10);
        getters.put(p.key(), field::getText);
        setters.put(p.key(), field::setText);
        notifyOnEdit(p.key(), field);
        field.putClientProperty("JTextField.placeholderText",
                p.type() == Param.Type.DIRECTORY ? "Ordner auswählen oder eingeben …" : "Datei auswählen oder eingeben …");
        // browse button inside the field (FlatLaf trailing component)
        JButton browse = new JButton("…");
        browse.putClientProperty("JButton.buttonType", "toolBarButton");
        browse.setFocusable(false);
        browse.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        browse.setToolTipText("Durchsuchen …");
        browse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(field.getText().isBlank() ? null : field.getText());
            if (p.type() == Param.Type.DIRECTORY) {
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            } else if (!p.options().isEmpty()) {
                String[] extensions = p.options().stream().map(Option::value).toArray(String[]::new);
                chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                        String.join(", ", p.options().stream().map(Option::label).toList()), extensions));
            }
            // the field, not the form: without grid the form itself is never shown
            int answer = chooser.showOpenDialog(field);
            if (answer == JFileChooser.APPROVE_OPTION) {
                field.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        if (!grid) {
            field.setFont(UiTheme.mono(12f));
        }
        field.putClientProperty("JTextField.trailingComponent", browse);
        return field;
    }

    /** One segment of a multi-selection: same height, background and border as the input fields; selected = tinted. */
    private static final class Segment extends JToggleButton {
        Segment(String label) {
            super(label);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(UiTheme.text(Font.PLAIN, 13f));
            setPreferredSize(new Dimension(10, 34));
            setMinimumSize(new Dimension(10, 34));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean on = isSelected();
            g2.setColor(on ? UiTheme.ACCENT_TINT : UiTheme.INPUT);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            g2.setColor(on ? UiTheme.ACCENT_DARK : UiTheme.BORDER_STRONG);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            g2.setFont(getFont());
            g2.setColor(on ? UiTheme.ACCENT : UiTheme.TEXT);
            String text = (on ? "✓  " : "") + getText();
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, (getWidth() - fm.stringWidth(text)) / 2, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }
}
