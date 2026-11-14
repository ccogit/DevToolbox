package de.devtoolbox.ui;

import de.devtoolbox.core.EntitySuggestions;
import de.devtoolbox.core.MigrationProject;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Card "Entitäten für alle Projekte" of the Basisdaten page: code folders (projects with a code folder, plus folders
 * added by hand) are scanned for entities; the result is saved at once as an editable name pool in
 * {@code _basisdaten/entitaeten.json}. Manual edits are saved with "Änderungen speichern".
 */
final class SharedEntitiesCard {

    private SharedEntitiesCard() {
    }

    static JComponent create() {
        JPanel sources = new JPanel();
        sources.setLayout(new BoxLayout(sources, BoxLayout.Y_AXIS));
        sources.setOpaque(false);
        Map<JCheckBox, Path> folders = new LinkedHashMap<>();
        JLabel none = Ui.muted("Kein Projekt mit Code-Ordner – Ordner hinzufügen.", 12f, false);
        sources.add(none); // stays last; checkboxes are inserted before it
        for (MigrationProject p : MigrationProject.all()) {
            // code kept in Bitbucket: the clone of an earlier run, if there is one
            addSource(sources, folders, p.rootCode(), p.name(), "Code");
            addSource(sources, folders, p.referenceCode(), p.name(), "Referenz");
        }
        none.setVisible(folders.isEmpty());
        JScrollPane sourceScroll = new JScrollPane(sources);
        sourceScroll.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
        sourceScroll.setPreferredSize(new Dimension(10, 90));
        sourceScroll.setMinimumSize(new Dimension(10, 60));

        List<String> stored = new ArrayList<>(MigrationProject.sharedEntities());
        JTextArea list = new JTextArea(String.join("\n", stored));
        list.setFont(UiTheme.mono(12.5f));
        JLabel hint = Ui.muted(" ", 12f, false);
        JLabel count = Ui.muted(stored.size() + " gespeichert", 12f, false);
        JButton save = Ui.primaryButton("Änderungen speichern");
        save.setEnabled(false); // only when the text differs from the stored list
        Ui.onDocumentChange(list, () ->
                save.setEnabled(!MigrationProject.parseEntities(list.getText()).equals(stored)));

        // saves the names; returns an error message or null
        Function<List<String>, String> store = names -> {
            try {
                MigrationProject.saveSharedEntities(names);
            } catch (Exception ex) {
                return ex.getMessage();
            }
            stored.clear();
            stored.addAll(MigrationProject.parseEntities(String.join("\n", names)));
            list.setText(String.join("\n", stored));
            list.setCaretPosition(0);
            count.setText(stored.size() + " gespeichert");
            save.setEnabled(false);
            return null;
        };

        JLabel addFolder = Ui.link("Ordner hinzufügen…", () -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Code-Ordner wählen");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (chooser.showOpenDialog(sources) == JFileChooser.APPROVE_OPTION) {
                none.setVisible(false);
                JCheckBox box = addSource(sources, folders, chooser.getSelectedFile().toPath(),
                        chooser.getSelectedFile().getName(), "Ordner");
                if (box != null) {
                    box.setSelected(true);
                }
                sources.revalidate();
                sources.repaint();
            }
        });
        JButton generate = Ui.smallButton("Aus markierten Ordnern ermitteln");
        generate.setToolTipText("Entitäten (Datenstrukturen und Read/Process/Write-Klassen) im Code der markierten Ordner ermitteln und die "
                + "Liste damit ersetzen (wird sofort gespeichert)");
        generate.addActionListener(e -> {
            List<Path> selected = new ArrayList<>();
            folders.forEach((box, path) -> {
                if (box.isSelected()) {
                    selected.add(path);
                }
            });
            if (selected.isEmpty()) {
                JOptionPane.showMessageDialog(sources, "Bitte mindestens einen Code-Ordner ankreuzen.");
                return;
            }
            generate.setEnabled(false);
            hint.setText("Code in " + selected.size() + " Ordner(n) wird gelesen …");
            // reading the code of whole projects takes time: not on the EDT
            Thread.ofVirtual().start(() -> {
                List<String> names = EntitySuggestions.fromFolders(selected);
                SwingUtilities.invokeLater(() -> {
                    generate.setEnabled(true);
                    String error = store.apply(names);
                    if (error != null) {
                        hint.setText(" ");
                        JOptionPane.showMessageDialog(sources, "Speichern fehlgeschlagen:\n" + error);
                        return;
                    }
                    hint.setText(names.size() + " Entitäten aus " + selected.size()
                            + " Ordner(n) ermittelt und gespeichert");
                });
            });
        });
        save.addActionListener(e -> {
            String error = store.apply(MigrationProject.parseEntities(list.getText()));
            if (error != null) {
                JOptionPane.showMessageDialog(sources, "Speichern fehlgeschlagen:\n" + error);
                return;
            }
            hint.setText("Gespeichert");
        });

        JPanel top = Ui.transparent(new BorderLayout(0, 6));
        JPanel topLabel = Ui.transparent(new BorderLayout());
        topLabel.add(Ui.muted("Code-Ordner", 12.5f, false), BorderLayout.WEST);
        topLabel.add(addFolder, BorderLayout.EAST);
        top.add(topLabel, BorderLayout.NORTH);
        top.add(sourceScroll, BorderLayout.CENTER);
        top.add(generate, BorderLayout.SOUTH);

        JPanel middle = Ui.transparent(new BorderLayout(0, 4));
        JPanel label = Ui.transparent(new BorderLayout());
        label.add(Ui.muted("Entitäten (eine je Zeile)", 12.5f, false), BorderLayout.WEST);
        label.add(count, BorderLayout.EAST);
        middle.add(label, BorderLayout.NORTH);
        middle.add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel bottom = Ui.transparent(new BorderLayout(12, 0));
        bottom.add(hint, BorderLayout.CENTER);
        bottom.add(save, BorderLayout.EAST);

        JPanel body = Ui.transparent(new BorderLayout(0, 10));
        body.add(top, BorderLayout.NORTH);
        body.add(middle, BorderLayout.CENTER);
        body.add(bottom, BorderLayout.SOUTH);
        String subtitle = "Namensvorrat für alle Projekte";
        Ui.Card card = Ui.card("☰", "Entitäten für alle Projekte", subtitle, null, body);
        ((JLabel) card.getClientProperty("subtitle")).setToolTipText(subtitle); // the narrow card may cut it off
        return card;
    }

    /** Adds a checkbox for an existing code folder not yet listed; returns it, or null. */
    private static JCheckBox addSource(JPanel sources, Map<JCheckBox, Path> folders, Path folder, String label,
                                       String kind) {
        if (folder == null || !Files.isDirectory(folder) || folders.containsValue(folder)) {
            return null;
        }
        JCheckBox box = new JCheckBox(label + " – " + kind);
        box.setOpaque(false);
        box.setToolTipText(folder.toString());
        sources.add(box, sources.getComponentCount() - 1);
        folders.put(box, folder);
        return box;
    }
}
