package de.devtoolbox.core;

import de.devtoolbox.tools.codecollector.Conventions;
import de.devtoolbox.tools.standards.JavaCatalog;
import de.devtoolbox.tools.codecollector.DdlReader;
import de.devtoolbox.tools.codecollector.TargetModel;
import de.devtoolbox.tools.mappingdoc.MappingTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Proposes the business entities of a migration project by merging all available sources: legacy data (DDL,
 * reference code) and target model (JSON, mapping templates). Names are merged case-insensitively, ignoring
 * non-alphanumerics; each entry says where the entity occurs.
 */
public final class EntitySuggestions {

    /** One suggested entity: legacy = in DDL or reference code; target = in target model or mapping templates. */
    public record Entry(String name, boolean legacy, boolean target) {
        /** Provenance for display, e.g. "Altdaten + Zielmodell". */
        public String provenance() {
            return legacy && target ? "Altdaten + Zielmodell" : legacy ? "nur Altdaten" : target ? "nur Zielmodell"
                    : "aus anderen Projekten";
        }

        public int priority() {
            return legacy && target ? 3 : target ? 2 : legacy ? 1 : 0;
        }

        public String label() {
            return name + " – " + provenance();
        }
    }

    public record Suggestion(List<Entry> entries) {
        public List<String> names() {
            return entries.stream().map(Entry::name).toList();
        }

        public boolean isEmpty() {
            return entries.isEmpty();
        }

        /** Best initial suggestion without changing the stable alphabetical display order. */
        public java.util.Optional<Entry> best() {
            return entries.stream().sorted(Comparator.comparingInt(Entry::priority).reversed()
                    .thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER)).findFirst();
        }
    }

    private EntitySuggestions() {
    }

    /**
     * Merges all available sources (any path may be {@code null}); the target model's spelling is preferred. Only if
     * nothing is found, the entities of the other projects are offered (legacy and target both false).
     *
     * @param ownId id of the project being edited (excluded from "other projects"), may be {@code null}
     */
    public static Suggestion suggest(Path ddl, Path targetModel, Path mappingTemplates, Path reference, String ownId) {
        Map<String, String> display = new LinkedHashMap<>();
        Set<String> legacy = new HashSet<>();
        Set<String> target = new HashSet<>();
        if (targetModel != null && Files.isDirectory(targetModel)) {
            try {
                add(TargetModel.entityNames(targetModel), true, display, legacy, target);
            } catch (IOException | RuntimeException e) {
                // source unusable
            }
        }
        if (mappingTemplates != null && Files.isDirectory(mappingTemplates)) {
            try {
                for (MappingTemplate t : MappingTemplate.all(mappingTemplates)) {
                    add(t.entities(), true, display, legacy, target);
                }
            } catch (IOException | RuntimeException e) {
                // source unusable
            }
        }
        if (ddl != null && Files.exists(ddl)) {
            try {
                List<String> names = new ArrayList<>();
                for (DdlReader.Table t : DdlReader.read(ddl)) {
                    if (!t.isView()) {
                        names.add(tableName(t));
                    }
                }
                add(names, false, display, legacy, target);
            } catch (IOException | RuntimeException e) {
                // source unusable
            }
        }
        if (reference != null && Files.isDirectory(reference)) {
            add(fromCode(reference), false, display, legacy, target);
        }
        List<Entry> entries = new ArrayList<>();
        display.forEach((key, name) -> entries.add(new Entry(name, legacy.contains(key), target.contains(key))));
        if (entries.isEmpty()) {
            TreeSet<String> union = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (MigrationProject p : MigrationProject.all()) {
                if (ownId == null || !p.id().equalsIgnoreCase(ownId)) {
                    union.addAll(p.entities());
                }
            }
            union.forEach(n -> entries.add(new Entry(n, false, false)));
        }
        entries.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
        return new Suggestion(List.copyOf(entries));
    }

    /** Target names are added first, so their spelling wins; legacy names only fill the gaps. */
    private static void add(List<String> names, boolean isTarget, Map<String, String> display, Set<String> legacy,
                            Set<String> target) {
        for (String name : names) {
            String key = name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
            if (key.isEmpty()) {
                continue;
            }
            (isTarget ? target : legacy).add(key);
            display.putIfAbsent(key, name);
        }
    }

    /** Label if short (max. 3 words), else a short comment, else the table name. */
    static String tableName(DdlReader.Table t) {
        for (String text : new String[]{t.label(), t.comment()}) {
            if (text != null && !text.isBlank() && text.strip().split("\\s+").length <= 3) {
                return text.strip();
            }
        }
        return t.name();
    }

    /** Finds entities in models and in Read/Process/Write classes, with shared naming rules. */
    public static List<String> fromCode(Path reference) {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (reference == null || !Files.isDirectory(reference)) {
            return List.of();
        }
        try {
            JavaCatalog catalog = JavaCatalog.scan(reference);
            for (var group : catalog.entities().values()) {
                if (!group.isEmpty()) {
                    String name = group.getFirst().name();
                    JavaCatalog.Recognition first = JavaCatalog.recognise(group.getFirst());
                    if (first == null) {
                        first = JavaCatalog.roleBeforeVariant(group.getFirst());
                    }
                    if (first != null && first.entity().length() > 1) {
                        names.add(first.entity());
                    }
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // A corrupt optional code source must not prevent suggestions from the target model.
        }
        return List.copyOf(names);
    }

    /** Entities of the code of several folders, merged case-insensitively, deduplicated and sorted. */
    public static List<String> fromFolders(List<Path> folders) {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Path folder : folders) {
            if (folder != null && Files.isDirectory(folder)) {
                names.addAll(fromCode(folder));
            }
        }
        return List.copyOf(names);
    }

    private static boolean skipped(Path rel) {
        for (Path part : rel) {
            String s = part.toString();
            if (s.equals("target") || s.equals("test") || s.startsWith(".")) {
                return true;
            }
        }
        return false;
    }
}
