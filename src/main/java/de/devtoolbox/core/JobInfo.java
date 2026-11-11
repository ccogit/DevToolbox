package de.devtoolbox.core;

/**
 * The uniform description of a job.
 *
 * @param goal          "Ziel": what the job helps the user with (teaser, 1-2 sentences)
 * @param inputs        "Primäre Inputs"
 * @param prerequisites "Voraussetzungen"
 * @param output        "Ergebnis": files to upload and what Copilot returns
 */
public record JobInfo(String goal, String inputs, String prerequisites, String output) {
}
