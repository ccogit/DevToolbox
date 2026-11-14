package de.devtoolbox.ui;

import de.devtoolbox.core.Param;
import de.devtoolbox.core.ProjectLayout;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.List;
import java.util.Map;

/**
 * Page "Einstellungen": the local, company-specific settings (development folder, prefix of the insurance lines'
 * parent projects, Jira projects). They are stored in {@code migrationsprojekte/umgebung.properties}, which Git
 * ignores – so they never end up in the repository and need no write access to the user profile.
 */
final class SettingsPage {

    private static final String DEVELOP = "entwicklungsordner";
    private static final String PREFIX = "projektpraefix";
    private static final String JIRA_LIABILITY = "jiraHaftpflicht";
    private static final String JIRA_PROPERTY = "jiraSach";

    private SettingsPage() {
    }

    /**
     * @param saved called after saving (e.g. to re-derive the project paths), with a line for the log
     */
    static JComponent create(java.util.function.Consumer<String> saved) {
        List<Param> params = List.of(
                Param.directory(DEVELOP, "Entwicklungsordner", false,
                        "Ordner, in dem die Sparten-Projekte liegen; leer = " + ProjectLayout.DEVELOP_ROOT),
                Param.text(PREFIX, "Präfix der Sparten-Projekte", "", false,
                        "Namensanfang der Sparten-Projekte, z. B. „firma-“ für firma-sachvertrag; leer = kein Präfix"),
                Param.text(JIRA_LIABILITY, "Jira-Projekt Haftpflicht", "", false,
                        "Präfix der Spezifikations-Tickets der Haftpflicht; leer = " + ProjectLayout.JIRA_LIABILITY),
                Param.text(JIRA_PROPERTY, "Jira-Projekt Sach", "", false,
                        "Präfix der Spezifikations-Tickets der Sachversicherung; leer = " + ProjectLayout.JIRA_PROPERTY));
        ParamForm form = new ParamForm("einstellungen", params);
        form.setOpaque(false);
        ProjectLayout.Settings stored = ProjectLayout.storedSettings();
        form.setValues(Map.of(DEVELOP, stored.developFolder(), PREFIX, stored.prefix(),
                JIRA_LIABILITY, stored.jiraLiability(), JIRA_PROPERTY, stored.jiraProperty()));

        JLabel status = Ui.muted(location(), 12f, false);
        JButton save = Ui.primaryButton("Speichern");
        save.addActionListener(e -> {
            Map<String, String> v = form.values();
            try {
                java.nio.file.Path file = ProjectLayout.saveSettings(new ProjectLayout.Settings(v.get(DEVELOP),
                        v.get(PREFIX), v.get(JIRA_LIABILITY), v.get(JIRA_PROPERTY)));
                status.setText("Gespeichert in " + file + " · Entwicklungsordner jetzt: " + ProjectLayout.developRoot()
                        + " · Jira: " + ProjectLayout.jiraLiability() + " / " + ProjectLayout.jiraProperty());
                status.setForeground(UiTheme.ACCENT);
                saved.accept("Einstellungen gespeichert: " + file);
            } catch (Exception ex) {
                status.setText("Nicht gespeichert: " + ex.getMessage());
                status.setForeground(UiTheme.WARNING);
            }
        });

        JPanel body = Ui.transparent(new BorderLayout(0, 10));
        body.add(form, BorderLayout.CENTER);
        JPanel south = Ui.transparent(new BorderLayout(0, 8));
        List<String> overriding = ProjectLayout.overridingProperties();
        if (!overriding.isEmpty()) {
            JLabel warn = Ui.muted("Hinweis: beim Start gesetzt und vorrangig: " + String.join(", ", overriding), 12f, false);
            warn.setForeground(UiTheme.WARNING);
            south.add(warn, BorderLayout.NORTH);
        }
        south.add(status, BorderLayout.CENTER);
        JPanel buttons = Ui.transparent(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.add(save);
        south.add(buttons, BorderLayout.SOUTH);
        body.add(south, BorderLayout.SOUTH);

        JPanel page = Ui.transparent(new BorderLayout(0, 16));
        page.setBorder(new EmptyBorder(20, 24, 16, 24));
        page.add(Ui.pageHeader("⚙", "Einstellungen", "Lokal für diesen Rechner – werden nicht ins Repository übernommen",
                null), BorderLayout.NORTH);
        JPanel cards = Ui.transparent(new GridLayout(0, 1, 0, 14));
        cards.add(Ui.card("⌂", "Umgebung", "Firmenspezifische Pfade und Schlüssel; leere Felder = Standardwert",
                null, body));
        JPanel wrap = Ui.transparent(new BorderLayout());
        wrap.add(cards, BorderLayout.NORTH);
        page.add(wrap, BorderLayout.CENTER);
        return page;
    }

    private static String location() {
        return "Gespeichert wird in " + ProjectLayout.settingsFile();
    }
}
