/*
 * Copyright (c) 2019 Versicherung AG. Alle Rechte vorbehalten.
 * Dieser Lizenzkopf wird im Kompaktmodus entfernt.
 */
package de.vers.migration.config;

import de.vers.migration.risiko.model.AltRisikoDto;
import de.vers.migration.risiko.model.NeuRisikoEntity;
import de.vers.migration.risiko.process.RisikoItemProcessor;
import de.vers.migration.risiko.read.AltRisikoItemReader;
import de.vers.migration.risiko.write.RisikoItemWriter;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/** Job zur Migration der Risiken aus dem Altbestand Wohngebäude (WG2010). */
@Configuration
public class RisikoMigrationJobConfig {

    @Bean
    public Job risikoMigrationJob(JobRepository repository, Step risikoStep, JobProtokollListener listener) {
        return new JobBuilder("risikoMigrationJob", repository)
                .listener(listener)
                .start(risikoStep)
                .build();
    }

    @Bean
    public Step risikoStep(JobRepository repository, PlatformTransactionManager tx,
                           AltRisikoItemReader reader, RisikoItemProcessor processor, RisikoItemWriter writer) {
        return new StepBuilder("risikoStep", repository)
                .<AltRisikoDto, NeuRisikoEntity>chunk(500, tx)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .faultTolerant()
                .skipLimit(100)
                .skip(IllegalArgumentException.class)
                .build();
    }
}
