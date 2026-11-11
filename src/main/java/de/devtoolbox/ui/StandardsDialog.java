package de.devtoolbox.ui;

import de.devtoolbox.tools.standards.StandardsTool;
import de.devtoolbox.tools.standards.StandardsTool.Row;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import javax.swing.JComponent;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.GridLayout;
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

    /**
     * The editor as a page.
     *
     * @param back   called by the "Zurück" button
     * @param create optional action of the "Aus Code erzeugen..." button (the page reloads afterwards), may be null
     * @param pageTitle   title of the page header
     * @param subtitle    subtitle of the page header
     * @param right       optional card shown right of the standards (about 1/3 of the width), may be null
     */
    static JPanel panel(Path dir, String title, String pageTitle, String subtitle, Runnable back, Runnable create,
                        JComponent right) {
        JPanel dialog = new JPanel(new BorderLayout());
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
        JLabel note = Ui.muted(" ", 12f, false);
        info.setForeground(UiTheme.MUTED);
        info.setFont(UiTheme.mono(11.5f));
        info.setToolTipText(dir.toString());

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
                    info.setText(shortPath(dir));
                    note.setText(s.example().isEmpty() ? "" : "Beispiel-Entität: " + s.example());
                } else {
                    info.setText(shortPath(dir));
                    note.setText("Noch keine Standards – hier ergänzen oder aus Code erzeugen");
                }
            } catch (Exception ex) {
                info.setText(shortPath(dir));
                note.setText("Standards nicht lesbar: " + ex.getMessage());
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

        JButton addTemplate = Ui.smallButton("Hinzufügen…");
        addTemplate.setToolTipText("Ganze Klassen als Vorlage übernehmen; der Entitätsname wird durch Platzhalter ersetzt");
        addTemplate.addActionListener(e -> {
            for (File file : chooseJava(dialog, "Klassen als Vorlage wählen")) {
                if (!addTemplate(dialog, dir, file.toPath(), title)) {
                    break;
                }
            }
            reload.run();
        });
        JButton addBase = Ui.smallButton("Hinzufügen…");
        addBase.setToolTipText("Klassen unverändert in basis/ übernehmen");
        addBase.addActionListener(e -> {
            try {
                for (File file : chooseJava(dialog, "Vorlagen je Projekt wählen")) {
                    StandardsTool.addBase(dir, file.toPath());
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Hinzufügen fehlgeschlagen:\n" + ex.getMessage());
            }
            reload.run();
        });
        java.awt.event.ActionListener removeAction = e -> {
            int r = table.getSelectedRow();
            int b = bases.getSelectedIndex();
            if (r < 0 && b < 0) {
                JOptionPane.showMessageDialog(dialog, "Bitte zuerst eine Vorlage auswählen.");
                return;
            }
            String what = r >= 0 ? "Vorlage " + rows.get(r).template() : "Vorlage je Projekt " + baseFiles.get(b).getFileName();
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
        };
        JButton removeTemplate = Ui.smallButton("Entfernen");
        removeTemplate.setToolTipText("Ausgewählte Vorlage entfernen");
        removeTemplate.addActionListener(removeAction);
        JButton removeBase = Ui.smallButton("Entfernen");
        removeBase.setToolTipText("Ausgewählte Vorlage je Projekt entfernen");
        removeBase.addActionListener(removeAction);
        JButton close = Ui.secondaryButton("Zurück");
        close.addActionListener(e -> back.run());

        JLabel editIndex = Ui.link("STANDARDS.md bearbeiten", () -> {
            try {
                StandardsTool.ensureStandard(dir, title);
                Desktop.getDesktop().open(dir.resolve(StandardsTool.INDEX).toFile());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Öffnen fehlgeschlagen:\n" + ex.getMessage());
            }
        });
        editIndex.setToolTipText("Mit dem Standard-Editor öffnen, z. B. für die Spalte „Hinweise“");
        JLabel openDir = Ui.link("Ordner öffnen", () -> {
            try {
                Files.createDirectories(dir);
                Desktop.getDesktop().open(dir.toFile());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Öffnen fehlgeschlagen:\n" + ex.getMessage());
            }
        });
        JLabel reloadLink = Ui.link("Neu laden", reload);

        JPanel templateActions = Ui.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        if (create != null) {
            JButton generate = Ui.smallButton("Aus Code erzeugen…");
            generate.setToolTipText("Standards aus bereits umgesetztem Code ableiten");
            generate.addActionListener(e -> {
                create.run();
                reload.run();
            });
            templateActions.add(generate);
        }
        templateActions.add(addTemplate);
        templateActions.add(removeTemplate);
        JPanel baseActions = Ui.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        baseActions.add(addBase);
        baseActions.add(removeBase);

        table.setFillsViewportHeight(true);
        table.setRowHeight(26);
        int[] widths = {90, 120, 260, 260};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        JScrollPane tableScroll = new JScrollPane(table);
        JScrollPane baseScroll = new JScrollPane(bases);
        baseScroll.setPreferredSize(new Dimension(10, 70));
        tableScroll.setPreferredSize(new Dimension(10, 200));
        JComponent templatesCard = Ui.card("≡", "Vorlagen je Entität", "Implementierungs-Standards",
                templateActions, tableScroll);
        JComponent basesCard = Ui.card("▤", "Vorlagen je Projekt (einmalig)",
                "Einmal im Zielprojekt angelegt, danach nur verwendet",
                baseActions, baseScroll);

        JPanel root = dialog;
        root.setLayout(new BorderLayout(0, 12));
        root.setOpaque(false);
        root.setBorder(BorderFactory.createEmptyBorder(20, 24, 14, 24));
        root.add(Ui.pageHeader("≡", pageTitle, subtitle, close), BorderLayout.NORTH);

        JPanel left = Ui.transparent(new BorderLayout(0, 14));
        left.add(templatesCard, BorderLayout.CENTER);
        left.add(basesCard, BorderLayout.SOUTH);
        JPanel body = Ui.transparent(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.gridx = 0;
        c.weightx = 2;
        c.insets = new Insets(0, 0, 0, right == null ? 0 : 7);
        body.add(left, c);
        if (right != null) {
            c.gridx = 1;
            c.weightx = 1;
            c.insets = new Insets(0, 7, 0, 0);
            body.add(right, c);
        }
        JPanel center = Ui.transparent(new BorderLayout(0, 8));
        JPanel links = Ui.transparent(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        links.add(note);
        links.add(editIndex);
        links.add(openDir);
        links.add(reloadLink);
        JPanel infoRow = Ui.transparent(new BorderLayout(16, 0));
        infoRow.add(info, BorderLayout.CENTER);
        infoRow.add(links, BorderLayout.EAST);
        info.setMinimumSize(new Dimension(0, 0));
        center.add(infoRow, BorderLayout.NORTH);
        center.add(body, BorderLayout.CENTER);
        root.add(center, BorderLayout.CENTER);
        return root;
    }

    /** The last three path segments, e.g. "migrationsprojekte/_basisdaten/standards". */
    static String shortPath(Path dir) {
        int n = dir.getNameCount();
        return n <= 3 ? dir.toString() : dir.subpath(n - 3, n).toString().replace('\\', '/');
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
