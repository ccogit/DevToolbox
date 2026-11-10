package de.vers.migration.risiko.writer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Einstellungen für den Export der migrierten Risiken.
 * Ist eine @Configuration, liegt aber im Verzeichnis "writer" und gehört deshalb zur Write-Phase.
 */
@Configuration
public class RisikoExportConfig {

    @Bean
    public String risikoExportVerzeichnis() {
        return "/data/export/risiko";
    }
}
