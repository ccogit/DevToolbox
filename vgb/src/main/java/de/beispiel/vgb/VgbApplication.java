package de.beispiel.vgb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Noch nicht abgeschlossene Zielmigration; bewusst kein produktiver Batch-Job. */
@SpringBootApplication
public class VgbApplication {
    public static void main(String[] args) { SpringApplication.run(VgbApplication.class, args); }
}
