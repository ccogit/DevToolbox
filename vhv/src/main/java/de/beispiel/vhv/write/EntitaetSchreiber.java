package de.beispiel.vhv.write;

import org.springframework.jdbc.core.JdbcTemplate;
import de.beispiel.vhv.modell.*;

public interface EntitaetSchreiber {
    EntitaetTyp entitaet();
    void schreibe(ZielDatensatz item, JdbcTemplate jdbc);
}
