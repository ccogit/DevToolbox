package de.devtoolbox.core;

import de.devtoolbox.tools.codecollector.Conventions;
import de.devtoolbox.tools.codecollector.TargetModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Proposes the business entities of a migration project: exactly from the target model, largely from the code of
 * the reference project (to be reviewed), otherwise from the entities of the other projects (to be adapted).
 */
public final class EntitySuggestions {

    /** Where a suggestion comes from; {@code hint} is the text for the user. */
    public enum Source {
        TARGET_MODEL("aus dem Zielmodell – passt"),
        REFERENCE("aus dem Referenzprojekt extrahiert – bitte prüfen"),
        OTHER_PROJECTS("aus anderen Projekten – bitte anpassen"),
        NONE("nichts zum Ableiten gefunden – bitte Entitäten eintragen");

        public final String hint;

        Source(String hint) {
            this.hint = hint;
        }
    }

    public record Suggestion(List<String> names, Source source) {
    }

    private EntitySuggestions() {
    }

    /**
     * @param targetModel target model folder or {@code null}
     * @param reference   code of the reference project or {@code null}
     * @param ownId       id of the project being edited (excluded from "other projects"), may be {@code null}
     */
    public static Suggestion suggest(Path targetModel, Path reference, String ownId) {
        if (targetModel != null && Files.isDirectory(targetModel)) {
            try {
                List<String> names = TargetModel.entityNames(targetModel);
                if (!names.isEmpty()) {
                    return new Suggestion(names, Source.TARGET_MODEL);
                }
            } catch (IOException | RuntimeException e) {
                // fall through to the next source
            }
        }
        if (reference != null && Files.isDirectory(reference)) {
            List<String> names = fromCode(reference);
            if (!names.isEmpty()) {
                return new Suggestion(names, Source.REFERENCE);
            }
        }
        TreeSet<String> union = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (MigrationProject p : MigrationProject.all()) {
            if (ownId == null || !p.id().equalsIgnoreCase(ownId)) {
                union.addAll(p.entities());
            }
        }
        return union.isEmpty() ? new Suggestion(List.of(), Source.NONE)
                : new Suggestion(List.copyOf(union), Source.OTHER_PROJECTS);
    }

    /** Entities of the data structures (TO, PO, DTO, entity) in the code, without Alt/Neu prefix, sorted. */
    static List<String> fromCode(Path reference) {
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
