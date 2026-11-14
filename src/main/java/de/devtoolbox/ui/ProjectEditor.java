package de.devtoolbox.ui;

import de.devtoolbox.core.EntitySuggestions;
import de.devtoolbox.core.Json;
import de.devtoolbox.core.MigrationProject;
import de.devtoolbox.core.Param;
import de.devtoolbox.core.Param.Option;
import de.devtoolbox.tools.standards.StandardsProposal;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

/**
 * Dialog to create, edit or delete a migration project ({@code migrationsprojekte/<id>/projekt.json}) with the same
 * kind of form as the job configuration. Unknown entries of an existing file are kept.
 */
final class ProjectEditor {

    private static final String ID = "id";
    private static final String REFERENCE_PROJECT = "referenzProjekt";
    private static final String REFERENCE_PATH = "referenzPfad";
    private ProjectEditor() {
    }

    /**
     * The editor as a page.
     *
     * @param project      the project to edit, or {@code null} to create a new one
     * @param done         called with the outcome; {@code null} = cancelled
     * @param newProject   opens the page for a new project (only offered when editing)
     * @param switchTo     opens the page for another project (chosen in the header drop-down)
     * @param importSchema imports DDL files into the project (only offered when editing)
     */
    static JPanel panel(MigrationProject project, java.util.function.Consumer<Result> done, Runnable newProject,
                        java.util.function.Consumer<MigrationProject> switchTo, Runnable importSchema, java.util.function.BiConsumer<Path, String> editStandardsPage) {
        Path projectsDir = MigrationProject.directory();
        List<MigrationProject> others = MigrationProject.all().stream()
                .filter(p -> project == null || !p.id().equals(project.id())).toList();
        List<Param> params = params(project == null, others);
        ParamForm form = new ParamForm("projekt-editor", params, false);
        // root is a module of the line's parent project: target model and mapping templates lie in its
        // designer-services module – prefilled, but the user may overwrite them
        form.deriveFrom("root", Map.of(
                "zielmodell", r -> string(de.devtoolbox.core.ProjectLayout.targetModel(pathOrNull(r))),
                "mappingvorlagen", r -> string(de.devtoolbox.core.ProjectLayout.mappingTemplates(pathOrNull(r))),
                "sparte", r -> de.devtoolbox.core.ProjectLayout.sparte(pathOrNull(r))));
        // all projects lie in the development folder (ProjectLayout.developRoot): the migration module follows from insurance line and id
        String fixedId = project == null ? null : project.id();
        java.util.function.Function<String, String> rootOf = ignored -> string(de.devtoolbox.core.ProjectLayout.root(
                form.value("sparte"), fixedId != null ? fixedId : form.value(ID)));
        form.deriveFrom("sparte", Map.of("root", rootOf));
        if (project == null) {
            form.deriveFrom(ID, Map.of("root", rootOf));
        }
        form.setValues(initialValues(project, others));

        // entities: the effective list is derived from the sources and only displayed; the user maintains just the
        // extra names that are in no source (stored in "entitaeten")
        JTextArea extras = new JTextArea(project == null ? "" : String.join("\n", project.entities()), 3, 20);
        extras.setFont(UiTheme.mono(12.5f));
        JTextArea derivedList = new JTextArea(5, 20);
        derivedList.setEditable(false);
        derivedList.setFont(UiTheme.text(Font.PLAIN, 12.5f));
        derivedList.setText("Wird ermittelt …");
        java.util.concurrent.atomic.AtomicReference<EntitySuggestions.Effective> latest =
                new java.util.concurrent.atomic.AtomicReference<>();

        JPanel page = new JPanel(new BorderLayout(0, 16));
        page.setOpaque(false);
        page.setBorder(BorderFactory.createEmptyBorder(20, 24, 14, 24));
        JButton save = Ui.primaryButton("Speichern");
        JButton cancel = Ui.secondaryButton("Abbrechen");
        cancel.addActionListener(e -> done.accept(null));
        save.addActionListener(e -> {
            try {
                done.accept(new Result(save(projectsDir, project, form.values(), extrasToStore(latest.get(), extras.getText())), false));
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(page, ex.getMessage());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(page, "Speichern fehlgeschlagen:\n" + ex.getMessage());
            }
        });

        // header: project drop-down, "Neu"/"Löschen…" as small secondary actions, Abbrechen and Speichern
        JPanel actions = Ui.transparent(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        String[] snapshot = {""};
        java.util.function.Supplier<String> state = () -> form.values() + "|" + extras.getText();
        List<MigrationProject> all = MigrationProject.all();
        MigrationProject current = project == null ? null
                : all.stream().filter(p -> p.id().equals(project.id())).findFirst().orElse(null);
        JComboBox<MigrationProject> projectChoice = new JComboBox<>(all.toArray(MigrationProject[]::new));
        projectChoice.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected,
                                                          boolean focus) {
                return super.getListCellRendererComponent(list,
                        value == null ? "– Neues Projekt –" : ((MigrationProject) value).label(), index, selected, focus);
            }
        });
        projectChoice.setPreferredSize(new Dimension(260, 34));
        projectChoice.setSelectedItem(current);
        if (current == null) {
            projectChoice.setSelectedIndex(-1);
        }
        projectChoice.setToolTipText("Projekt zum Bearbeiten wählen");
        projectChoice.addActionListener(e -> {
            MigrationProject chosen = (MigrationProject) projectChoice.getSelectedItem();
            if (chosen == null || (current != null && chosen.id().equals(current.id()))) {
                return;
            }
            if (!state.get().equals(snapshot[0]) && JOptionPane.showConfirmDialog(page,
                    "Ungespeicherte Änderungen verwerfen und „" + chosen.label() + "“ laden?", "Projekt wechseln",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
                projectChoice.setSelectedItem(current);
                return;
            }
            switchTo.accept(chosen);
        });
        actions.add(projectChoice);
        actions.add(Ui.link("Neu", newProject));
        if (project != null) {
            JLabel delete = Ui.link("Löschen…", () -> {
                try {
                    if (delete(page, project)) {
                        done.accept(new Result(project.id(), true));
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(page, "Löschen fehlgeschlagen:\n" + ex.getMessage());
                }
            });
            delete.setForeground(new Color(0xF0616D));
            delete.setToolTipText("Projektordner mit projekt.json und Struktur der Altdaten (DDL) löschen");
            actions.add(delete);
        }
        actions.add(cancel);
        actions.add(save);
        page.add(Ui.pageHeader("▤", project == null ? "Migrationsprojekt anlegen" : "Migrationsprojekt",
                (project == null ? "Neues Projekt" : project.label()) + "  ·  " + projectsDir.getFileName() + "/"
                        + (project == null ? "<id>" : project.id()) + "/projekt.json",
                actions), BorderLayout.NORTH);

        // cards
        JComponent stammdaten = Ui.card("▤", "Stammdaten", "Name, Sparte; Export/Import des ganzen Projekts.",
                configurationLinks(project, page, done),
                north(JobView.fields(form, params.stream().filter(p -> List.of(ID, "name", "produkt", "sparte",
                        "beschreibung").contains(p.key())).toList(), Map.of())));
        Map<String, JComponent> trailing = new HashMap<>();
        if (project != null) {
            JButton schema = Ui.smallButton("DDL importieren…");
            schema.setToolTipText("DDL-Dateien der Altdaten-Tabellen dieses Projekts in den DDL-Ordner des Projekts übernehmen");
            schema.addActionListener(e -> importSchema.run());
            trailing.put("ddl", schema);
        }
        JComponent pfade = Ui.card("⌂", "Pfade", "Code, Referenz und Quellen der Migration; Pfade im Projektordner werden relativ abgelegt.",
                null, north(JobView.fields(form, params.stream().filter(p -> List.of("root", REFERENCE_PROJECT,
                                REFERENCE_PATH, "ddl", "zielmodell", "mappingvorlagen").contains(p.key())).toList(), trailing,
                        java.util.Set.of("root", "ddl", "zielmodell", "mappingvorlagen"))));

        // entities: read-only display of the derived list above, the extra names below
        List<String> shared = MigrationProject.sharedEntities();
        JPanel entityBody = Ui.transparent(new BorderLayout(0, 8));
        JScrollPane derivedScroll = new JScrollPane(derivedList);
        derivedScroll.setPreferredSize(new Dimension(10, 60));
        entityBody.add(derivedScroll, BorderLayout.CENTER);
        JPanel extraBox = Ui.transparent(new BorderLayout(0, 4));
        JPanel extraHead = Ui.transparent(new BorderLayout());
        extraHead.add(Ui.muted("Zusätzliche Entitäten (eine je Zeile)", 12.5f, false), BorderLayout.WEST);
        if (!shared.isEmpty()) {
            extraHead.add(Ui.link("Aus dem Namensvorrat ergänzen…",
                    () -> adoptShared(page, shared, latest.get(), extras)), BorderLayout.EAST);
        }
        extraBox.add(extraHead, BorderLayout.NORTH);
        JScrollPane extraScroll = new JScrollPane(extras);
        extraScroll.setPreferredSize(new Dimension(10, 66));
        extraBox.add(extraScroll, BorderLayout.CENTER);
        entityBody.add(extraBox, BorderLayout.SOUTH);
        Ui.Card entityCard = Ui.card("☰", "Entitäten", "Wird ermittelt …", null, entityBody);
        JLabel entitySubtitle = (JLabel) entityCard.getClientProperty("subtitle");

        // shows a derived list; extras that the list already contains are dropped from the extras field
        java.util.function.Consumer<EntitySuggestions.Effective> showEntities = e -> {
            latest.set(e);
            boolean clean = state.get().equals(snapshot[0]);
            derivedList.setText(e.automatic().isEmpty()
                    ? "Nichts gefunden – bitte oben das Zielmodell, die DDL oder das Referenzprojekt angeben."
                    : e.automatic().stream().map(EntitySuggestions.Item::label)
                    .collect(java.util.stream.Collectors.joining("\n")));
            derivedList.setCaretPosition(0);
            entitySubtitle.setText(e.fromTarget() ? "Automatisch aus dem Zielmodell ermittelt"
                    : "Automatisch aus Altdaten und Referenzcode ermittelt");
            List<String> typed = MigrationProject.parseEntities(extras.getText());
            List<String> kept = e.withoutAutomatic(typed);
            if (!kept.equals(typed)) {
                extras.setText(String.join("\n", kept));
                if (clean) {
                    snapshot[0] = state.get();
                }
            }
        };
        // read the sources off the EDT; a new run makes older ones obsolete (live update while paths are edited)
        long[] generation = {0};
        Runnable derive = () -> {
            long mine = ++generation[0];
            Path ddl = pathOrDerived(form.value("ddl"), project == null ? null : project.ddl());
            Path model = pathOrDerived(form.value("zielmodell"), project == null ? null : project.targetModel());
            Path templates = pathOrDerived(form.value("mappingvorlagen"),
                    project == null ? null : project.mappingTemplates());
            Path reference = referencePath(form, others);
            Thread.ofVirtual().start(() -> {
                EntitySuggestions.Effective e = EntitySuggestions.effective(ddl, model, templates, reference, List.of());
                SwingUtilities.invokeLater(() -> {
                    if (mine == generation[0]) {
                        showEntities.accept(e);
                    }
                });
            });
        };
        javax.swing.Timer debounce = new javax.swing.Timer(600, e -> derive.run());
        debounce.setRepeats(false);
        for (String key : List.of("ddl", "zielmodell", "mappingvorlagen", REFERENCE_PROJECT, REFERENCE_PATH)) {
            form.onChange(key, v -> debounce.restart());
        }
        derive.run();

        JComponent standardsCard = standardsCard(project, editStandardsPage, page);

        // two columns: paths + standards on the left, master data on the right; the entities use the full width below
        JPanel leftColumn = Ui.transparent(new BorderLayout(0, 14));
        leftColumn.add(pfade, BorderLayout.NORTH);
        JPanel rightColumn = Ui.transparent(new BorderLayout(0, 14));
        rightColumn.add(stammdaten, BorderLayout.NORTH);
        rightColumn.add(standardsCard, BorderLayout.CENTER);
        JPanel grid = Ui.transparent(new GridLayout(1, 2, 14, 0));
        grid.add(leftColumn);
        grid.add(rightColumn);
        // BorderLayout: the columns always keep their full height, the entities take the rest
        JPanel content = Ui.transparent(new BorderLayout(0, 14));
        content.add(grid, BorderLayout.NORTH);
        content.add(entityCard, BorderLayout.CENTER);
        page.add(content, BorderLayout.CENTER);
        snapshot[0] = state.get();
        return page;
    }

    /**
     * "Exportieren…"/"Importieren…" in the page header: the whole project (projekt.json, entities, job configurations).
     */
    private static JComponent configurationLinks(MigrationProject project, Component parent,
                                                 java.util.function.Consumer<Result> done) {
        JButton export = new JButton("Exportieren…");
        JButton imp = new JButton("Importieren…");
        for (JButton b : List.of(export, imp)) {
            b.putClientProperty("JButton.buttonType", "borderless");
            b.setForeground(UiTheme.ACCENT);
        }
        export.setEnabled(project != null);
        export.setToolTipText("Gespeicherter Stand: projekt.json, Entitätenliste und alle Job-Konfigurationen des Projekts "
                + "in eine JSON-Datei");
        imp.setToolTipText("Eine exportierte Datei einlesen: ersetzt projekt.json und die Job-Konfigurationen des Projekts");
        export.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(new java.io.File(project.id() + "-konfiguration.json"));
            if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            Path file = chooser.getSelectedFile().toPath();
            if (Files.exists(file) && JOptionPane.showConfirmDialog(parent, file.getFileName() + " existiert bereits. Überschreiben?",
                    "Konfiguration exportieren", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
                return;
            }
            try {
                de.devtoolbox.core.ProjectConfigs.export(project, file);
                JOptionPane.showMessageDialog(parent, "Konfiguration exportiert:\n" + file);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(parent, "Export fehlgeschlagen:\n" + ex.getMessage());
            }
        });
        imp.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON (*.json)", "json"));
            if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            try {
                Path file = chooser.getSelectedFile().toPath();
                String id = project != null ? project.id() : de.devtoolbox.core.ProjectConfigs.exportedId(file);
                boolean exists = MigrationProject.find(id).isPresent();
                if (JOptionPane.showConfirmDialog(parent, (exists
                                ? "Das Projekt „" + id + "“ wird überschrieben: projekt.json und die Job-Konfigurationen "
                                + "aus der Datei ersetzen die bisherigen."
                                : "Es wird das neue Projekt „" + id + "“ angelegt.") + "\nFortfahren?",
                        "Konfiguration importieren", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                        != JOptionPane.YES_OPTION) {
                    return;
                }
                de.devtoolbox.core.ProjectConfigs.importInto(file, id);
                done.accept(new Result(id, false));
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(parent, "Import fehlgeschlagen:\n" + ex.getMessage());
            }
        });
        JPanel buttons = Ui.transparent(new FlowLayout(FlowLayout.LEFT, 2, 0));
        buttons.setToolTipText("Job-Konfigurationen werden je Projekt automatisch gespeichert (konfigurationen/)");
        buttons.add(export);
        buttons.add(imp);
        return buttons;
    }

    private static JComponent north(JComponent c) {
        JPanel p = Ui.transparent(new BorderLayout());
        p.add(c, BorderLayout.NORTH);
        return p;
    }

    /**
     * Card "Implementierungs-Standards": which standards apply. One primary button – "Erzeugen…" without own
     * standards, "Bearbeiten…" with them; "Neu erzeugen…" and "Entfernen" are small links.
     */
    private static JComponent standardsCard(MigrationProject project, java.util.function.BiConsumer<Path, String> edit,
                                            Component parent) {
        String subtitle = "Nur für den Job Implementierung – einmal je Projekt aus bereits umgesetztem Code erzeugen.";
        JLabel status = Ui.muted(" ", 12.5f, false);
        JPanel actions = Ui.transparent(new FlowLayout(FlowLayout.LEFT, 14, 0));
        JPanel body = Ui.transparent(new BorderLayout(0, 10));
        body.add(status, BorderLayout.NORTH);
        body.add(actions, BorderLayout.CENTER);
        if (project == null) {
            status.setText("Nach dem Speichern lassen sich projekteigene Standards erzeugen.");
            return Ui.card("≡", "Implementierungs-Standards", subtitle, null, body);
        }
        Runnable[] refresh = {() -> {
        }};
        Runnable generateNew = () -> proposeStandards(parent, project, project.ownStandardsDirectory(),
                "Projekteigene Standards – " + project.label(), () -> refresh[0].run());
        JButton generate = Ui.primaryButton("Erzeugen…");
        generate.setToolTipText("Erzeugt Standards nur für dieses Projekt; sie ersetzen die übergreifenden (Seite Basisdaten)");
        generate.addActionListener(e -> generateNew.run());
        JButton editButton = Ui.primaryButton("Bearbeiten…");
        editButton.setToolTipText("Vorlagen je Entität und je Projekt der projekteigenen Standards bearbeiten");
        editButton.addActionListener(e -> edit.accept(project.ownStandardsDirectory(), project.label()));
        JLabel regenerate = Ui.link("Neu erzeugen…", generateNew);
        regenerate.setToolTipText("Die projekteigenen Standards aus Code neu erzeugen (die bisherigen bleiben als Sicherung)");
        JLabel remove = Ui.link("Entfernen", () -> {
            if (JOptionPane.showConfirmDialog(parent, "Projekteigene Standards von " + project.label()
                            + " löschen?\n" + project.ownStandardsDirectory() + "\nDanach gelten die übergreifenden Standards.",
                    "Projekteigene Standards entfernen", JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
                return;
            }
            try {
                deleteFolder(project.ownStandardsDirectory());
                refresh[0].run();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(parent, "Entfernen fehlgeschlagen:\n" + ex.getMessage());
            }
        });
        remove.setToolTipText("Projekteigene Standards löschen; es gelten wieder die übergreifenden");
        refresh[0] = () -> {
            boolean own = project.hasOwnStandards();
            status.setText(own ? "Es gelten die projekteigenen Standards." : "Es gelten die übergreifenden Standards.");
            status.setToolTipText(String.valueOf(project.standardsDirectory()));
            generate.setVisible(!own);
            editButton.setVisible(own);
            regenerate.setVisible(own);
            remove.setVisible(own);
        };
        actions.add(generate);
        actions.add(editButton);
        actions.add(regenerate);
        actions.add(remove);
        refresh[0].run();
        return Ui.card("≡", "Implementierungs-Standards", subtitle, null, body);
    }

    /**
     * Generates standards into {@code target} after the user chose the source (own code, reference project or
     * any folder) and the example entity; existing standards are kept as a backup. {@code p} is the project whose code is offered first (may be
     * {@code null} for the cross-project standards: then the code of all projects and their references is offered);
     * {@code title} is written into the index.
     */
    static void generateStandards(Component parent, MigrationProject p, Path target, String title) {
        try {
            if (target == null) {
                JOptionPane.showMessageDialog(parent, "Es gibt keinen Ordner für Migrationsprojekte.");
                return;
            }
            Map<String, Path> sources = standardSources(p);
            String choose = "Anderes Verzeichnis wählen…";
            sources.put(choose, null);
            String sourceKey = (String) JOptionPane.showInputDialog(parent, "Woher sollen die Standards kommen?\n"
                            + "Wähle Code, in dem die Entitäten bereits nach den gewünschten Konventionen "
                            + "umgesetzt sind.", title,
                    JOptionPane.QUESTION_MESSAGE, null, sources.keySet().toArray(), sources.keySet().iterator().next());
            if (sourceKey == null) {
                return;
            }
            Path source = sources.get(sourceKey);
            if (source == null) {
                JFileChooser chooser = new JFileChooser(p != null && p.rootCode() != null ? p.rootCode().toFile() : null);
                chooser.setDialogTitle("Code-Verzeichnis (Root) für die Standards wählen");
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) {
                    return;
                }
                source = chooser.getSelectedFile().toPath();
            }
            List<de.devtoolbox.tools.standards.StandardsTool.Candidate> candidates =
                    de.devtoolbox.tools.standards.StandardsTool.candidates(source);
            if (candidates.isEmpty()) {
                JOptionPane.showMessageDialog(parent, "In " + source + " wurden keine umgesetzten Entitäten "
                        + "(TO, PO, Entity, Repository, Logik) gefunden.");
                return;
            }
            String[] labels = candidates.stream().map(de.devtoolbox.tools.standards.StandardsTool.Candidate::label)
                    .toArray(String[]::new);
            String chosen = (String) JOptionPane.showInputDialog(parent, "Beispiel-Entität für die Standards "
                            + "(vorgeschlagen: die vollständigste):", title, JOptionPane.QUESTION_MESSAGE,
                    null, labels, labels[0]);
            if (chosen == null) {
                return;
            }
            String example = candidates.get(java.util.Arrays.asList(labels).indexOf(chosen)).name();
            if (!confirmReplace(parent, target, title)) {
                return;
            }
            reportGenerated(parent, de.devtoolbox.tools.standards.StandardsTool
                    .generate(source, example, target, title, m -> {
                    }), title);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, "Standards konnten nicht erzeugt werden:\n" + ex.getMessage());
        }
    }

    /**
     * Code that may already follow the wanted conventions, labelled for display: the project's own code and its
     * reference, then the code of the other projects (for the cross-project standards also their references).
     */
    private static Map<String, Path> standardSources(MigrationProject p) {
        Map<String, Path> sources = new LinkedHashMap<>();
        if (p != null && p.rootCode() != null && Files.isDirectory(p.rootCode())) {
            sources.put("Code dieser Migration – " + p.rootCode(), p.rootCode());
        }
        if (p != null && p.reference() != null && Files.isDirectory(p.reference())) {
            sources.put("Referenzprojekt – " + p.referenceLabel(), p.reference());
        }
        for (MigrationProject other : MigrationProject.all()) {
            Path otherCode = other.rootCode();
            if ((p == null || !other.id().equals(p.id())) && otherCode != null
                    && Files.isDirectory(otherCode) && !sources.containsValue(otherCode)) {
                sources.put((p == null ? "Migrationsprojekt – " : "Anderes Migrationsprojekt – ") + other.label(),
                        otherCode);
            }
            if (p == null && other.reference() != null && Files.isDirectory(other.reference())
                    && !sources.containsValue(other.reference())) {
                sources.put("Referenz von " + other.label() + " – " + other.reference(), other.reference());
            }
        }
        return sources;
    }

    /** Asks before existing (possibly edited) standards are replaced; true if there are none or the user agrees. */
    private static boolean confirmReplace(Component parent, Path dir, String title) {
        return !Files.exists(dir.resolve(de.devtoolbox.tools.standards.StandardsTool.INDEX))
                || JOptionPane.showConfirmDialog(parent, "Es gibt bereits Standards (ggf. bearbeitet). Ersetzen?\n"
                        + "Die bisherigen bleiben als Sicherung „" + dir.getFileName() + "-<Zeitpunkt>“ daneben.",
                title, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    /** Tells what was generated and offers to open the folder. */
    private static void reportGenerated(Component parent, de.devtoolbox.tools.standards.StandardsTool.Summary s,
                                        String title) throws java.io.IOException {
        String text = "Standards erzeugt: " + s.templates() + " Vorlagen und " + s.baseTypes()
                + " Vorlagen je Projekt aus „" + s.example() + "“.\n" + s.dir()
                + (s.backup() == null ? "" : "\nSicherung der bisherigen: " + s.backup().getFileName())
                + "\n\nDu kannst STANDARDS.md und die Vorlagen jetzt überarbeiten. Ordner öffnen?";
        if (JOptionPane.showConfirmDialog(parent, text, title, JOptionPane.YES_NO_OPTION)
                == JOptionPane.YES_OPTION) {
            Desktop.getDesktop().open(s.dir().toFile());
        }
    }

    /**
     * One click instead of two choosers: scans the known code sources off the EDT, shows what the richest one
     * would yield and writes it after confirmation. "Anpassen…" (and the case that nothing is found) leads to the
     * manual choice of source and example entity.
     *
     * @param refresh run on the EDT after standards may have changed; may be null
     */
    static void proposeStandards(Component parent, MigrationProject p, Path target, String title, Runnable refresh) {
        if (target == null) {
            JOptionPane.showMessageDialog(parent, "Es gibt keinen Ordner für Migrationsprojekte.");
            return;
        }
        Runnable changed = refresh == null ? () -> {
        } : refresh;
        Runnable manual = () -> {
            generateStandards(parent, p, target, title);
            changed.run();
        };
        List<Path> sources = List.copyOf(standardSources(p).values());
        busy(parent, () -> StandardsProposal.best(sources), proposal -> {
            if (proposal.isEmpty()) {
                if (JOptionPane.showConfirmDialog(parent, "In den bekannten Code-Quellen wurde keine umgesetzte "
                                + "Entität erkannt.\nCode-Verzeichnis selbst wählen?", title,
                        JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    manual.run();
                }
                return;
            }
            StandardsProposal.Proposal found = proposal.get();
            Object[] options = {"Vorschlag übernehmen", "Anpassen…", "Abbrechen"};
            int choice = JOptionPane.showOptionDialog(parent, proposalText(found), title, JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            if (choice == 1) {
                manual.run();
            } else if (choice == 0 && confirmReplace(parent, target, title)) {
                busy(parent, () -> de.devtoolbox.tools.standards.StandardsTool.generate(found.source(),
                        found.preview().entity(), target, title, m -> {
                        }), summary -> {
                    changed.run();
                    reportGenerated(parent, summary, title);
                });
            }
        });
    }

    private static String proposalText(StandardsProposal.Proposal proposal) {
        de.devtoolbox.tools.standards.StandardsTool.Preview preview = proposal.preview();
        List<String> templates = preview.templates();
        return "Empfohlene Quelle: " + proposal.source() + "\n\n" + preview.summary()
                + "\n\nVorlagen:\n  " + String.join("\n  ", templates.subList(0, Math.min(6, templates.size())))
                + (templates.size() > 6 ? "\n  … und " + (templates.size() - 6) + " weitere" : "")
                + (preview.warnings().isEmpty() ? "" : "\n\nBitte prüfen: " + String.join("; ", preview.warnings()))
                + "\n\nÜber „Anpassen…“ lassen sich Quelle und Beispiel-Entität selbst wählen.";
    }

    private interface Work<T> {
        T run() throws Exception;
    }

    private interface Then<T> {
        void accept(T result) throws Exception;
    }

    /** Runs {@code work} off the EDT with a wait cursor, then {@code then} on the EDT; failures are shown. */
    private static <T> void busy(Component parent, Work<T> work, Then<T> then) {
        Cursor previous = parent.getCursor();
        parent.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        Thread.ofVirtual().start(() -> {
            T result = null;
            Exception failure = null;
            try {
                result = work.run();
            } catch (Exception ex) {
                failure = ex;
            }
            T value = result;
            Exception error = failure;
            SwingUtilities.invokeLater(() -> {
                parent.setCursor(previous);
                try {
                    if (error != null) {
                        throw error;
                    }
                    then.accept(value);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(parent, "Standards konnten nicht erzeugt werden:\n" + ex.getMessage());
                }
            });
        });
    }

    /**
     * Deletes the project folder after a confirmation that names what is lost. Code, reference and target model
     * outside the folder are not touched.
     */
    private static boolean delete(Component parent, MigrationProject project) throws Exception {
        List<Path> files;
        try (java.util.stream.Stream<Path> walk = Files.walk(project.folder())) {
            files = walk.filter(Files::isRegularFile).toList();
        }
        List<String> users = MigrationProject.all().stream()
                .filter(p -> !p.id().equals(project.id()) && p.referenceLabel().equals(project.label()))
                .map(MigrationProject::label).toList();
        StringBuilder text = new StringBuilder("Migrationsprojekt „" + project.label() + "“ löschen?\n\n")
                .append("Gelöscht wird der Ordner\n").append(project.folder()).append("\nmit ").append(files.size())
                .append(files.size() == 1 ? " Datei" : " Dateien").append(" (projekt.json, DDL, Standards …).\n")
                .append("Code, Referenzprojekt und Zielmodell außerhalb dieses Ordners bleiben erhalten.");
        if (!users.isEmpty()) {
            text.append("\n\nAchtung: Als Referenzprojekt verwendet von\n  ").append(String.join("\n  ", users))
                    .append("\nDort ist die Referenz danach nicht mehr konfiguriert.");
        }
        int answer = JOptionPane.showConfirmDialog(parent, text.toString(), "Migrationsprojekt löschen",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) {
            return false;
        }
        deleteFolder(project.folder());
        return true;
    }

    static void deleteFolder(Path folder) throws java.io.IOException {
        try (java.util.stream.Stream<Path> walk = Files.walk(folder)) {
            for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }

    private static List<Param> params(boolean create, List<MigrationProject> others) {
        List<Option> references = new ArrayList<>();
        references.add(new Option("", "– keins (Pfad unten angeben) –"));
        others.forEach(p -> references.add(new Option(p.id(), p.label())));
        List<Param> params = new ArrayList<>();
        if (create) {
            params.add(Param.text(ID, "Id", "", true, "Ordnername, Kleinbuchstaben, z. B. vhv – nicht mehr änderbar"));
        }
        params.add(Param.text("name", "Kurzname", "", true, "z. B. VHV"));
        params.add(Param.text("produkt", "Produkt", "", false, "z. B. Hausrat"));
        params.add(Param.text("sparte", "Sparte", "", false, "z. B. Sachversicherung, Haftpflichtversicherung"));
        params.add(Param.directory("root", "Code dieser Migration", false,
                "Migrationsmodul (Zielprojekt der Implementierung) – Ordner oder Bitbucket-Adresse (Link aus dem "
                        + "Browser oder Klon-Adresse HTTPS/SSH). Wird aus Sparte und Id vorbelegt: Modul mit der Id "
                        + "im Namen im Sparten-Projekt unter " + de.devtoolbox.core.ProjectLayout.developRoot() + " ("
                        + de.devtoolbox.core.ProjectLayout.projectPrefix() + "haftpflichtvertrag, "
                        + de.devtoolbox.core.ProjectLayout.projectPrefix() + "sachvertrag)"));
        params.add(new Param(REFERENCE_PROJECT, "Referenzprojekt", Param.Type.CHOICE, "", false,
                "Bereits umgesetzte Migration, deren Code für die Spezifikation durchsucht wird", List.copyOf(references)));
        params.add(Param.directory(REFERENCE_PATH, "Referenz als Pfad", false,
                "Nur wenn oben kein Referenzprojekt gewählt ist: Code der Referenz direkt angeben – Ordner oder "
                        + "Bitbucket-Adresse (Link aus dem Browser oder Klon-Adresse HTTPS/SSH)"));
        params.add(Param.directory("ddl", "Struktur der Altdaten (DDL)", false,
                "Ordner oder Datei mit .ddl/.sql; Standard: Ordner ddl im Projekt (Import über „DDL importieren…“)"));
        params.add(Param.directory("zielmodell", "Zielmodell (JSON je Entität)", false,
                "Ordner mit einer JSON-Datei je Entität – Pflicht für die Phase Process"));
        params.add(Param.directory("mappingvorlagen", "Mapping-Vorlagen", false,
                "Ordner mit einer Vorlage der Mapping-Dokumentation je Entität (Confluence-Tabelle) – für den Job Mapping-Dokumentation"));
        params.add(Param.text("beschreibung", "Beschreibung", "", false, "Optional"));
        return params;
    }

    private static Map<String, String> initialValues(MigrationProject p, List<MigrationProject> others) {
        Map<String, String> v = new HashMap<>();
        if (p == null) {
            for (String key : List.of(ID, "name", "produkt", "sparte", "root", REFERENCE_PATH, "ddl", "zielmodell", "mappingvorlagen", "beschreibung")) {
                v.put(key, "");
            }
            v.put(REFERENCE_PROJECT, "");
            return v;
        }
        v.put("name", p.name());
        v.put("produkt", p.produkt());
        v.put("sparte", p.sparte());
        v.put("root", p.root() == null ? p.rootUrl() : p.root().toString());
        String referenceId = others.stream().filter(o -> o.label().equals(p.referenceLabel())).map(MigrationProject::id)
                .findFirst().orElse("");
        v.put(REFERENCE_PROJECT, referenceId);
        v.put(REFERENCE_PATH, !referenceId.isEmpty() ? ""
                : p.reference() != null ? p.reference().toString() : p.referenceUrl());
        v.put("ddl", p.ddl() == null ? "" : p.ddl().toString());
        v.put("zielmodell", p.targetModel() == null ? "" : p.targetModel().toString());
        v.put("mappingvorlagen", p.mappingTemplates() == null ? "" : p.mappingTemplates().toString());
        v.put("beschreibung", p.description());
        return v;
    }

    private static Path pathOrDerived(String value, Path derived) {
        Path p = pathOrNull(value);
        return p != null ? p : derived;
    }

    /**
     * Code of the reference as currently entered: chosen project's code, else the path.
     */
    private static Path referencePath(ParamForm form, List<MigrationProject> others) {
        String id = form.value(REFERENCE_PROJECT);
        if (id != null && !id.isBlank()) {
            // the reference project may have no code folder yet (root null) – findFirst() must not see a null
            return others.stream().filter(o -> o.id().equals(id)).findFirst().map(MigrationProject::rootCode)
                    .orElse(null);
        }
        return pathOrNull(form.value(REFERENCE_PATH));
    }

    /**
     * What is stored in "entitaeten": the typed names without those the derived list already contains.
     */
    private static String extrasToStore(EntitySuggestions.Effective derived, String typed) {
        List<String> names = MigrationProject.parseEntities(typed);
        return String.join("\n", derived == null ? names : derived.withoutAutomatic(names));
    }

    /**
     * Dialog with the names of the cross-project pool that the project does not have yet; appends the chosen ones.
     */
    private static void adoptShared(Component parent, List<String> shared, EntitySuggestions.Effective derived,
                                    JTextArea extras) {
        List<String> have = new ArrayList<>(MigrationProject.parseEntities(extras.getText()));
        if (derived != null) {
            derived.automatic().forEach(i -> have.add(i.name()));
        }
        List<String> offer = shared.stream().filter(n -> have.stream().noneMatch(h -> h.equalsIgnoreCase(n))).toList();
        if (offer.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "Alle Namen aus dem Namensvorrat sind schon in der Liste.");
            return;
        }
        JList<String> list = new JList<>(offer.toArray(String[]::new));
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(280, 260));
        if (JOptionPane.showConfirmDialog(parent, scroll, "Aus dem Namensvorrat ergänzen",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        List<String> merged = new ArrayList<>(MigrationProject.parseEntities(extras.getText()));
        merged.addAll(list.getSelectedValuesList());
        extras.setText(String.join("\n", merged));
    }

    private static String save(Path projectsDir, MigrationProject project, Map<String, String> values,
                               String entities) throws Exception {
        String id = project != null ? project.id() : values.getOrDefault(ID, "").strip().toLowerCase();
        if (id.isEmpty() || !id.matches("[a-z0-9][a-z0-9_-]*")) {
            throw new IllegalArgumentException("Id fehlt oder ungültig (Kleinbuchstaben, Ziffern, - und _).");
        }
        if (values.getOrDefault("name", "").isBlank()) {
            throw new IllegalArgumentException("Kurzname fehlt.");
        }
        Path folder = project != null ? project.folder() : projectsDir.resolve(id);
        Path file = folder.resolve("projekt.json");
        if (project == null && Files.exists(folder)) {
            throw new IllegalArgumentException("Ein Migrationsprojekt mit der Id '" + id + "' gibt es schon.");
        }
        Map<String, Object> json = new LinkedHashMap<>();
        Map<String, String> old = Files.exists(file) ? Json.parse(Files.readString(file)) : Map.of();
        json.putAll(old); // keep entries this form does not know
        json.put("name", values.get("name").strip());
        json.put("sparte", values.getOrDefault("sparte", "").strip());
        json.put("produkt", values.getOrDefault("produkt", "").strip());
        // paths equal to the derived ones (ProjectLayout) are not stored, so they follow the layout
        String sparte = values.getOrDefault("sparte", "").strip();
        String code = values.getOrDefault("root", "").strip();
        json.put("root", de.devtoolbox.core.CodeSource.isRemote(code) ? code
                : unlessDerived(code, de.devtoolbox.core.ProjectLayout.root(sparte, id))
                ? keep(folder, old.get("root"), relative(folder, code)) : "");
        String reference = values.getOrDefault(REFERENCE_PROJECT, "");
        String referencePath = values.getOrDefault(REFERENCE_PATH, "").strip();
        json.put("referenz", !reference.isBlank() ? reference
                : de.devtoolbox.core.CodeSource.isRemote(referencePath) ? referencePath
                : keep(folder, old.get("referenz"), relative(folder, referencePath)));
        String ddl = keep(folder, old.get("ddl"), relative(folder, values.get("ddl")));
        json.put("ddl", ddl.isEmpty() && project == null ? "ddl" : ddl);
        // a path equal to the one derived from root is not stored, so it follows root
        Path designer = de.devtoolbox.core.ProjectLayout.designer(pathOrNull(values.get("root")), sparte);
        json.put("zielmodell", unlessDerived(values.get("zielmodell"), de.devtoolbox.core.ProjectLayout.targetModelIn(designer))
                ? keep(folder, old.get("zielmodell"), relative(folder, values.get("zielmodell"))) : "");
        json.put("mappingvorlagen", unlessDerived(values.get("mappingvorlagen"), de.devtoolbox.core.ProjectLayout.mappingTemplatesIn(designer))
                ? keep(folder, old.get("mappingvorlagen"), relative(folder, values.get("mappingvorlagen"))) : "");
        json.put("beschreibung", values.getOrDefault("beschreibung", "").strip());
        json.put(MigrationProject.ENTITIES_KEY, String.join(", ", MigrationProject.parseEntities(entities)));

        Files.createDirectories(folder);
        if ("ddl".equals(json.get("ddl"))) {
            Files.createDirectories(folder.resolve("ddl"));
        }
        Files.writeString(file, Json.write(json), StandardCharsets.UTF_8);
        return id;
    }

    /**
     * Keeps the previous spelling (e.g. "../../testprojekt") if it still points to the same place.
     */
    private static Path pathOrNull(String value) {
        if (de.devtoolbox.core.CodeSource.isRemote(value)) {
            // a Bitbucket address: the clone of an earlier run, without network access while typing
            return de.devtoolbox.core.CodeSource.cached(value);
        }
        try {
            return value == null || value.isBlank() ? null : Path.of(value.strip());
        } catch (java.nio.file.InvalidPathException e) {
            return null;
        }
    }

    private static String string(Path path) {
        return path == null ? "" : path.toString();
    }

    /**
     * True if {@code value} is set and differs from the derived path (then it is an explicit setting).
     */
    private static boolean unlessDerived(String value, Path derived) {
        if (value == null || value.isBlank()) {
            return false;
        }
        Path given = pathOrNull(value);
        return derived == null || given == null
                || !given.toAbsolutePath().normalize().equals(derived.toAbsolutePath().normalize());
    }

    private static String keep(Path folder, String previous, String now) {
        if (previous == null || previous.isBlank() || now.isEmpty() || de.devtoolbox.core.CodeSource.isRemote(previous)) {
            return now;
        }
        Path a = folder.resolve(previous.strip()).toAbsolutePath().normalize();
        Path b = folder.resolve(now).toAbsolutePath().normalize();
        return a.equals(b) ? previous.strip() : now;
    }

    /**
     * Paths inside the project folder are stored relative (e.g. "ddl"), others absolute with forward slashes.
     */
    static String relative(Path folder, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        Path path = folder.resolve(value.strip()).toAbsolutePath().normalize();
        Path base = folder.toAbsolutePath().normalize();
        return (path.startsWith(base) ? base.relativize(path).toString() : path.toString()).replace('\\', '/');
    }

    /**
     * Outcome of the dialog: the saved project's id, or {@code deleted}; {@code null} result = cancelled.
     */
    record Result(String id, boolean deleted) {
    }
}
