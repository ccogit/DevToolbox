package de.devtoolbox.core;

import de.devtoolbox.jobs.diagnosis.ProjectCheckJob;
import de.devtoolbox.jobs.implementation.ImplementationJob;
import de.devtoolbox.jobs.mappingdoc.MappingDocJob;
import de.devtoolbox.jobs.phasespec.PhaseSpecJob;
import de.devtoolbox.jobs.speccheck.SpecCheckJob;

import java.util.List;
import java.util.Optional;

/** The single place where jobs are registered. New job = one line here. */
public final class JobRegistry {

    private static final List<Job> JOBS = List.of(
            new ProjectCheckJob(),
            new PhaseSpecJob(),
            new ImplementationJob(),
            new MappingDocJob(),
            new SpecCheckJob()
    );

    /** Found by id (CLI, JSON files) but not offered in the UI: the specification fixed to one phase. */
    private static final List<Job> HIDDEN = List.of(
            PhaseSpecJob.forPhase("READ"),
            PhaseSpecJob.forPhase("PROCESS"),
            PhaseSpecJob.forPhase("WRITE"));

    private JobRegistry() {
    }

    /** The jobs the UI and the help offer. */
    public static List<Job> all() {
        return JOBS;
    }

    /** Jobs that can be run by id but are not in {@link #all()}. */
    public static List<Job> hidden() {
        return HIDDEN;
    }

    /** Any job by id, including the hidden ones. */
    public static Optional<Job> find(String id) {
        return java.util.stream.Stream.concat(JOBS.stream(), HIDDEN.stream())
                .filter(j -> j.id().equals(id)).findFirst();
    }
}
