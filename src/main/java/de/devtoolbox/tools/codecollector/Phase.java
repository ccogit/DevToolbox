package de.devtoolbox.tools.codecollector;

/** Sections of the output, ordered along the batch flow. Only the three batch phases can be deselected. */
enum Phase {
    READ("Read", "Read-Phase – Einlesen", true),
    PROCESS("Process", "Process-Phase – Mapping/Verarbeitung", true),
    WRITE("Write", "Write-Phase – Schreiben", true),
    PERSISTENCE("Datenzugriff", "Datenzugriff – Repositories/DAOs/SQL-Mapper", false),
    CONFIG("Konfiguration", "Job-/Step-Konfiguration und Properties", false),
    MODEL("Modell", "Datenmodell – Entities/POs/TOs/DTOs/Schemas", false),
    OTHER("Sonstiges", "Sonstiges", false),
    TEST("Test", "Tests", false);

    final String shortName;
    final String title;
    /** Read/Process/Write; the others are supporting categories that are always kept. */
    final boolean batchPhase;

    Phase(String shortName, String title, boolean batchPhase) {
        this.shortName = shortName;
        this.title = title;
        this.batchPhase = batchPhase;
    }
}
