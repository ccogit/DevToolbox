package de.devtoolbox.ui;

import de.devtoolbox.core.Config;
import de.devtoolbox.core.Param;
import de.devtoolbox.core.Param.Option;

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
 * its cards ({@link #field}); choices of several options then appear as selectable tiles.
 */
final class ParamForm extends JPanel {

    /** Short explanations of the batch phases, shown on their tiles. */
    static final Map<String, String> OPTION_HINTS = Map.of(
            "READ", "Objekte aus IMS und LAS einlesen",
            "PROCESS", "Objekte in Entitäten des Zielmodells mappen",
            "WRITE", "Entitäten des Zielmodells in den BOS importieren");

    private final Preferences prefs;
    private final List<Param> params;
    private final Map<String, Supplier<String>> getters = new LinkedHashMap<>();
    private final Map<String, Consumer<String>> setters = new HashMap<>();
    private final Map<String, List<Consumer<String>>> listeners = new HashMap<>();
    private final Map<String, JComponent> fields = new LinkedHashMap<>();
    private final List<Runnable> anyChange = new ArrayList<>();
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
            case TEXT -> textField(p);
            case DIRECTORY, FILE -> pathField(p);
            case CHOICE -> comboBox(p);
            case MULTI -> grid ? checkBoxes(p) : tiles(p);
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

    private void notifyOnEdit(String key, JTextField field) {
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
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        List<JCheckBox> boxes = new ArrayList<>();
        for (Option option : p.options()) {
            JCheckBox box = new JCheckBox(option.label());
            box.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 16));
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

    /** One selectable tile per option (check mark, name, short explanation); selected tiles are green. */
    private JComponent tiles(Param p) {
        JPanel panel = new JPanel(new GridLayout(1, p.options().size(), 10, 0));
        panel.setOpaque(false);
        List<JCheckBox> boxes = new ArrayList<>();
        for (Option option : p.options()) {
            JCheckBox box = new JCheckBox(option.label());
            Ui.Card card = new Ui.Card(new BorderLayout(0, 4), false, 12) {
                @Override
                protected void paintComponent(Graphics g) {
                    boolean on = box.isSelected();
                    fill = on ? UiTheme.ACCENT_TINT : new Color(0x151D27);
                    super.paintComponent(g);
                    if (on) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(UiTheme.ACCENT_DARK);
                        g2.setStroke(new BasicStroke(1.6f));
                        g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 12, 12);
                        g2.dispose();
                    }
                }
            };
            card.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            box.setOpaque(false);
            box.setFont(UiTheme.text(Font.BOLD, 13f));
            box.setHorizontalAlignment(SwingConstants.CENTER);
            box.addActionListener(e -> {
                panel.repaint();
                changed();
            });
            JLabel hint = new JLabel("<html><div style='text-align:center'>"
                    + OPTION_HINTS.getOrDefault(option.value(), "") + "</div></html>", SwingConstants.CENTER);
            hint.setFont(UiTheme.text(Font.PLAIN, 11f));
            hint.setForeground(UiTheme.MUTED);
            card.add(box, BorderLayout.NORTH);
            card.add(hint, BorderLayout.CENTER);
            card.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    box.doClick();
                }
            });
            boxes.add(box);
            panel.add(card);
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
            panel.repaint();
            changed();
        });
        return panel;
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
}
