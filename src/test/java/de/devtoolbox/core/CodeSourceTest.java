package de.devtoolbox.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CodeSourceTest {

    @Test
    void browserAddressOfBitbucketDataCenterWithContextPathAndBranch() {
        CodeSource.Remote r = CodeSource.parse("https://bitbucket.firma.de/bitbucket/projects/SACH/repos/firma-sachvertrag/"
                + "browse?at=refs%2Fheads%2Fmigration%2Fbeispiel_branch").orElseThrow();
        assertEquals("https://bitbucket.firma.de/bitbucket/scm/sach/firma-sachvertrag.git", r.url());
        assertEquals("migration/beispiel_branch", r.branch());
        assertEquals("", r.subfolder());
    }

    @Test
    void browserAddressOfAFolder() {
        CodeSource.Remote r = CodeSource.parse("https://bitbucket.firma.de/bitbucket/projects/SACH/repos/firma-sachvertrag/"
                + "browse/firma-sachvertrag-migration-batch/src?at=refs%2Fheads%2Fdevelop").orElseThrow();
        assertEquals("https://bitbucket.firma.de/bitbucket/scm/sach/firma-sachvertrag.git", r.url());
        assertEquals("develop", r.branch());
        assertEquals("firma-sachvertrag-migration-batch/src", r.subfolder());
    }

    @Test
    void browserAddressWithoutBranchUsesTheDefaultBranch() {
        CodeSource.Remote r = CodeSource.parse("https://bitbucket.firma.de/bitbucket/projects/SACH/repos/firma-sachvertrag")
                .orElseThrow();
        assertEquals("https://bitbucket.firma.de/bitbucket/scm/sach/firma-sachvertrag.git", r.url());
        assertEquals("", r.branch());
    }

    @Test
    void personalRepository() {
        assertEquals("https://host/scm/~mmuster/spielwiese.git",
                CodeSource.parse("https://host/users/MMUSTER/repos/spielwiese/browse").orElseThrow().url());
    }

    @Test
    void cloneUrlWithBranchAndSubfolder() {
        CodeSource.Remote r = CodeSource.parse("https://bitbucket.firma.de/bitbucket/scm/sach/firma-sachvertrag.git"
                + "#migration/beispiel_branch:firma-sachvertrag-migration-batch").orElseThrow();
        assertEquals("https://bitbucket.firma.de/bitbucket/scm/sach/firma-sachvertrag.git", r.url());
        assertEquals("migration/beispiel_branch", r.branch());
        assertEquals("firma-sachvertrag-migration-batch", r.subfolder());
    }

    @Test
    void sshCloneUrlWithPort() {
        CodeSource.Remote plain = CodeSource.parse("ssh://git@bitbucket.firma.de:7999/sach/firma-sachvertrag.git").orElseThrow();
        assertEquals("ssh://git@bitbucket.firma.de:7999/sach/firma-sachvertrag.git", plain.url());
        assertEquals("", plain.branch());
        CodeSource.Remote r = CodeSource.parse("ssh://git@bitbucket.firma.de:7999/sach/firma-sachvertrag.git"
                + "#migration/beispiel_branch:firma-sachvertrag-migration-batch").orElseThrow();
        assertEquals("ssh://git@bitbucket.firma.de:7999/sach/firma-sachvertrag.git", r.url());
        assertEquals("migration/beispiel_branch", r.branch());
        assertEquals("firma-sachvertrag-migration-batch", r.subfolder());
        // the user "git" is no credential and must not be masked
        assertEquals(r.url(), CodeSource.mask(r.url()));
    }

    @Test
    void explainsSshFailures() {
        CodeSource.Remote r = CodeSource.parse("ssh://git@bitbucket.firma.de:7999/sach/firma-sachvertrag.git").orElseThrow();
        assertTrue(CodeSource.explain("Host key verification failed.\nfatal: Could not read from remote repository.", r)
                .contains("ssh -p 7999 git@bitbucket.firma.de"));
        assertTrue(CodeSource.explain("git@bitbucket.firma.de: Permission denied (publickey).\nfatal: Could not read "
                + "from remote repository.", r).contains("SSH-Schlüssel"));
    }

    @Test
    void localPathsAreNoRepositories() {
        assertFalse(CodeSource.isRemote("C:\\Firma-Develop\\firma-sachvertrag"));
        assertFalse(CodeSource.isRemote("../testprojekt"));
        assertTrue(CodeSource.parse("C:/x").isEmpty());
    }

    @Test
    void tokenNeverAppearsInTextOrCacheFolder() {
        String url = "https://mmuster:geheim123@host/scm/sach/repo.git";
        assertEquals("https://mmuster:***@host/scm/sach/repo.git", CodeSource.mask(url));
        CodeSource.Remote r = CodeSource.parse(url).orElseThrow();
        assertFalse(r.describe().contains("geheim123"));
        assertFalse(CodeSource.cacheFolder(r).toString().contains("geheim123"));
        // same repository with and without credentials shares the clone
        assertEquals(CodeSource.cacheFolder(r), CodeSource.cacheFolder(CodeSource.parse("https://host/scm/sach/repo.git").orElseThrow()));
    }

    @Test
    void differentBranchesGetDifferentClones() {
        assertNotEquals(CodeSource.cacheFolder(new CodeSource.Remote("https://h/scm/a/r.git", "develop", "")),
                CodeSource.cacheFolder(new CodeSource.Remote("https://h/scm/a/r.git", "main", "")));
    }

    @Test
    void explainsTheUsualFailures() {
        CodeSource.Remote r = new CodeSource.Remote("https://h/scm/a/r.git", "x", "");
        assertTrue(CodeSource.explain("fatal: Authentication failed for 'https://h/scm/a/r.git/'", r)
                .contains("Credential Manager"));
        assertTrue(CodeSource.explain("fatal: unable to access '…': SSL certificate problem: unable to get local issuer "
                + "certificate", r).contains("schannel"));
        assertTrue(CodeSource.explain("warning: Could not find remote branch x to clone.\nfatal: Remote branch x not "
                + "found in upstream origin", r).contains("Branch „x“"));
    }

    /** End to end with a local bare repository (file:// behaves like a server); skipped without git. */
    @Test
    void clonesAndUpdates(@TempDir Path tmp) throws Exception {
        assumeTrue(gitAvailable());
        Path work = tmp.resolve("work");
        Files.createDirectories(work.resolve("modul/src"));
        Files.writeString(work.resolve("modul/src/Risiko.java"), "class Risiko {}");
        run(work, "git", "init", "-q", "-b", "develop");
        run(work, "git", "-c", "user.email=t@t", "-c", "user.name=t", "add", ".");
        run(work, "git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-q", "-m", "eins");
        String home = System.getProperty("user.home");
        System.setProperty("user.home", tmp.resolve("home").toString());
        try {
            // file:// is no Bitbucket spelling – go through Remote directly
            CodeSource.Remote r = new CodeSource.Remote(work.toUri().toString(), "develop", "modul");
            List<String> log = new ArrayList<>();
            assertFalse(Files.exists(CodeSource.cacheFolder(r)));
            CodeSource.Resolved first = resolve(r, log);
            assertTrue(Files.isRegularFile(first.folder().resolve("src/Risiko.java")), log.toString());
            assertTrue(first.origin().contains("eins"));

            Files.writeString(work.resolve("modul/src/Deckung.java"), "class Deckung {}");
            run(work, "git", "-c", "user.email=t@t", "-c", "user.name=t", "add", ".");
            run(work, "git", "-c", "user.email=t@t", "-c", "user.name=t", "commit", "-q", "-m", "zwei");
            CodeSource.Resolved second = resolve(r, log);
            assertTrue(Files.isRegularFile(second.folder().resolve("src/Deckung.java")), log.toString());
            assertTrue(second.origin().contains("zwei"));
        } finally {
            System.setProperty("user.home", home);
        }
    }

    private static CodeSource.Resolved resolve(CodeSource.Remote r, List<String> log) throws Exception {
        return CodeSource.resolve(r, log::add);
    }

    private static boolean gitAvailable() {
        try {
            return new ProcessBuilder("git", "--version").start().waitFor(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            return false;
        }
    }

    private static void run(Path dir, String... command) throws Exception {
        Process p = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assertEquals(0, p.waitFor(), out);
    }
}
