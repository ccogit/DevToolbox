package de.devtoolbox.ui;

import de.devtoolbox.core.Config;
import de.devtoolbox.core.Job;
import de.devtoolbox.core.Json;
import de.devtoolbox.core.Keys;
import de.devtoolbox.core.MigrationProject;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

/**
 * The single screen: migration project and job selection, the job's configuration (incl. the params of the tools it uses),
 * the run log, and loading/saving the configuration as JSON.
 */
public final class MainWindow {

    private static final Preferences PREFS = Preferences.userNodeForPackage(MainWindow.class);

    private final JFrame frame = new JFrame("DevToolbox");
    /** Selections of project and job (shown in the selection card). */
    private final JComboBox<Job> jobBox;
    private final JComboBox<MigrationProject> projectBox;
    private final InfoButton projectInfo = new InfoButton("Migrationsprojekt");
    private boolean reloadingProjects;
    private final InfoButton description = new InfoButton("Job");
    private final JPanel formHost = new WidthTrackingPanel();
    private final JButton runButton = new JButton("▶  Ausführen");
    private final JButton folderButton = new JButton("Ordner öffnen");
    private Console console;
    private final List<Job> jobs;
    /** "Projekt: … | Job: …" in the status bar. */
    private final JLabel statusContext = new JLabel();
    private Job job;
    private ParamForm form;
    private Path lastFolder;
    /** The pages of the main area (CardLayout) and the sidebar items that open them. */
    private final CardLayout cards = new CardLayout();
    private final JPanel pages = new JPanel(cards);
    private final Map<String, JComponent> pageComponents = new HashMap<>();
    private final Map<String, NavItem> navItems = new HashMap<>();
    private static final String PAGE_RUN = "run";
    private static final String PAGE_PROJECT = "project";
    private static final String PAGE_STANDARDS = "standards";
    private static final String PAGE_HELP = "help";

    public static void show(List<Job> jobs) {
        SwingUtilities.invokeLater(() -> {
            UiTheme.install();
            MainWindow window = new MainWindow(jobs);
            window.frame.setVisible(true);
            // hidden option for screenshots: -Ddevtoolbox.page=ausfuehren|projekt|standards|hilfe
            switch (System.getProperty("devtoolbox.page", "")) {
                case "projekt" -> window.editProject(window.project());
                case "standards" -> window.showSharedStandards();
                case "hilfe" -> window.showHelp();
                default -> { }
            }
        });
    }

    /** Stands for "no project": paths are entered by hand. */
    private static final MigrationProject NO_PROJECT =
            new MigrationProject("", "– kein Migrationsprojekt (Pfade manuell) –", "", "", null, null, "", null, null, null,
                    "", null);

    private MainWindow(List<Job> jobs) {
        this.jobs = jobs;
        List<MigrationProject> projects = new java.util.ArrayList<>(List.of(NO_PROJECT));
        projects.addAll(MigrationProject.all());
        projectBox = new JComboBox<>(projects.toArray(MigrationProject[]::new));
        String lastProject = PREFS.get(Keys.PROJECT, "");
        projects.stream().filter(p -> p.id().equals(lastProject)).findFirst().ifPresent(projectBox::setSelectedItem);
        projectBox.addActionListener(e -> {
            if (!reloadingProjects) {
                projectChanged();
            }
        });
        jobBox = new JComboBox<>(jobs.toArray(Job[]::new));
        jobBox.addActionListener(e -> select((Job) jobBox.getSelectedItem(), null));

        runButton.addActionListener(e -> run());
        // filled with the accent colour
        runButton.putClientProperty("FlatLaf.style", "background: " + hex(UiTheme.ACCENT_DARK)
                + "; foreground: #ffffff; hoverBackground: " + hex(UiTheme.ACCENT) + "; pressedBackground: "
                + hex(UiTheme.ACCENT_DARK.darker()) + "; borderWidth: 0; focusWidth: 0; arc: 12");
        runButton.setFont(UiTheme.text(Font.BOLD, 14f));
        runButton.setPreferredSize(new Dimension(150, 40));
        folderButton.addActionListener(e -> open(lastFolder));
        folderButton.setEnabled(false);

        folderButton.putClientProperty("JButton.buttonType", "borderless");
        folderButton.setFont(UiTheme.text(Font.PLAIN, 12f));
        frame.setJMenuBar(titleBar());
        frame.getRootPane().putClientProperty("JRootPane.titleBarShowTitle", false);
        frame.getRootPane().putClientProperty("JRootPane.titleBarShowIcon", false);
        frame.getRootPane().putClientProperty("JRootPane.titleBarBackground", UiTheme.HEADER);
        frame.setContentPane(layoutB());
        frame.setIconImage(logoImage());
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        // Strg+Enter runs the job (Enter alone stays with the text fields)
        frame.getRootPane().registerKeyboardAction(e -> {
                    if (runButton.isEnabled()) {
                        run();
                    }
                }, KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, java.awt.event.InputEvent.CTRL_DOWN_MASK),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        frame.setSize(Math.min(1360, screen.width), Math.min(1000, screen.height));
        frame.setMinimumSize(new Dimension(980, 640));
        frame.setLocationRelativeTo(null);

        String lastJob = PREFS.get("job", "");
        Job initial = jobs.stream().filter(j -> j.id().equals(lastJob)).findFirst().orElse(jobs.getFirst());
        jobBox.setSelectedItem(initial);
        if (form == null) {
            select(initial, null); // no change event if the item was already selected
        }
        updateProjectInfo();
        append("Bereit für die Jobausführung – „Ausführen“ (Strg+Enter).");
    }

    // ---- layout ----

    private static String hex(Color c) {
        return String.format("#%06x", c.getRGB() & 0xFFFFFF);
    }

    private static Image logoImage() {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        JComponent logo = Ui.logo(64);
        logo.setSize(64, 64);
        logo.paint(g);
        g.dispose();
        return image;
    }

    /** Logo and name in the title bar (a menu bar FlatLaf embeds there), vertically centred. */
    private static JMenuBar titleBar() {
        JMenuBar bar = new JMenuBar();
        bar.setOpaque(false);
        bar.setBorder(new javax.swing.border.EmptyBorder(6, 12, 6, 12));
        JPanel brand = Ui.transparent(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.CENTER;
        brand.add(Ui.logo(26), c);
        JLabel title = new JLabel("<html><b>DEV</b><b><font color='" + hex(UiTheme.ACCENT) + "'>TOOLBOX</font></b></html>");
        title.setFont(UiTheme.text(Font.BOLD, 15f));
        title.setForeground(UiTheme.TEXT);
        c.insets = new Insets(0, 10, 0, 0);
        brand.add(title, c);
        brand.setMaximumSize(brand.getPreferredSize());
        bar.add(brand);
        bar.add(Box.createHorizontalGlue());
        return bar;
    }

    /** Sidebar; header with "Job starten"; project and job; the job's configuration; log; status bar. */
    private JComponent layoutB() {
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        console = new Console(folderButton, () -> split.setDividerLocation(
                split.getDividerLocation() > split.getHeight() / 2 ? 0.35 : 0.72));
        JPanel consoleBox = Ui.transparent(new BorderLayout());
        consoleBox.setBorder(new javax.swing.border.EmptyBorder(0, 24, 10, 24));
        consoleBox.add(console);
        consoleBox.setMinimumSize(new Dimension(10, 110));

        JPanel top = Ui.transparent(new BorderLayout(0, 16));
        top.setBorder(new javax.swing.border.EmptyBorder(20, 24, 0, 24));
        JPanel title = Ui.transparent(new BorderLayout(14, 0));
        JLabel play = (JLabel) Ui.iconTile("▶", UiTheme.TEXT, 44);
        title.add(play, BorderLayout.WEST);
        JPanel titleText = Ui.transparent(new GridLayout(2, 1, 0, 2));
        titleText.add(Ui.strong("Job ausführen", 22f));
        titleText.add(Ui.muted("Projekt, Job und Phasen konfigurieren und ausführen", 13.5f, false));
        title.add(titleText, BorderLayout.CENTER);
        runButton.setText("▶  Job starten");
        runButton.setFont(UiTheme.text(Font.BOLD, 15f));
        runButton.setPreferredSize(new Dimension(190, 46));
        JPanel runBox = Ui.transparent(new GridBagLayout());
        runBox.add(runButton);
        title.add(runBox, BorderLayout.EAST);
        top.add(title, BorderLayout.NORTH);
        top.add(selectionCard(), BorderLayout.CENTER);

        JPanel column = new WidthTrackingPanel();
        column.setBackground(UiTheme.BACKGROUND);
        column.setBorder(new javax.swing.border.EmptyBorder(16, 24, 0, 24));
        formHost.setOpaque(false);
        column.add(formHost, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(column);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(UiTheme.BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setPreferredSize(new Dimension(10, 470)); // so the split leaves the log a reasonable height

        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(UiTheme.BACKGROUND);
        main.add(top, BorderLayout.NORTH);
        main.add(scroll, BorderLayout.CENTER);
        pages.setBackground(UiTheme.BACKGROUND);
        pages.add(main, PAGE_RUN);
        split.setTopComponent(pages);
        split.setBottomComponent(consoleBox);
        split.setBorder(null);
        split.setResizeWeight(1);
        split.setContinuousLayout(true);
        // start with a log of reasonable height directly below the content
        split.addComponentListener(new java.awt.event.ComponentAdapter() {
            private boolean placed;

            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                if (!placed && split.getHeight() > 300) {
                    placed = true;
                    split.setDividerLocation(split.getHeight() - 225);
                }
            }
        });
        split.setDividerSize(4);

        JPanel status = new JPanel(new BorderLayout());
        status.setBackground(UiTheme.HEADER);
        status.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UiTheme.BORDER),
                new javax.swing.border.EmptyBorder(9, 20, 9, 20)));
        JLabel state = console.stateLabel();
        state.setFont(UiTheme.text(Font.PLAIN, 13f));
        status.add(state, BorderLayout.WEST);
        statusContext.setFont(UiTheme.text(Font.PLAIN, 13f));
        statusContext.setForeground(UiTheme.TEXT);
        status.add(statusContext, BorderLayout.EAST);

        JPanel right = new JPanel(new BorderLayout());
        right.add(split, BorderLayout.CENTER);
        right.add(status, BorderLayout.SOUTH);
        JPanel root = new JPanel(new BorderLayout());
        root.add(sidebar(), BorderLayout.WEST);
        root.add(right, BorderLayout.CENTER);
        return root;
    }

    /** Project and job drop-downs with the project actions. */
    private JComponent selectionCard() {
        projectBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int i, boolean sel, boolean focus) {
                String text = value == null ? "" : value == NO_PROJECT ? NO_PROJECT.name() : ((MigrationProject) value).label();
                return super.getListCellRendererComponent(l, text, i, sel, focus);
            }
        });
        jobBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int i, boolean sel, boolean focus) {
                return super.getListCellRendererComponent(l, value == null ? "" : ((Job) value).name(), i, sel, focus);
            }
        });
        Ui.Card card = new Ui.Card(new GridBagLayout(), false);
        card.setBorder(new javax.swing.border.EmptyBorder(12, 18, 14, 18));
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridy = 0;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 6, 24);
        card.add(Ui.strong("▭  Projekt", 14f), c);
        c.insets = new Insets(0, 0, 6, 16);
        card.add(Ui.strong("◈  Job", 14f), c);
        c.gridy = 1;
        c.insets = new Insets(0, 0, 0, 24);
        projectBox.setPreferredSize(new Dimension(10, 38));
        card.add(projectBox, c);
        c.insets = new Insets(0, 0, 0, 16);
        jobBox.setPreferredSize(new Dimension(10, 38));
        card.add(jobBox, c);
        c.weightx = 0;
        c.insets = new Insets(0, 0, 0, 0);
        JPanel actions = Ui.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.add(squareButton("+", "Neues Migrationsprojekt", () -> editProject(null)));
        actions.add(squareButton("✎", "Migrationsprojekt bearbeiten", () -> editProject(project())));
        card.add(actions, c);
        return card;
    }

    private static JButton squareButton(String glyph, String tip, Runnable action) {
        JButton b = new JButton(glyph);
        b.setToolTipText(tip);
        b.setFont(UiTheme.text(Font.PLAIN, 16f));
        b.setPreferredSize(new Dimension(40, 38));
        b.setFocusable(false);
        b.addActionListener(e -> action.run());
        return b;
    }

    /** Navigation: actions; project info and version at the bottom. */
    private JComponent sidebar() {
        JPanel side = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setPaint(new GradientPaint(0, 0, UiTheme.HEADER, 0, getHeight(), UiTheme.CARD));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(UiTheme.BORDER);
                g2.drawLine(getWidth() - 1, 0, getWidth() - 1, getHeight());
                g2.dispose();
            }
        };
        side.setPreferredSize(new Dimension(250, 10));
        side.setBorder(new javax.swing.border.EmptyBorder(20, 16, 18, 16));

        JPanel nav = Ui.transparent(new GridLayout(0, 1, 0, 6));
        nav.setBorder(new javax.swing.border.EmptyBorder(0, 0, 0, 0));
        nav.add(navItem(PAGE_RUN, "▶", "Ausführen", () -> showPage(PAGE_RUN)));
        nav.add(navItem(PAGE_PROJECT, "▤", "Projekt", () -> editProject(project())));
        nav.add(navItem(PAGE_STANDARDS, "≡", "Standards", this::showSharedStandards));
        nav.add(navItem(PAGE_HELP, "?", "Hilfe", this::showHelp));
        nav.add(navItem(null, "⤓", "Speichern", this::saveJson));
        nav.add(navItem(null, "⤒", "Laden", this::loadJson));
        showPage(PAGE_RUN);
        JPanel top = Ui.transparent(new BorderLayout());
        top.add(nav, BorderLayout.CENTER);
        side.add(top, BorderLayout.NORTH);

        JPanel bottom = Ui.transparent(new FlowLayout(FlowLayout.LEFT, 6, 0));
        bottom.add(projectInfo);
        String version = MainWindow.class.getPackage().getImplementationVersion();
        bottom.add(Ui.muted("v" + (version == null ? "1.0.0" : version), 13f, false));
        side.add(bottom, BorderLayout.SOUTH);
        return side;
    }

    /** Item with a page ({@code page != null}, highlighted while shown) or a plain action. */
    private JComponent navItem(String page, String glyph, String text, Runnable action) {
        NavItem item = new NavItem(glyph, text);
        item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        item.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                action.run();
            }
        });
        if (page != null) {
            navItems.put(page, item);
        }
        return item;
    }

    private static final class NavItem extends JLabel {
        private boolean active;

        NavItem(String glyph, String text) {
            super("<html><span style='font-size:13px'>" + glyph + "</span>&nbsp;&nbsp;&nbsp;" + text + "</html>");
            setBorder(new javax.swing.border.EmptyBorder(10, 16, 10, 16));
            setActive(false);
        }

        void setActive(boolean active) {
            this.active = active;
            setFont(UiTheme.text(active ? Font.BOLD : Font.PLAIN, 14.5f));
            setForeground(active ? UiTheme.ACCENT : UiTheme.TEXT);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (active) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(UiTheme.ACCENT_TINT);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }

    // ---- pages ----

    /** Shows the page and highlights its sidebar item ({@code highlight}: the page whose item is marked). */
    private void showPage(String name) {
        showPage(name, name);
    }

    private void showPage(String name, String highlight) {
        cards.show(pages, name);
        navItems.forEach((key, item) -> item.setActive(key.equals(highlight)));
    }

    /** Replaces the page {@code name} by {@code content} (scrollable, as wide as the window) and shows it. */
    private void setPage(String name, JComponent content, String highlight) {
        JComponent old = pageComponents.remove(name);
        if (old != null) {
            pages.remove(old);
        }
        JPanel column = new WidthTrackingPanel();
        column.setBackground(UiTheme.BACKGROUND);
        column.add(content, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(column);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(UiTheme.BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        pageComponents.put(name, scroll);
        pages.add(scroll, name);
        showPage(name, highlight);
        pages.revalidate();
    }

    private void showHelp() {
        JPanel help = Ui.transparent(new BorderLayout(0, 16));
        help.setBorder(new javax.swing.border.EmptyBorder(20, 24, 16, 24));
        help.add(Ui.pageHeader("?", "Hilfe", job.name(), null), BorderLayout.NORTH);
        JPanel cards = Ui.transparent(new GridLayout(0, 1, 0, 14));
        de.devtoolbox.core.JobInfo info = job.info();
        String[][] parts = {{"◎", "Ziel", "Wobei der Job dich unterstützt.", info.goal()},
                {"⇥", "Primäre Inputs", "Was du angibst.", info.inputs()},
                {"☑", "Voraussetzungen", "Was vorher vorliegen muss.", info.prerequisites()},
                {"✓", "Ergebnis", "Was nach der Ausführung vorliegt.", info.output()}};
        for (String[] p : parts) {
            cards.add(Ui.card(p[0], p[1], p[2], null, helpText(p[3])));
        }
        JPanel wrap = Ui.transparent(new BorderLayout());
        wrap.add(cards, BorderLayout.NORTH);
        help.add(wrap, BorderLayout.CENTER);
        setPage(PAGE_HELP, help, PAGE_HELP);
    }

    private static JComponent helpText(String html) {
        JTextPane text = new JTextPane();
        text.setContentType("text/html");
        text.setText("<html><body style='font-family:Inter,sans-serif;font-size:13pt;color:" + hex(UiTheme.TEXT) + "'>"
                + html + "</body></html>");
        text.setEditable(false);
        text.setFocusable(false);
        text.setOpaque(false);
        text.setBorder(null);
        return text;
    }

    // ---- behaviour ----

    /** Builds the form for {@code newJob}; {@code values} (e.g. from a JSON file) win over the remembered ones. */
    private void select(Job newJob, Map<String, String> values) {
        if (newJob == null) {
            return;
        }
        job = newJob;
        form = new ParamForm(job.id(), job.params(), false);
        // code of the migration entered by hand: target model and mapping templates from its designer-services module
        form.deriveFrom(Keys.TARGET, Map.of(
                Keys.TARGET_MODEL, t -> derived(t, de.devtoolbox.core.ProjectLayout::targetModel),
                Keys.MAPPING_TEMPLATES, t -> derived(t, de.devtoolbox.core.ProjectLayout::mappingTemplates)));
        form.setValues(project().values()); // the project's paths win over remembered ones …
        if (values != null) {               // … and a loaded file wins over both
            form.setValues(values);
        }
        // the business entity is chosen from the target model's entities
        form.onChange(Keys.TARGET_MODEL, v -> loadEntityChoices());
        loadEntityChoices();
        description.setInfo(job.name(), job.info().goal(), false);
        description.setForeground(UiTheme.MUTED);
        formHost.removeAll();
        formHost.add(new JobView(form, job.info()), BorderLayout.CENTER);
        updateStatusContext();
        formHost.revalidate();
        formHost.repaint();
        PREFS.put("job", job.id());
    }

    /** Entities of the target model in the form's folder as choices of the entity drop-down. */
    private void loadEntityChoices() {
        // the project's entity list (maintained in the project editor), else the target model in the form
        List<String> names = project() == NO_PROJECT ? List.of() : project().entityChoices();
        String folder = form.value(Keys.TARGET_MODEL).strip();
        try {
            if (names.isEmpty() && !folder.isEmpty()) {
                names = de.devtoolbox.tools.codecollector.TargetModel.entityNames(Path.of(folder));
            }
        } catch (Exception e) {
            // no or unreadable target model: free text only
        }
        form.setEntityChoices(names);
    }

    /** "Projekt: … | Job: …" in the status bar. */
    private void updateStatusContext() {
        if (job == null) {
            return;
        }
        MigrationProject p = project();
        statusContext.setText("Projekt: " + (p == NO_PROJECT ? "–" : p.name()) + "   |   Job: "
                + job.name().replaceFirst("\\s*\\(.*\\)\\s*$", ""));
    }

    private static String derived(String root, java.util.function.Function<Path, Path> derive) {
        try {
            Path p = root == null || root.isBlank() ? null : derive.apply(Path.of(root.strip()));
            return p == null ? "" : p.toString();
        } catch (java.nio.file.InvalidPathException e) {
            return "";
        }
    }

    private MigrationProject project() {
        return (MigrationProject) projectBox.getSelectedItem();
    }

    private void projectChanged() {
        PREFS.put(Keys.PROJECT, project().id());
        if (form != null) {
            form.setValues(project().values());
            loadEntityChoices();
        }
        updateProjectInfo();
        updateStatusContext();
    }

    private void updateProjectInfo() {
        MigrationProject p = project();
        if (p == NO_PROJECT) {
            projectInfo.setInfo("Kein Migrationsprojekt", MigrationProject.directory() == null
                    ? "Keine Migrationsprojekte gefunden (Ordner 'migrationsprojekte')."
                    : "Pfade werden in der Job-Konfiguration manuell angegeben.", false);
            return;
        }
        List<String> parts = new java.util.ArrayList<>();
        if (!p.sparte().isEmpty()) {
            parts.add(p.sparte());
        }
        parts.add(schemaSummary(p));
        parts.add(targetModelSummary(p));
        parts.add(mappingTemplatesSummary(p));
        parts.add(standardsSummary(p));
        if (p.reference() == null && !p.referenceLabel().isEmpty()) {
            parts.add("<b>Referenzprojekt „" + p.referenceLabel() + "“ nicht gefunden</b> (Bearbeiten…)");
        } else if (p.reference() == null) {
            parts.add("<b>Referenzprojekt nicht konfiguriert</b> (Bearbeiten…)");
        } else if (!Files.isDirectory(p.reference())) {
            parts.add("<b>Referenzprojekt fehlt:</b> " + p.reference());
        } else {
            parts.add("Referenz: " + (p.referenceLabel().equals(p.reference().toString())
                    ? p.reference() : p.referenceLabel() + " · " + p.reference()));
        }
        if (!p.description().isEmpty()) {
            parts.add("<i>" + p.description() + "</i>");
        }
        // bold parts are problems (missing schema, reference …) – they turn the symbol into a warning
        projectInfo.setInfo(p.label(), String.join("<br>", parts), parts.stream().anyMatch(t -> t.contains("<b>")));
    }

    /** e.g. "Standards: übergreifend, 8 Vorlagen (Vertrag)" or "Standards: projekteigen, …". */
    private static String standardsSummary(MigrationProject p) {
        Path dir = p.standardsDirectory();
        if (dir == null || !Files.exists(dir.resolve(de.devtoolbox.tools.standards.StandardsTool.INDEX))) {
            return "keine Standards (Standards… in der Seitenleiste)";
        }
        try {
            de.devtoolbox.tools.standards.StandardsTool.Stored s = de.devtoolbox.tools.standards.StandardsTool.load(dir);
            return "Standards: " + (p.hasOwnStandards() ? "projekteigen" : "übergreifend") + ", "
                    + s.rows().size() + " Vorlagen (" + s.example() + ")";
        } catch (Exception e) {
            return "<b>Standards nicht lesbar</b>";
        }
    }

    /** e.g. "Zielmodell: 12 Entitäten" – JSON files in the project's target model folder. */
    private static String targetModelSummary(MigrationProject p) {
        if (p.targetModel() == null) {
            return "kein Zielmodell (Bearbeiten…)";
        }
        try {
            int n = de.devtoolbox.tools.codecollector.TargetModel.entityCount(p.targetModel());
            return n == 0 ? "<b>Zielmodell leer/fehlt:</b> " + p.targetModel() : "Zielmodell: " + n + " Entitäten";
        } catch (Exception e) {
            return "<b>Zielmodell nicht lesbar</b>";
        }
    }

    /** e.g. "Mapping-Vorlagen: 12" – templates of the mapping documentation. */
    private static String mappingTemplatesSummary(MigrationProject p) {
        if (p.mappingTemplates() == null) {
            return "keine Mapping-Vorlagen (Bearbeiten…)";
        }
        try {
            int n = de.devtoolbox.tools.mappingdoc.MappingTemplate.all(p.mappingTemplates()).size();
            return n == 0 ? "<b>Mapping-Vorlagen leer/fehlen:</b> " + p.mappingTemplates() : "Mapping-Vorlagen: " + n;
        } catch (Exception e) {
            return "<b>Mapping-Vorlagen nicht lesbar</b>";
        }
    }

    /** e.g. "Schema: 4 Tabellen, 21 Spalten" – read from the project's DDL. */
    private static String schemaSummary(MigrationProject p) {
        if (p.ddl() == null || !Files.exists(p.ddl())) {
            return "<b>kein Schema</b> (Schema importieren…)";
        }
        try {
            List<de.devtoolbox.tools.codecollector.DdlReader.Table> tables =
                    de.devtoolbox.tools.codecollector.DdlReader.read(p.ddl());
            if (tables.isEmpty()) {
                return "<b>kein Schema</b> (Schema importieren…)";
            }
            int columns = tables.stream().mapToInt(t -> t.columns().size()).sum();
            return "Schema: " + tables.size() + " Tabellen, " + columns + " Spalten";
        } catch (Exception e) {
            return "<b>Schema nicht lesbar:</b> " + e.getMessage();
        }
    }

    /**
     * Copies DDL files into the schema folder of the project – the folder next to its projekt.json – after checking
     * that they contain table definitions.
     *
     * @return true if something was imported (the projects are reloaded then)
     */
    private boolean importSchema(MigrationProject p) {
        if (p == null) {
            return false;
        }
        JFileChooser chooser = new JFileChooser(PREFS.get("schemaDir", ""));
        chooser.setDialogTitle("Schema (DDL) für " + p.label() + " importieren");
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter("DDL / SQL (*.sql, *.ddl)", "sql", "ddl"));
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return false;
        }
        Path target = p.schemaDirectory();
        try {
            Files.createDirectories(target);
            int imported = 0;
            for (java.io.File selected : chooser.getSelectedFiles()) {
                Path source = selected.toPath();
                PREFS.put("schemaDir", source.getParent().toString());
                List<de.devtoolbox.tools.codecollector.DdlReader.Table> tables =
                        de.devtoolbox.tools.codecollector.DdlReader.read(source);
                if (tables.isEmpty() && JOptionPane.showConfirmDialog(frame, source.getFileName()
                                + " enthält kein CREATE TABLE / CREATE VIEW. Trotzdem übernehmen?", "Schema importieren",
                        JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
                    continue;
                }
                Path dest = target.resolve(source.getFileName());
                if (Files.exists(dest) && !Files.isSameFile(source, dest) && JOptionPane.showConfirmDialog(frame,
                        dest.getFileName() + " ist im Schema bereits vorhanden. Ersetzen?", "Schema importieren",
                        JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
                    continue;
                }
                if (!Files.exists(dest) || !Files.isSameFile(source, dest)) {
                    Files.copy(source, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                int columns = tables.stream().mapToInt(t -> t.columns().size()).sum();
                append("Schema importiert: " + source.getFileName() + " → " + dest + " (" + tables.size()
                        + " Tabellen, " + columns + " Spalten: "
                        + tables.stream().map(de.devtoolbox.tools.codecollector.DdlReader.Table::name)
                        .collect(java.util.stream.Collectors.joining(", ")) + ")");
                imported++;
            }
            if (imported > 0) {
                p.ensureDdlEntry(target);
                reloadProjects(p.id());
            }
            return imported > 0;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Import fehlgeschlagen:\n" + ex.getMessage());
            return false;
        }
    }

    /** The cross-project standards as a page. */
    private void showSharedStandards() {
        Path dir = MigrationProject.sharedStandardsDirectory();
        if (dir == null) {
            JOptionPane.showMessageDialog(frame, "Es gibt keinen Ordner für Migrationsprojekte.");
            return;
        }
        standardsPage(dir, "übergreifend", PAGE_RUN, () -> ProjectEditor.generateStandards(frame, null, dir,
                "Übergreifende Standards"));
    }

    /** Shows the standards of {@code dir}; "Zurück" leads to the page {@code back}. */
    private void standardsPage(Path dir, String title, String back, Runnable create) {
        setPage(PAGE_STANDARDS, StandardsDialog.panel(dir, title, () -> {
            MigrationProject p = project();
            reloadProjects(p == NO_PROJECT ? "" : p.id());
            showPage(back);
        }, create), back.equals(PAGE_RUN) ? PAGE_STANDARDS : back);
    }

    /** Shows the project page; {@code null} creates a new project. Reloads and selects the saved project. */
    private void editProject(MigrationProject p) {
        if (MigrationProject.directory() == null) {
            JOptionPane.showMessageDialog(frame, "Kein Ordner 'migrationsprojekte' gefunden.");
            return;
        }
        MigrationProject edited = p == NO_PROJECT ? null : p;
        setPage(PAGE_PROJECT, ProjectEditor.panel(edited, result -> {
            if (result == null) {
                reloadProjects(edited == null ? "" : edited.id());
            } else if (result.deleted()) {
                reloadProjects("");
                append("Migrationsprojekt gelöscht: " + edited.folder());
            } else {
                reloadProjects(result.id());
                append("Migrationsprojekt gespeichert: " + project().folder().resolve("projekt.json"));
            }
            showPage(PAGE_RUN);
        }, () -> editProject(null), () -> {
            if (importSchema(edited)) {
                editProject(project()); // show the new DDL entry
            }
        }, (dir, title) -> standardsPage(dir, title, PAGE_PROJECT, null)), PAGE_PROJECT);
    }

    /** Re-reads the project definitions (e.g. after an import) and keeps the selection. */
    private void reloadProjects(String selectId) {
        reloadingProjects = true;
        try {
            projectBox.removeAllItems();
            projectBox.addItem(NO_PROJECT);
            MigrationProject.all().forEach(projectBox::addItem);
            for (int i = 0; i < projectBox.getItemCount(); i++) {
                if (projectBox.getItemAt(i).id().equals(selectId)) {
                    projectBox.setSelectedIndex(i);
                }
            }
        } finally {
            reloadingProjects = false;
        }
        projectChanged();
    }

    private void run() {
        Job current = job;
        Map<String, String> values = new LinkedHashMap<>(form.values());
        values.put(Keys.PROJECT, project().id());
        Config config = new Config(values);
        List<String> missing = config.missing(current.params());
        if (!missing.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Bitte ausfüllen: " + String.join(", ", missing));
            return;
        }
        form.remember();
        runButton.setEnabled(false);
        folderButton.setEnabled(false);
        console.clear();
        console.running();
        Thread.ofVirtual().start(() -> {
            long start = System.nanoTime();
            List<Path> files = List.of();
            try {
                files = current.run(config, this::append);
                append("Fertig in %,d ms.".formatted((System.nanoTime() - start) / 1_000_000));
                console.ready();
            } catch (Exception ex) {
                append("FEHLER: " + ex.getMessage());
                console.failed();
            } finally {
                Path folder = files.isEmpty() ? null : files.getFirst().getParent();
                SwingUtilities.invokeLater(() -> {
                    runButton.setEnabled(true);
                    lastFolder = folder;
                    folderButton.setEnabled(folder != null);
                });
            }
        });
    }

    /** The file's "job" entry selects the job; all other entries fill the form. */
    private void loadJson() {
        JFileChooser chooser = jsonChooser();
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path path = chooser.getSelectedFile().toPath();
        try {
            Map<String, String> values = new HashMap<>(Json.parse(Files.readString(path)));
            String jobId = values.remove("job");
            String projectId = values.remove(Keys.PROJECT);
            if (projectId != null) {
                for (int i = 0; i < projectBox.getItemCount(); i++) {
                    if (projectBox.getItemAt(i).id().equalsIgnoreCase(projectId)) {
                        projectBox.setSelectedIndex(i);
                    }
                }
            }
            Job target = job;
            for (int i = 0; i < jobBox.getItemCount(); i++) {
                if (jobBox.getItemAt(i).id().equals(jobId)) {
                    target = jobBox.getItemAt(i);
                }
            }
            if (target != jobBox.getSelectedItem()) {
                jobBox.setSelectedItem(target); // fires select(target, null) …
            }
            select(target, values);              // … then apply the file's values
            PREFS.put("jsonDir", path.getParent().toString());
            append("Konfiguration geladen: " + path);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Konfiguration kann nicht geladen werden:\n" + ex.getMessage());
        }
    }

    private void saveJson() {
        JFileChooser chooser = jsonChooser();
        chooser.setSelectedFile(chooser.getCurrentDirectory().toPath().resolve(defaultConfigName()).toFile());
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path path = chooser.getSelectedFile().toPath();
        if (!path.getFileName().toString().toLowerCase().endsWith(".json")) {
            path = path.resolveSibling(path.getFileName() + ".json");
        }
        if (Files.exists(path) && JOptionPane.showConfirmDialog(frame, path.getFileName() + " existiert bereits. Überschreiben?",
                "Konfiguration speichern", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("job", job.id());
        if (project() != NO_PROJECT) {
            content.put(Keys.PROJECT, project().id());
        }
        content.putAll(form.typedValues());
        try {
            Files.writeString(path, Json.write(content), StandardCharsets.UTF_8);
            PREFS.put("jsonDir", path.getParent().toString());
            append("Konfiguration gespeichert: " + path);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Speichern fehlgeschlagen:\n" + ex.getMessage());
        }
    }

    /** Opens in the project's configuration folder (falls back to the last used folder). */
    private static JFileChooser jsonChooser() {
        Path dir = configDirectory();
        String last = PREFS.get("jsonDir", "");
        JFileChooser chooser = new JFileChooser(dir != null ? dir.toFile() : last.isEmpty() ? null : new java.io.File(last));
        chooser.setFileFilter(new FileNameExtensionFilter("JSON-Konfiguration (*.json)", "json"));
        return chooser;
    }

    /**
     * {@code src/main/resources/konfigurationen} of the DevToolbox project (the folder that also holds
     * {@code migrationsprojekte}); without sources (jar only) {@code konfigurationen} next to {@code migrationsprojekte}.
     * Created on first use; {@code null} if neither can be determined.
     */
    static Path configDirectory() {
        Path projects = MigrationProject.directory();
        if (projects == null || projects.getParent() == null) {
            return null;
        }
        Path root = projects.getParent();
        Path resources = root.resolve("src/main/resources");
        Path dir = Files.isDirectory(resources) ? resources.resolve("konfigurationen") : root.resolve("konfigurationen");
        try {
            return Files.createDirectories(dir);
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /** e.g. "vgb-spezifikation-risiko-alle-phasen.json" or "plg-implementierung-risiko-read-process.json". */
    private String defaultConfigName() {
        Map<String, String> values = form.values();
        List<String> parts = new java.util.ArrayList<>();
        parts.add(project() == NO_PROJECT ? "ohne-projekt" : project().id());
        parts.add(job.fileTag());
        String entity = values.getOrDefault(Keys.TERMS, values.getOrDefault(Keys.ENTITY, ""));
        List<String> names = Config.split(entity);
        if (!names.isEmpty()) {
            parts.add(names.getFirst());
        }
        if (values.containsKey(Keys.PHASES)) {
            List<String> phases = Config.split(values.get(Keys.PHASES));
            parts.add(phases.size() == 3 ? "alle-phasen" : String.join("-", phases));
        }
        if ("compact".equals(values.get(Keys.DETAIL))) {
            parts.add("kompakt");
        }
        return parts.stream().map(de.devtoolbox.tools.codecollector.CodeCollectorTool::slug).filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.joining("-")) + ".json";
    }

    private void append(String line) {
        console.append(line);
    }

    private void open(Path path) {
        try {
            Desktop.getDesktop().open(path.toFile());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, "Kann nicht geöffnet werden: " + ex.getMessage());
        }
    }

    /** Lets the form shrink to the viewport width instead of forcing a horizontal scroll bar. */
    private static final class WidthTrackingPanel extends JPanel implements Scrollable {
        WidthTrackingPanel() {
            super(new BorderLayout());
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return visible.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
