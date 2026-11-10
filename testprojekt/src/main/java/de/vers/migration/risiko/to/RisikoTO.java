package de.vers.migration.risiko.to;

import de.vers.migration.to.DeckungTO;

import java.math.BigDecimal;
import java.util.List;

/** Transferobjekt zur Schnittstelle Bestandsführung (enthält den Begriff im Namen). */
public class RisikoTO {

    private String risikoTyp;
    private BigDecimal versicherungssumme;
    private List<DeckungTO> deckungen;

    public String getRisikoTyp() { return risikoTyp; }
    public void setRisikoTyp(String t) { this.risikoTyp = t; }
    public BigDecimal getVersicherungssumme() { return versicherungssumme; }
    public void setVersicherungssumme(BigDecimal vs) { this.versicherungssumme = vs; }
    public List<DeckungTO> getDeckungen() { return deckungen; }
    public void setDeckungen(List<DeckungTO> d) { this.deckungen = d; }
}
