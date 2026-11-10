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

/** Input form generated from a job's params; remembers the last values per job. */
final class ParamForm extends JPanel {

    private final Preferences prefs;
    private final List<Param> params;
    private final Map<String, Supplier<String>> getters = new LinkedHashMap<>();
    private final Map<String, Consumer<String>> setters = new HashMap<>();

    ParamForm(String jobId, List<Param> params) {
        super(new GridBagLayout());
        this.params = params;
        prefs = Preferences.userNodeForPackage(ParamForm.class).node(jobId);
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
            JComponent field = switch (p.type()) {
                case FLAG -> checkBox(p);
                case TEXT -> textField(p);
                case DIRECTORY, FILE -> pathField(p);
                case CHOICE -> comboBox(p);
                case MULTI -> checkBoxes(p);
            };
            field.setToolTipText(p.hint());
            add(field, c);
            setters.get(p.key()).accept(prefs.get(p.key(), p.defaultValue()));
            if (p.hint() != null && !p.hint().isBlank()) {
                JLabel hint = new JLabel(p.hint());
                hint.setForeground(Color.GRAY);
                hint.setFont(hint.getFont().deriveFont(hint.getFont().getSize2D() - 1f));
                hint.setToolTipText(p.hint());
                // a long hint must not widen the column (it is cut with "…"; the tooltip shows it in full)
                hint.setPreferredSize(new Dimension(0, hint.getPreferredSize().height));
                hint.setMinimumSize(hint.getPreferredSize());
                c.gridy = row++;
                c.insets = new Insets(0, 8, 2, 4);
                add(hint, c);
            }
        }
        // push everything to the top
        c.gridy = row;
        c.weighty = 1;
        add(Box.createGlue(), c);
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

    void remember() {
        values().forEach(prefs::put);
    }

    private JComponent checkBox(Param p) {
        JCheckBox box = new JCheckBox();
        getters.put(p.key(), () -> String.valueOf(box.isSelected()));
        setters.put(p.key(), v -> box.setSelected(Boolean.parseBoolean(v.trim())));
        return box;
    }

    private JComponent textField(Param p) {
        JTextField field = new JTextField(10);
        getters.put(p.key(), field::getText);
        setters.put(p.key(), field::setText);
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

    private JComponent pathField(Param p) {
        JTextField field = new JTextField(10);
        getters.put(p.key(), field::getText);
        setters.put(p.key(), field::setText);
        JButton browse = new JButton("Durchsuchen...");
        browse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser(field.getText().isBlank() ? null : field.getText());
            if (p.type() == Param.Type.DIRECTORY) {
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            } else if (!p.options().isEmpty()) {
                String[] extensions = p.options().stream().map(Option::value).toArray(String[]::new);
                chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                        String.join(", ", p.options().stream().map(Option::label).toList()), extensions));
            }
            int answer = chooser.showOpenDialog(this);
            if (answer == JFileChooser.APPROVE_OPTION) {
                field.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        JPanel panel = new JPanel(new BorderLayout(6, 0));
        panel.add(field, BorderLayout.CENTER);
        panel.add(browse, BorderLayout.EAST);
        return panel;
    }
}
