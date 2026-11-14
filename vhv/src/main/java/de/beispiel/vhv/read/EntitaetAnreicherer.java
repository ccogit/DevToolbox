package de.beispiel.vhv.read;
import de.beispiel.vhv.modell.Datenstand;
import de.beispiel.vhv.modell.EntitaetTyp;
import de.beispiel.vhv.modell.QuellDatensatz;

public interface EntitaetAnreicherer {
    EntitaetTyp entitaet();
    Datenstand datenstand();
    void anreichern(QuellDatensatz datensatz);
}
