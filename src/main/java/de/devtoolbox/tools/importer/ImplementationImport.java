package de.devtoolbox.tools.importer;

import de.devtoolbox.tools.standards.StandardsTool;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Takes the result ZIP of the "implementation" job (files at their project-relative paths plus
 * IMPLEMENTIERUNG.md), classifies it against the project root and writes the files on request.
 */
public final class ImplementationImport {

    public static final String REPORT = "IMPLEMENTIERUNG.md";

    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern OPL = Pattern.compile("//\\s*TODO\\s+OPL-");
    private static final Pattern DRIVE = Pattern.compile("^[A-Za-z]:.*");

    public enum Status { NEW, OVERWRITE, IDENTICAL, UNEXPECTED, REJECTED }

    /** One ZIP entry: normalised path (forward slashes), classification and size in bytes. */
    public record Entry(String path, Status status, long size) {
    }

    public record Check(String message, boolean problem) {
    }

    public record Plan(Path zip, Path root, String entity, List<Entry> entries, boolean hasReport,
                       List<Check> checks) {

        public long count(Status status) {
            return entries.stream().filter(e -> e.status() == status).count();
        }

        public boolean hasProblems() {
            return checks.stream().anyMatch(Check::problem);
        }
    }

    private ImplementationImport() {
    }

    /**
     * Analyses the ZIP without writing anything.
     *
     * @param standards        nullable; without standards no entry is UNEXPECTED and no artifact can be missing
     * @param phases           e.g. ["Read","Process"] (case-insensitive); null = all phases
     * @param targetAttributes nullable names that must occur in at least one Java file
     */
    public static Plan plan(Path zip, Path projectRoot, String entity, StandardsTool.Stored standards,
                            List<String> phases, List<String> targetAttributes) throws IOException {
        Path root = projectRoot.toAbsolutePath().normalize();
        Map<String, byte[]> files = readZip(zip);
        List<Check> checks = new ArrayList<>();
        Set<String> expected = expectedPaths(standards, entity, phases);
        boolean hasReport = files.keySet().stream().anyMatch(ImplementationImport::isReport);
        if (!hasReport) {
            checks.add(new Check(REPORT + " fehlt im ZIP", true));
        }

        List<Entry> entries = new ArrayList<>();
        Map<String, String> javaTexts = new LinkedHashMap<>();
        List<String> unexpectedJava = new ArrayList<>();
        for (Map.Entry<String, byte[]> e : files.entrySet()) {
            String path = e.getKey();
            byte[] data = e.getValue();
            if (isReport(path)) {
                continue;
            }
            Path target = safeTarget(root, path);
            Status status;
            if (target == null) {
                status = Status.REJECTED;
            } else if (standards != null && !expected.contains(path)) {
                status = Status.UNEXPECTED;
            } else if (!Files.exists(target)) {
                status = Status.NEW;
            } else {
                status = Files.isRegularFile(target) && Arrays.equals(Files.readAllBytes(target), data)
                        ? Status.IDENTICAL : Status.OVERWRITE;
            }
            entries.add(new Entry(path, status, data.length));
            if (status != Status.REJECTED && path.endsWith(".java")) {
                javaTexts.put(path, new String(data, StandardCharsets.UTF_8));
                if (status == Status.UNEXPECTED) {
                    unexpectedJava.add(path);
                }
            }
        }
        entries.sort((a, b) -> a.path().compareToIgnoreCase(b.path()));

        for (Entry en : entries) {
            if (en.status() == Status.REJECTED) {
                checks.add(new Check("Unsicherer Pfad abgelehnt: " + en.path(), true));
            }
        }
        for (String path : new TreeSet<>(expected)) {
            if (!files.containsKey(path)) {
                checks.add(new Check("Erwartetes Artefakt fehlt im ZIP: " + path, true));
            }
        }
        for (String path : unexpectedJava) {
            checks.add(new Check("Unerwartete Java-Datei (nicht in den Standards): " + path, false));
        }
        if (targetAttributes != null) {
            String all = String.join("\n", javaTexts.values());
            for (String attr : targetAttributes) {
                if (attr == null || attr.isBlank()) {
                    continue;
                }
                Pattern p = Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(attr.strip())
                        + "(?![\\p{L}\\p{N}_])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                if (!p.matcher(all).find()) {
                    checks.add(new Check("Zielattribut „" + attr + "“ kommt in keiner Datei vor", true));
                }
            }
        }
        int opl = 0;
        for (String text : javaTexts.values()) {
            Matcher m = OPL.matcher(text);
            while (m.find()) {
                opl++;
            }
        }
        if (opl > 0) {
            checks.add(new Check(opl + " offene Punkte (// TODO OPL-…) im Code", false));
        }
        for (Map.Entry<String, String> j : javaTexts.entrySet()) {
            String expectedPkg = packageOfPath(j.getKey());
            Matcher m = PACKAGE.matcher(j.getValue());
            if (expectedPkg != null && m.find() && !m.group(1).equals(expectedPkg)) {
                checks.add(new Check("Package passt nicht zum Pfad: " + j.getKey() + " deklariert „" + m.group(1)
                        + "“, erwartet „" + expectedPkg + "“", true));
            }
        }
        return new Plan(zip, root, entity, List.copyOf(entries), hasReport, List.copyOf(checks));
    }

    /**
     * Writes NEW entries (and OVERWRITE entries if {@code overwrite}); existing files are first copied to
     * {@code backupDir} under their relative path. UNEXPECTED entries (additional artefacts) are written like NEW
     * ones if the file does not exist yet, otherwise like OVERWRITE. IDENTICAL and REJECTED entries and the report are
     * never written.
     *
     * @return the written paths
     */
    public static List<Path> apply(Plan plan, Path backupDir, boolean overwrite) throws IOException {
        Map<String, byte[]> files = readZip(plan.zip());
        List<Path> written = new ArrayList<>();
        for (Entry e : plan.entries()) {
            Path target = safeTarget(plan.root(), e.path());
            byte[] data = files.get(e.path());
            if (target == null || data == null) {
                continue;
            }
            boolean write = switch (e.status()) {
                case NEW -> true;
                case OVERWRITE -> overwrite;
                // additional artefacts the specification asked for: new files are written, existing ones only
                // with overwrite (and only if they differ)
                case UNEXPECTED -> !Files.exists(target)
                        || overwrite && !(Files.isRegularFile(target) && Arrays.equals(Files.readAllBytes(target), data));
                default -> false;
            };
            if (!write) {
                continue;
            }
            if (Files.isRegularFile(target)) {
                if (backupDir == null) {
                    throw new IllegalArgumentException("Backup-Ordner fehlt");
                }
                Path backup = backupDir.resolve(e.path());
                Files.createDirectories(backup.getParent());
                Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.createDirectories(target.getParent());
            Files.write(target, data);
            written.add(target);
        }
        return written;
    }

    // ---- helpers ----

    private static boolean isReport(String path) {
        return path.equalsIgnoreCase(REPORT);
    }

    private static Set<String> expectedPaths(StandardsTool.Stored standards, String entity, List<String> phases) {
        Set<String> out = new HashSet<>();
        if (standards == null) {
            return out;
        }
        for (StandardsTool.Row row : standards.rows()) {
            boolean inPhase = phases == null
                    || phases.stream().anyMatch(p -> p.strip().equalsIgnoreCase(row.phase().strip()));
            if (inPhase) {
                out.add(normalize(StandardsTool.apply(row.pathOrDefault(), entity)));
            }
        }
        return out;
    }

    private static String normalize(String name) {
        return name.replace('\\', '/');
    }

    /** Target below root, or null if the path is absolute, contains "..", or leaves the root. */
    private static Path safeTarget(Path root, String path) {
        if (path.isBlank() || path.startsWith("/") || DRIVE.matcher(path).matches() || path.indexOf('\0') >= 0) {
            return null;
        }
        for (String seg : path.split("/")) {
            if (seg.equals("..")) {
                return null;
            }
        }
        try {
            Path target = root.resolve(path).normalize();
            return target.startsWith(root) && !target.equals(root) ? target : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** "src/main/java/de/x/A.java" gives "de.x"; null if there is no java source folder in the path. */
    private static String packageOfPath(String path) {
        int i = path.startsWith("java/") ? 0 : path.indexOf("/java/");
        if (i < 0) {
            return null;
        }
        String rest = path.substring(i == 0 ? 5 : i + 6);
        int slash = rest.lastIndexOf('/');
        return slash < 0 ? "" : rest.substring(0, slash).replace('/', '.');
    }

    /** Reads all file entries with normalised names; later duplicates replace earlier ones. */
    private static Map<String, byte[]> readZip(Path zip) throws IOException {
        Map<String, byte[]> out = new LinkedHashMap<>();
        try (InputStream in = Files.newInputStream(zip);
             ZipInputStream zis = new ZipInputStream(in, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = normalize(entry.getName());
                if (entry.isDirectory() || name.endsWith("/")) {
                    continue;
                }
                while (name.startsWith("./")) {
                    name = name.substring(2);
                }
                out.put(name, zis.readAllBytes());
            }
        }
        return out;
    }
}
