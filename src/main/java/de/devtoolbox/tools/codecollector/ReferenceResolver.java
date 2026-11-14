package de.devtoolbox.tools.codecollector;

import de.devtoolbox.tools.codecollector.ProjectScanner.Hit;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Finds project types used directly by the hit files (one level deep): imports, same-package types,
 * wildcard imports and fully qualified names (e.g. {@code class="..."} in Spring XML).
 * Catches DTOs/entities that never mention the search term themselves.
 */
final class ReferenceResolver {

    private static final Pattern WILDCARD_IMPORT = Pattern.compile("^\\s*import\\s+([\\w.]+)\\.\\*\\s*;", Pattern.MULTILINE);
    private static final Pattern TYPE_TOKEN = Pattern.compile("\\b[A-Z][A-Za-z0-9_]*\\b");
    private static final Pattern QUALIFIED = Pattern.compile("\\b[a-z_]\\w*(?:\\.\\w+)+\\b");

    private ReferenceResolver() {
    }

    /** @return referenced file → the hit files that reference it (hits themselves excluded) */
    static Map<Path, List<Path>> resolve(List<Hit> hits, Map<String, Path> typesByFqn) {
        Map<String, Map<String, Path>> byPackage = new HashMap<>();
        typesByFqn.forEach((fqn, path) -> {
            int dot = fqn.lastIndexOf('.');
            String pkg = dot < 0 ? "" : fqn.substring(0, dot);
            byPackage.computeIfAbsent(pkg, k -> new HashMap<>()).put(fqn.substring(dot + 1), path);
        });
        Set<Path> hitPaths = hits.stream().map(Hit::path).collect(Collectors.toSet());
        Map<Path, List<Path>> references = new LinkedHashMap<>();
        for (Hit hit : hits) {
            Set<Path> found = new LinkedHashSet<>();
            Matcher qualified = QUALIFIED.matcher(hit.content());
            while (qualified.find()) {
                addQualified(qualified.group(), typesByFqn, found);
            }
            if (hit.path().getFileName().toString().endsWith(".java")) {
                Set<String> packages = new HashSet<>();
                packages.add(ProjectScanner.packageOf(hit.content()));
                Matcher wildcard = WILDCARD_IMPORT.matcher(hit.content());
                while (wildcard.find()) {
                    packages.add(wildcard.group(1));
                }
                Matcher token = TYPE_TOKEN.matcher(hit.content());
                while (token.find()) {
                    for (String pkg : packages) {
                        Path path = byPackage.getOrDefault(pkg, Map.of()).get(token.group());
                        if (path != null) {
                            found.add(path);
                        }
                    }
                }
            }
            for (Path path : found) {
                if (!hitPaths.contains(path)) {
                    references.computeIfAbsent(path, _ -> new ArrayList<>()).add(hit.path());
                }
            }
        }
        return references;
    }

    /** Also resolves {@code pkg.Outer.Inner} and static imports {@code pkg.Type.member} to {@code pkg.Type}. */
    private static void addQualified(String name, Map<String, Path> typesByFqn, Set<Path> found) {
        Path path = typesByFqn.get(name);
        int dot = name.lastIndexOf('.');
        if (path == null && dot > 0) {
            path = typesByFqn.get(name.substring(0, dot));
        }
        if (path != null) {
            found.add(path);
        }
    }
}
