package de.vers.migration.to;

import de.vers.migration.util.BetragFormatierer;

import java.math.BigDecimal;

/** Ebene 2 – ohne Suchbegriff, wird über das Transferobjekt der Ebene 1 gefunden. */
public class DeckungTO {

    private String deckungsart;
    private BigDecimal haftungssumme;
    private SelbstbeteiligungTO selbstbeteiligung;

    public String anzeige() {
        return deckungsart + " " + BetragFormatierer.formatiere(haftungssumme);
    }

    public String getDeckungsart() { return deckungsart; }
    public void setDeckungsart(String a) { this.deckungsart = a; }
    public BigDecimal getHaftungssumme() { return haftungssumme; }
    public void setHaftungssumme(BigDecimal h) { this.haftungssumme = h; }
    public SelbstbeteiligungTO getSelbstbeteiligung() { return selbstbeteiligung; }
    public void setSelbstbeteiligung(SelbstbeteiligungTO sb) { this.selbstbeteiligung = sb; }
}
