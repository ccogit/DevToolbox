package de.vers.neu.vertrag.to;

import de.vers.neu.common.AbstractTO;

import java.time.LocalDate;

/** Transferobjekt Vertrag (Ergebnis der Read-Phase). */
public class VertragTO extends AbstractTO {

    /** Produktschlüssel des Altbestands (ALT_VERTRAG.PRODUKT). */
    private String produkt;

    /** Tarif (ALT_VERTRAG.TARIF). */
    private String tarif;

    /** Vertragsbeginn (ALT_VERTRAG.BEGINN). */
    private LocalDate beginn;

    /** Zahlweise-Code J/H/V/M (ALT_VERTRAG.ZW), angereichert aus ALT_ZAHLWEG. */
    private String zahlweise;

    public String getProdukt() { return produkt; }
    public void setProdukt(String produkt) { this.produkt = produkt; }
    public String getTarif() { return tarif; }
    public void setTarif(String tarif) { this.tarif = tarif; }
    public LocalDate getBeginn() { return beginn; }
    public void setBeginn(LocalDate beginn) { this.beginn = beginn; }
    public String getZahlweise() { return zahlweise; }
    public void setZahlweise(String zahlweise) { this.zahlweise = zahlweise; }
}
