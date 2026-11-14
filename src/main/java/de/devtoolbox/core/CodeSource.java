package de.devtoolbox.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Where code to be searched comes from: a local folder or a Git repository (internal Bitbucket Data Center, HTTPS
 * or SSH).
 * A repository is cloned shallowly into a cache folder and updated on every run; the scan then works on that folder.
 *
 * <p>Accepted spellings of a repository:
 * <ul>
 *   <li>clone URL: {@code https://bitbucket.firma.de/scm/sach/sachvertrag.git} or
 *       {@code ssh://git@bitbucket.firma.de:7999/sach/sachvertrag.git}</li>
 *   <li>clone URL with branch and subfolder: {@code …/sachvertrag.git#develop:sachvertrag-migration-batch}
 *       ({@code :} cannot occur in a Git branch name, so it separates both safely)</li>
 *   <li>the browser address of Bitbucket Data Center, e.g.
 *       {@code https://bitbucket.firma.de/projects/SACH/repos/sachvertrag/browse/sachvertrag-migration-batch?at=refs%2Fheads%2Fdevelop}
 *       – repository, folder and branch are taken from it</li>
 * </ul>
 * Sign-in is left to Git: the Git Credential Manager (part of Git for Windows) asks once and keeps the login; the
 * toolbox stores no password.
 */
public final class CodeSource {

    /** A repository to clone: clone URL, branch (empty = default branch) and subfolder (empty = whole repository). */
    public record Remote(String url, String branch, String subfolder) {

        /** Short text for logs and headers, without credentials. */
        public String describe() {
            return mask(url) + (branch.isEmpty() ? "" : " · Branch " + branch)
                    + (subfolder.isEmpty() ? "" : " · Ordner " + subfolder);
        }
    }

    /** The local folder to search and, for a repository, what was checked out (else null). */
    public record Resolved(Path folder, String origin) {
    }

    /** How long clone/fetch may take (large repositories, sign-in dialog of the credential manager). */
    static final long TIMEOUT_MINUTES = 20;

    private static final Pattern BROWSE = Pattern.compile(
            "(?i)^(https?://[^/?#]+(?:/[^?#]*?)?)/(projects|users)/([^/?#]+)/repos/([^/?#]+)(?:/browse(/[^?#]*)?)?/?(?:\\?([^#]*))?(?:#.*)?$");
    private static final Pattern USERINFO = Pattern.compile("^(\\w+://)([^/@:]+):[^/@]*@");
    private static final Map<Path, Object> LOCKS = new ConcurrentHashMap<>();

    private CodeSource() {
    }

    /** True if {@code value} names a repository rather than a local folder. */
    public static boolean isRemote(String value) {
        if (value == null) {
            return false;
        }
        String v = value.strip().toLowerCase(Locale.ROOT);
        return v.startsWith("https://") || v.startsWith("http://") || v.startsWith("ssh://") || v.startsWith("git@");
    }

    /** The repository named by {@code value}, or empty if it is a local folder. */
    public static Optional<Remote> parse(String value) {
        if (!isRemote(value)) {
            return Optional.empty();
        }
        String v = value.strip();
        Matcher browse = BROWSE.matcher(v);
        if (browse.matches() && !browse.group(4).toLowerCase(Locale.ROOT).endsWith(".git")) {
            String owner = browse.group(2).equalsIgnoreCase("users") ? "~" + browse.group(3) : browse.group(3);
            String url = browse.group(1) + "/scm/" + owner.toLowerCase(Locale.ROOT) + "/"
                    + browse.group(4).toLowerCase(Locale.ROOT) + ".git";
            String folder = browse.group(5) == null ? "" : decode(browse.group(5));
            return Optional.of(new Remote(url, branchFromQuery(browse.group(6)), cleanFolder(folder)));
        }
        String url = v;
        String branch = "";
        String folder = "";
        int hash = v.indexOf('#');
        if (hash >= 0) {
            url = v.substring(0, hash);
            String fragment = v.substring(hash + 1);
            int colon = fragment.indexOf(':');
            branch = (colon < 0 ? fragment : fragment.substring(0, colon)).strip();
            folder = colon < 0 ? "" : fragment.substring(colon + 1);
        }
        return Optional.of(new Remote(url.strip(), stripHeads(branch), cleanFolder(folder)));
    }

    /**
     * The local folder for {@code value}: the folder itself, or for a repository the up-to-date clone (created on
     * first use). Returns null if {@code value} is empty.
     *
     * @throws IllegalArgumentException with a message for the user if Git is missing or the repository cannot be
     *                                  read and there is no earlier clone to fall back on
     */
    public static Resolved resolve(String value, Consumer<String> log) throws IOException {
        if (value == null || value.isBlank()) {
            return null;
        }
        Optional<Remote> remote = parse(value);
        if (remote.isEmpty()) {
            return new Resolved(Path.of(value.strip()).toAbsolutePath().normalize(), null);
        }
        return resolve(remote.get(), log);
    }

    /** The up-to-date clone of {@code r} (created on first use), narrowed to its subfolder. */
    public static Resolved resolve(Remote r, Consumer<String> log) throws IOException {
        Path repo = cacheFolder(r);
        synchronized (LOCKS.computeIfAbsent(repo, k -> new Object())) {
            update(r, repo, log);
        }
        String commit = git(repo, List.of("log", "-1", "--format=%h vom %cs: %s")).output().strip();
        String origin = r.describe() + " · Commit " + commit;
        log.accept("Stand: " + origin);
        Path folder = r.subfolder().isEmpty() ? repo : repo.resolve(r.subfolder()).normalize();
        if (!folder.startsWith(repo) || !Files.isDirectory(folder)) {
            throw new IllegalArgumentException("Ordner „" + r.subfolder() + "“ gibt es im Repository nicht. "
                    + "Vorhanden auf oberster Ebene: " + String.join(", ", topFolders(repo)));
        }
        return new Resolved(folder, origin);
    }

    /** The folder of an earlier clone of {@code value} (for suggestions without network), or null. */
    public static Path cached(String value) {
        return parse(value).map(r -> {
            Path repo = cacheFolder(r);
            Path folder = r.subfolder().isEmpty() ? repo : repo.resolve(r.subfolder());
            return Files.isDirectory(repo.resolve(".git")) && Files.isDirectory(folder) ? folder : null;
        }).orElse(null);
    }

    /** {@code value} without a password or token in the URL. */
    public static String mask(String value) {
        return value == null ? "" : USERINFO.matcher(value).replaceFirst("$1$2:***@");
    }

    /** Cache root, one folder per repository and branch: {@code ~/.devtoolbox/repos}. */
    static Path cacheRoot() {
        return Path.of(System.getProperty("user.home"), ".devtoolbox", "repos");
    }

    static Path cacheFolder(Remote r) {
        String key = USERINFO.matcher(r.url()).replaceFirst("$1").toLowerCase(Locale.ROOT).replaceFirst("\\.git$", "")
                + "#" + r.branch();
        String name = key.replaceFirst("^\\w+://", "").replaceFirst("^[^/]*/", "")
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (name.length() > 60) {
            name = name.substring(name.length() - 60).replaceAll("^-", "");
        }
        return cacheRoot().resolve(name + "-" + hash(key));
    }

    // ---- git ----

    private static void update(Remote r, Path repo, Consumer<String> log) throws IOException {
        decideSsh();
        if (Files.isDirectory(repo.resolve(".git"))) {
            log.accept("Aktualisiere " + r.describe() + " …");
            GitResult fetch = git(repo, args("fetch", "--depth", "1", "--no-tags", "origin",
                    r.branch().isEmpty() ? "HEAD" : r.branch()));
            if (fetch.ok()) {
                GitResult reset = git(repo, List.of("reset", "--hard", "-q", "FETCH_HEAD"));
                if (!reset.ok()) {
                    throw new IllegalArgumentException("Git-Fehler beim Aktualisieren: " + reset.output().strip());
                }
                git(repo, List.of("clean", "-fdq"));
                return;
            }
            // offline or no access right now: the earlier clone is better than nothing, but say so
            log.accept("WARNUNG: Aktualisieren fehlgeschlagen – es wird der zuletzt geholte Stand verwendet. "
                    + explain(fetch.output(), r));
            return;
        }
        log.accept("Klone " + r.describe() + " nach " + repo + " (einmalig, kann einige Minuten dauern) …");
        Files.createDirectories(repo.getParent());
        Path partial = repo.resolveSibling(repo.getFileName() + ".tmp");
        deleteRecursively(partial);
        List<String> clone = args("clone", "--depth", "1", "--single-branch", "--no-tags");
        if (!r.branch().isEmpty()) {
            clone.addAll(List.of("--branch", r.branch()));
        }
        clone.addAll(List.of(r.url(), partial.toString()));
        GitResult result = git(null, clone);
        if (!result.ok()) {
            deleteRecursively(partial);
            throw new IllegalArgumentException("Repository konnte nicht geklont werden: " + explain(result.output(), r));
        }
        Files.move(partial, repo);
    }

    private static List<String> args(String... values) {
        return new ArrayList<>(List.of(values));
    }

    record GitResult(int exitCode, String output) {
        boolean ok() {
            return exitCode == 0;
        }
    }

    /** True unless the user configured an own ssh command (environment or git config), which is then kept. */
    private static volatile Boolean batchSshDecided;
    private static boolean batchSsh;

    private static void decideSsh() throws IOException {
        if (batchSshDecided != null) {
            return;
        }
        boolean own = System.getenv("GIT_SSH_COMMAND") != null || System.getenv("GIT_SSH") != null;
        if (!own) {
            batchSsh = false;
            own = !git(null, List.of("config", "--get", "core.sshCommand")).output().isBlank();
        }
        batchSsh = !own;
        batchSshDecided = true;
    }

    private static GitResult git(Path dir, List<String> args) throws IOException {
        List<String> command = new ArrayList<>(List.of("git"));
        if (dir != null) {
            command.addAll(List.of("-C", dir.toString()));
        }
        command.addAll(args);
        ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);
        // no prompt in a console nobody sees; the credential manager's sign-in window still appears
        pb.environment().put("GIT_TERMINAL_PROMPT", "0");
        if (batchSsh) {
            // ssh would wait for a passphrase or host key confirmation; keys from the agent still work
            pb.environment().put("GIT_SSH_COMMAND", "ssh -o BatchMode=yes");
        }
        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new IllegalArgumentException("Git ist nicht installiert oder nicht im PATH – für Bitbucket-Adressen "
                    + "wird Git for Windows benötigt (https://git-scm.com).");
        }
        process.getOutputStream().close();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Thread reader = Thread.ofVirtual().start(() -> copy(process.getInputStream(), out));
        try {
            if (!process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                return new GitResult(-1, "Zeitüberschreitung nach " + TIMEOUT_MINUTES + " Minuten");
            }
            reader.join();
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Abgebrochen", e);
        }
        return new GitResult(process.exitValue(), mask(out.toString(StandardCharsets.UTF_8)));
    }

    private static void copy(InputStream in, ByteArrayOutputStream out) {
        try (in) {
            in.transferTo(out);
        } catch (IOException ignored) {
            // process ended
        }
    }

    /** Git's message plus what to do, for the usual causes with an internal Bitbucket over HTTPS. */
    static String explain(String output, Remote r) {
        String o = output.toLowerCase(Locale.ROOT);
        String last = output.strip().lines().filter(l -> !l.isBlank()).reduce((a, b) -> b).orElse("").strip();
        String hint;
        boolean ssh = r.url().toLowerCase(Locale.ROOT).startsWith("ssh://") || r.url().startsWith("git@");
        if (o.contains("host key verification failed")) {
            hint = "Der Host-Schlüssel von Bitbucket ist noch nicht bestätigt. Einmal in einem Terminal „"
                    + sshProbe(r.url()) + "“ ausführen und mit „yes“ bestätigen.";
        } else if (o.contains("permission denied (publickey") || ssh && o.contains("could not read from remote")
                && !o.contains("could not resolve")) {
            hint = "SSH-Anmeldung fehlgeschlagen. Öffentlichen Schlüssel in Bitbucket unter Profil → SSH-Schlüssel "
                    + "hinterlegen; ist der Schlüssel mit Passphrase geschützt, ihn in den ssh-agent laden "
                    + "(„ssh-add“) – die Toolbox kann keine Passphrase abfragen.";
        } else if (o.contains("authentication failed") || o.contains("could not read username") || o.contains(" 401")
                || o.contains("terminal prompts disabled")) {
            hint = "Anmeldung fehlgeschlagen. Einmal in einem Terminal „git clone " + mask(r.url())
                    + "“ ausführen und anmelden – der Git Credential Manager merkt sich die Anmeldung. "
                    + "Alternativ in Bitbucket unter Profil → HTTP-Zugriffstoken ein Token mit Leserecht anlegen "
                    + "und bei der Anmeldung als Passwort verwenden.";
        } else if (o.contains("ssl certificate problem") || o.contains("unable to get local issuer")
                || o.contains("self signed certificate") || o.contains("self-signed certificate")) {
            hint = "Das Firmenzertifikat wird nicht erkannt. Abhilfe unter Windows: "
                    + "„git config --global http.sslBackend schannel“ (nutzt den Zertifikatsspeicher von Windows).";
        } else if (o.contains("could not resolve host") || o.contains("failed to connect") || o.contains("timed out")
                || o.contains("zeitüberschreitung")) {
            hint = "Bitbucket nicht erreichbar – Netz/VPN prüfen.";
        } else if (o.contains("remote branch") && o.contains("not found") || o.contains("couldn't find remote ref")) {
            hint = "Den Branch „" + r.branch() + "“ gibt es im Repository nicht.";
        } else if (o.contains(" 403") || o.contains("not found") || o.contains("does not exist")) {
            hint = "Repository nicht gefunden oder keine Leserechte. Klon-Adresse in Bitbucket über „Klonen“ (HTTPS) "
                    + "kopieren.";
        } else {
            hint = "";
        }
        return (hint.isEmpty() ? "" : hint + " ") + "(Git: " + last + ")";
    }

    // ---- helpers ----

    /** "ssh -p 7999 git@host" for ssh://git@host:7999/…, "ssh git@host" for git@host:… */
    static String sshProbe(String url) {
        Matcher m = Pattern.compile("^ssh://([^@/]+@)?([^:/]+)(?::([0-9]+))?/.*").matcher(url);
        if (m.matches()) {
            return "ssh " + (m.group(3) == null ? "" : "-p " + m.group(3) + " ")
                    + (m.group(1) == null ? "" : m.group(1)) + m.group(2);
        }
        return "ssh " + url.replaceFirst(":.*$", "");
    }

    private static String branchFromQuery(String query) {
        if (query == null) {
            return "";
        }
        for (String part : query.split("&")) {
            if (part.startsWith("at=")) {
                return stripHeads(decode(part.substring(3)));
            }
        }
        return "";
    }

    private static String stripHeads(String branch) {
        return branch.strip().replaceFirst("^refs/heads/", "");
    }

    private static String decode(String value) {
        return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private static String cleanFolder(String folder) {
        return folder.replace('\\', '/').strip().replaceAll("^/+|/+$", "");
    }

    private static String hash(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> topFolders(Path repo) throws IOException {
        try (Stream<Path> list = Files.list(repo)) {
            return list.filter(Files::isDirectory).map(p -> p.getFileName().toString())
                    .filter(n -> !n.startsWith(".")).sorted().toList();
        }
    }

    private static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                p.toFile().setWritable(true); // Git marks pack files read-only on Windows
                Files.deleteIfExists(p);
            }
        }
    }
}
