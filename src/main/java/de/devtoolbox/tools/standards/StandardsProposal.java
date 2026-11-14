package de.devtoolbox.tools.standards;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Picks the code source that offers the richest standards, without writing anything: the basis for proposing
 * standards with one click instead of asking for source and example entity.
 */
public final class StandardsProposal {

    /** The proposed source and what generating from it would yield. */
    public record Proposal(Path source, StandardsTool.Preview preview) {
    }

    private StandardsProposal() {
    }

    /**
     * The best of {@code sources}; with equal scores the earlier source wins, so the order of the sources is their
     * priority. Missing folders and sources without recognisable entities are skipped.
     */
    public static Optional<Proposal> best(Collection<Path> sources) {
        Proposal best = null;
        int bestScore = -1;
        Set<Path> seen = new HashSet<>();
        for (Path source : sources) {
            if (source == null || !Files.isDirectory(source) || !seen.add(source.toAbsolutePath().normalize())) {
                continue;
            }
            try {
                StandardsTool.Preview preview = StandardsTool.preview(source, "");
                if (score(preview) > bestScore) {
                    bestScore = score(preview);
                    best = new Proposal(source, preview);
                }
            } catch (Exception e) {
                // an unsuitable source is no proposal; the manual choice remains available
            }
        }
        return Optional.ofNullable(best);
    }

    /** Templates count most, then separately recognised stock/suspense variants, then shared base classes. */
    static int score(StandardsTool.Preview preview) {
        return preview.templates().size() * 3 + preview.variants().size() * 2 + preview.bases().size();
    }
}
