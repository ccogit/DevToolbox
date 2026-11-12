package de.devtoolbox.core;

import de.devtoolbox.tools.codecollector.Conventions;
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
            return legacy && target ? "Altdaten + Zielmodell" : legacy ? "nur Altdaten" : "nur Zielmodell";
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
    }

    private EntitySuggestions() {
    }

    /** One entity of the effective list: {@code attributes} is -1 if unknown (only the target model knows it). */
    public record Item(String name, String origin, int attributes) {
        /** e.g. "Risiko – 11 Attribute (Zielmodell)" or "Deckung – Altdaten". */
        public String label() {
            return name + " – " + (attributes >= 0 ? attributes + (attributes == 1 ? " Attribut" : " Attribute")
                    + " (" + origin + ")" : origin);
        }
    }

    /**
     * The automatically derived entities plus the user's extra entries.
     *
     * @param automatic    derived from the target model, or (without one) from DDL, mapping templates, reference code
     * @param extras       stored extra names that are in no source, in stored order
     * @param fromTarget   true if {@code automatic} comes from the target model
     */
    public record Effective(List<Item> automatic, List<String> extras, boolean fromTarget) {
        /** The given names without those that are in the automatic list (case-insensitive, ignoring non-alphanumerics). */
        public List<String> withoutAutomatic(List<String> names) {
            Set<String> known = new HashSet<>();
            automatic.forEach(i -> known.add(key(i.name())));
            return names.stream().filter(n -> !known.contains(key(n))).toList();
        }

        /** All names: the automatic ones, then the extras, without duplicates. */
        public List<String> names() {
            List<String> names = new ArrayList<>(automatic.stream().map(Item::name).toList());
            names.addAll(extras);
            return List.copyOf(names);
        }
    }

    /**
     * The effective entity list: if the target model folder contains JSON files, its entities (with attribute
     * counts); otherwise those of DDL, mapping templates and reference code. Plus the stored extras, merged
     * case-insensitively (ignoring non-alphanumerics) with the automatic list – extras already in it are dropped.
     * Any path may be {@code null}. Reads files (and, without target model, scans the reference code): not for the EDT.
     */
    public static Effective effective(Path ddl, Path targetModel, Path mappingTemplates, Path reference,
                                      List<String> storedExtras) {
        List<Item> automatic = new ArrayList<>();
        boolean fromTarget = false;
        try {
            if (!TargetModel.files(targetModel).isEmpty()) {
                TargetModel.entityAttributeCounts(targetModel).forEach((name, count) -> automatic.add(
                        new Item(name, "Zielmodell", count)));
                fromTarget = !automatic.isEmpty();
            }
        } catch (IOException | RuntimeException e) {
            // target model unusable: derive from the legacy side
        }
        if (!fromTarget) {
            for (Entry e : suggest(ddl, null, mappingTemplates, reference).entries()) {
                automatic.add(new Item(e.name(), e.legacy() ? "Altdaten" : "Mapping-Vorlage", -1));
            }
        }
        Set<String> known = new HashSet<>();
        automatic.forEach(i -> known.add(key(i.name())));
        List<String> extras = new ArrayList<>();
        for (String name : storedExtras) {
            String key = key(name);
            if (!key.isEmpty() && known.add(key)) {
                extras.add(name);
            }
        }
        return new Effective(List.copyOf(automatic), List.copyOf(extras), fromTarget);
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    /**
     * Merges DDL, target model, mapping templates and reference code (any path may be {@code null}); the target
     * model's spelling is preferred.
     */
    public static Suggestion suggest(Path ddl, Path targetModel, Path mappingTemplates, Path reference) {
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
        entries.sort(Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER));
        return new Suggestion(List.copyOf(entries));
    }

    /** Target names are added first, so their spelling wins; legacy names only fill the gaps. */
    private static void add(List<String> names, boolean isTarget, Map<String, String> display, Set<String> legacy,
                            Set<String> target) {
        for (String name : names) {
            String key = key(name);
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

    /** Entities of the data structures (TO, PO, DTO, entity) in the code, without Alt/Neu prefix, sorted. */
    public static List<String> fromCode(Path reference) {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        try (Stream<Path> walk = Files.walk(reference)) {
            walk.filter(f -> f.getFileName().toString().endsWith(".java") && Files.isRegularFile(f)
                    && !skipped(reference.relativize(f))).forEach(f -> {
                String file = f.getFileName().toString();
                String className = file.substring(0, file.length() - 5);
                try {
                    Conventions.DataStructure ds = Conventions.dataStructure(className, Files.readString(f));
                    if (ds != null) {
                        String stem = Conventions.stripVariantPrefix(ds.stem());
                        if (stem.length() > 1) {
                            names.add(stem);
                        }
                    }
                } catch (IOException | RuntimeException e) {
                    // unreadable file: ignore
                }
            });
        } catch (IOException e) {
            // return what was found
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
