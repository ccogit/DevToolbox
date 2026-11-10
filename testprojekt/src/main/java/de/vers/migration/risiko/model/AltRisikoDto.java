package de.vers.migration.risiko.model;

import de.vers.migration.model.Adresse;
import de.vers.migration.model.AltVertragDto;

import java.math.BigDecimal;

/** Eine Zeile aus ALT_RISIKO inkl. Vertragskopf. */
public class AltRisikoDto {

    private AltVertragDto vertrag;
    private String risikoart;          // Altcode, z. B. "FE", "LW", "ST", "EL"
    private String oberrisikoCode;
    private BigDecimal versicherungssumme;
    private BigDecimal beitrag;
    private String bauartklasse;
    private Adresse lage;

    public AltVertragDto getVertrag() { return vertrag; }
    public void setVertrag(AltVertragDto vertrag) { this.vertrag = vertrag; }
    public String getRisikoart() { return risikoart; }
    public void setRisikoart(String risikoart) { this.risikoart = risikoart; }
    public String getOberrisikoCode() { return oberrisikoCode; }
    public void setOberrisikoCode(String code) { this.oberrisikoCode = code; }
    public BigDecimal getVersicherungssumme() { return versicherungssumme; }
    public void setVersicherungssumme(BigDecimal summe) { this.versicherungssumme = summe; }
    public BigDecimal getBeitrag() { return beitrag; }
    public void setBeitrag(BigDecimal beitrag) { this.beitrag = beitrag; }
    public String getBauartklasse() { return bauartklasse; }
    public void setBauartklasse(String bak) { this.bauartklasse = bak; }
    public Adresse getLage() { return lage; }
    public void setLage(Adresse lage) { this.lage = lage; }
}
