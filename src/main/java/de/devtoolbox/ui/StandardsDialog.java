package de.devtoolbox.ui;

import de.devtoolbox.tools.standards.StandardsTool;
import de.devtoolbox.tools.standards.StandardsTool.Row;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * "Standards bearbeiten": the template rows of STANDARDS.md and the base classes of a standards folder; whole
 * classes can be added (as templates or unchanged as base classes) and removed.
 */
final class StandardsDialog {

    private StandardsDialog() {
    }

    static void open(Component parent, Path dir, String title) {
        Window owner = parent instanceof Window w ? w : parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, "Standards bearbeiten – " + title, Dialog.ModalityType.APPLICATION_MODAL);
        DefaultTableModel rowModel = new DefaultTableModel(new String[]{"Phase", "Artefakt", "Vorlage", "Klasse"}, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        JTable table = new JTable(rowModel);
        DefaultListModel<String> baseModel = new DefaultListModel<>();
        JList<String> bases = new JList<>(baseModel);
        List<Row> rows = new ArrayList<>();
        List<Path> baseFiles = new ArrayList<>();
        JLabel info = new JLabel();
        info.setForeground(javax.swing.UIManager.getColor("Label.disabledForeground"));

        Runnable reload = () -> {
            rows.clear();
            baseFiles.clear();
            rowModel.setRowCount(0);
            baseModel.clear();
            try {
                if (Files.isRegularFile(dir.resolve(StandardsTool.INDEX))) {
                    StandardsTool.Stored s = StandardsTool.load(dir);
                    rows.addAll(s.rows());
                    baseFiles.addAll(s.baseFiles());
                    info.setText(dir + (s.example().isEmpty() ? "" : "   ·   Beispiel-Entität: " + s.example()));
                } else {
                    info.setText(dir + "   ·   noch keine Standards (hier ergänzen oder in der Seitenleiste erzeugen)");
                }
            } catch (Exception ex) {
                info.setText("Standards nicht lesbar: " + ex.getMessage());
            }
            rows.forEach(r -> rowModel.addRow(new Object[]{r.phase(), r.kind(), r.template(), r.className()}));
            baseFiles.forEach(f -> baseModel.addElement(f.getFileName().toString()));
        };
        reload.run();

        table.getSelectionModel().addListSelectionListener(e -> {
            if (table.getSelectedRow() >= 0) {
                bases.clearSelection();
            }
        });
        bases.addListSelectionListener(e -> {
            if (bases.getSelectedIndex() >= 0) {
                table.clearSelection();
            }
        });

        JButton addTemplate = new JButton("Vorlage hinzufügen...");
        addTemplate.setToolTipText("Ganze Klassen als Vorlage übernehmen; der Entitätsname wird durch Platzhalter ersetzt");
        addTemplate.addActionListener(e -> {
            for (File file : chooseJava(dialog, "Klassen als Vorlage wählen")) {
                if (!addTemplate(dialog, dir, file.toPath(), title)) {
                    break;
                }
            }
            reload.run();
        });
        JButton addBase = new JButton("Basisklasse hinzufügen...");
        addBase.setToolTipText("Klassen unverändert in basis/ übernehmen");
        addBase.addActionListener(e -> {
            try {
                for (File file : chooseJava(dialog, "Basisklassen wählen")) {
                    StandardsTool.addBase(dir, file.toPath());
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Hinzufügen fehlgeschlagen:\n" + ex.getMessage());
            }
            reload.run();
        });
        JButton remove = new JButton("Entfernen");
        remove.setToolTipText("Ausgewählte Vorlage bzw. Basisklasse entfernen");
        remove.addActionListener(e -> {
            int r = table.getSelectedRow();
            int b = bases.getSelectedIndex();
            if (r < 0 && b < 0) {
                JOptionPane.showMessageDialog(dialog, "Bitte zuerst eine Vorlage oder Basisklasse auswählen.");
                return;
            }
            String what = r >= 0 ? "Vorlage " + rows.get(r).template() : "Basisklasse " + baseFiles.get(b).getFileName();
            if (JOptionPane.showConfirmDialog(dialog, what + " entfernen?\nDie Datei wird gelöscht.", "Entfernen",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
                return;
            }
            try {
                if (r >= 0) {
                    StandardsTool.removeRow(dir, rows.get(r));
                } else {
                    StandardsTool.removeBase(dir, baseFiles.get(b));
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Entfernen fehlgeschlagen:\n" + ex.getMessage());
            }
            reload.run();
        });
        JButton editIndex = new JButton("STANDARDS.md bearbeiten");
        editIndex.setToolTipText("Mit dem Standard-Editor öffnen, z. B. für die Spalte „Hinweise“");
        editIndex.addActionListener(e -> {
            try {
                StandardsTool.ensureStandard(dir, title);
                Desktop.getDesktop().open(dir.resolve(StandardsTool.INDEX).toFile());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Öffnen fehlgeschlagen:\n" + ex.getMessage());
            }
        });
        JButton openDir = new JButton("Ordner öffnen");
        openDir.addActionListener(e -> {
            try {
                Files.createDirectories(dir);
                Desktop.getDesktop().open(dir.toFile());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Öffnen fehlgeschlagen:\n" + ex.getMessage());
            }
        });
        JButton reloadButton = new JButton("Neu laden");
        reloadButton.addActionListener(e -> reload.run());
        JButton close = new JButton("Schließen");
        close.addActionListener(e -> dialog.dispose());

        JPanel buttons = new JPanel(new GridLayout(0, 1, 0, 6));
        for (JButton b : new JButton[]{addTemplate, addBase, remove, editIndex, openDir, reloadButton}) {
            buttons.add(b);
        }
        JPanel side = new JPanel(new BorderLayout());
        side.add(buttons, BorderLayout.NORTH);

        JPanel lists = new JPanel(new GridLayout(2, 1, 0, 8));
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Vorlagen (Zeilen in STANDARDS.md)"));
        JScrollPane baseScroll = new JScrollPane(bases);
        baseScroll.setBorder(BorderFactory.createTitledBorder("Basisklassen (basis/)"));
        lists.add(tableScroll);
        lists.add(baseScroll);

        JPanel closeRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        closeRow.add(close);
        JPanel root = new JPanel(new BorderLayout(12, 10));
        root.setBorder(BorderFactory.createEmptyBorder(14, 16, 12, 16));
        root.add(info, BorderLayout.NORTH);
        root.add(lists, BorderLayout.CENTER);
        root.add(side, BorderLayout.EAST);
        root.add(closeRow, BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.setSize(900, 520);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    private static List<File> chooseJava(Component parent, String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Java-Dateien (*.java)", "java"));
        return chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION ? List.of(chooser.getSelectedFiles()) : List.of();
    }

    /** Asks entity, phase and artefact kind (prefilled from the conventions) and adds the class; false = cancelled. */
    private static boolean addTemplate(Component parent, Path dir, Path file, String title) {
        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            String name = file.getFileName().toString().replaceFirst("\\.java$", "");
            String example = Files.isRegularFile(dir.resolve(StandardsTool.INDEX))
                    ? StandardsTool.load(dir).example() : "";
            String entity = example;
            if (example.isBlank() || !content.contains(example)) {
                JTextField entityField = new JTextField(StandardsTool.guessEntity(name, content));
                if (JOptionPane.showConfirmDialog(parent, new Object[]{"Welcher Entitätsname steckt in " + name
                                + "? (wird durch Platzhalter ersetzt, leer = unverändert)", entityField},
                        "Vorlage " + name, JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
                    return false;
                }
                entity = entityField.getText().strip();
            }
            String[] suggestion = StandardsTool.suggestKind(name, content);
            JComboBox<String> phase = new JComboBox<>(StandardsTool.phaseChoices().toArray(String[]::new));
            phase.setSelectedItem(suggestion[0]);
            JComboBox<String> kind = new JComboBox<>(StandardsTool.kindChoices().toArray(String[]::new));
            kind.setSelectedItem(suggestion[1]);
            if (JOptionPane.showConfirmDialog(parent, new Object[]{"Phase von " + name + ":", phase,
                            "Artefakt:", kind}, "Vorlage " + name, JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
                return false;
            }
            StandardsTool.ensureStandard(dir, title);
            StandardsTool.addTemplate(dir, file, entity, (String) phase.getSelectedItem(), (String) kind.getSelectedItem());
            return true;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, "Hinzufügen fehlgeschlagen:\n" + ex.getMessage());
            return false;
        }
    }
}
