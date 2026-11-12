package de.devtoolbox.core;

import de.devtoolbox.jobs.diagnosis.ProjectCheckJob;
import de.devtoolbox.jobs.implementation.ImplementationJob;
import de.devtoolbox.jobs.mappingdoc.MappingDocJob;
import de.devtoolbox.jobs.phasespec.PhaseSpecJob;

import java.util.List;
import java.util.Optional;

/** The single place where jobs are registered. New job = one line here. */
public final class JobRegistry {

    private static final List<Job> JOBS = List.of(
            new ProjectCheckJob(),
            new PhaseSpecJob(),
            new ImplementationJob(),
            new MappingDocJob()
    );

    private JobRegistry() {
    }

    public static List<Job> all() {
        return JOBS;
    }

    public static Optional<Job> find(String id) {
        return JOBS.stream().filter(j -> j.id().equals(id)).findFirst();
    }
}
