package de.devtoolbox;

import de.devtoolbox.core.Json;
import de.devtoolbox.core.Keys;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Parses the two supported CLI forms without mixing file/argument precedence into job execution. */
record CommandLine(String jobId, Map<String, String> fileValues, Map<String, String> overrides) {

    static CommandLine parse(String[] args) throws IOException {
        if (args.length == 0) {
            throw new IllegalArgumentException("Ein Job oder eine Konfigurationsdatei ist erforderlich.");
        }
        Map<String, String> fromFile = new HashMap<>();
        String jobId = args[0];
        if (jobId.toLowerCase(Locale.ROOT).endsWith(".json")) {
            fromFile.putAll(Json.parse(Files.readString(Path.of(jobId))));
            jobId = fromFile.remove("job");
        }

        Map<String, String> overrides = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            int separator = args[i].indexOf('=');
            if (separator > 0) {
                overrides.put(args[i].substring(0, separator), args[i].substring(separator + 1));
            }
        }
        return new CommandLine(jobId, Map.copyOf(fromFile), Map.copyOf(overrides));
    }

    /** Command-line values override the JSON file's project selection. */
    String projectId() {
        return overrides.getOrDefault(Keys.PROJECT, fileValues.getOrDefault(Keys.PROJECT, ""));
    }
}
