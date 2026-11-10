package de.vers.migration.config;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

/** Schreibt Start/Ende eines Jobs ins Protokoll. Ohne Suchbegriff - wird als Referenz aufgenommen. */
@Component
public class JobProtokollListener implements JobExecutionListener {

    @Override
    public void beforeJob(JobExecution execution) {
        System.out.println("Start: " + execution.getJobInstance().getJobName());
    }

    @Override
    public void afterJob(JobExecution execution) {
        System.out.println("Ende: " + execution.getStatus());
    }
}
