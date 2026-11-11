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
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Card "Übergreifende Entitäten" of the Basisdaten page: reference code folders (projects with a code folder, plus
 * folders added by hand) are scanned for entities; the merged result is an editable list stored in
 * {@code _basisdaten/entitaeten.json}.
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
            addSource(sources, folders, p.root(), p.name(), "Code");
            addSource(sources, folders, p.reference(), p.name(), "Referenz");
        }
        none.setVisible(folders.isEmpty());
        JScrollPane sourceScroll = new JScrollPane(sources);
        sourceScroll.setBorder(BorderFactory.createLineBorder(UiTheme.BORDER));
        sourceScroll.setPreferredSize(new Dimension(10, 90));
        sourceScroll.setMinimumSize(new Dimension(10, 60));

        JTextArea list = new JTextArea(String.join("\n", MigrationProject.sharedEntities()));
        list.setFont(UiTheme.mono(12.5f));
        JLabel hint = Ui.muted(" ", 12f, false);
        JLabel count = Ui.muted(MigrationProject.sharedEntities().size() + " gespeichert", 12f, false);

        JLabel addFolder = Ui.link("Ordner hinzufügen…", () -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Code-Ordner als Referenz wählen");
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
        JButton generate = Ui.smallButton("Aus Referenzprojekten erzeugen");
        generate.setToolTipText("Entitäten (TO, PO, DTO, Entity) im Code der gewählten Ordner ermitteln; "
                + "ersetzt die aktuelle Liste");
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
            List<String> names = EntitySuggestions.fromFolders(selected);
            list.setText(String.join("\n", names));
            list.setCaretPosition(0);
            hint.setText(names.size() + " Entitäten aus " + selected.size() + " Ordner(n) – noch nicht gespeichert");
        });
        JButton save = Ui.primaryButton("Speichern");
        save.addActionListener(e -> {
            try {
                List<String> names = MigrationProject.parseEntities(list.getText());
                MigrationProject.saveSharedEntities(names);
                list.setText(String.join("\n", names));
                hint.setText("Gespeichert");
                count.setText(names.size() + " gespeichert");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(sources, "Speichern fehlgeschlagen:\n" + ex.getMessage());
            }
        });

        JPanel top = Ui.transparent(new BorderLayout(0, 6));
        JPanel topLabel = Ui.transparent(new BorderLayout());
        topLabel.add(Ui.muted("Referenz-Code", 12.5f, false), BorderLayout.WEST);
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
        return Ui.card("☰", "Übergreifende Entitäten", "Für alle Projekte", null, body);
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
