package de.vers.neu.vertrag.po;

import de.vers.neu.common.AbstractPO;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Persistenzobjekt für ALT_VERTRAG - Felder heißen wie die Spalten. */
public class VertragPO extends AbstractPO {

    public static final String TABELLE = "ALT_VERTRAG";

    private String vnr;        // CHAR(12)
    private String produkt;    // CHAR(6)
    private String tarif;      // CHAR(6)
    private String zw;         // CHAR(1)
    private LocalDate beginn;  // DATE
    private BigDecimal jahresbeitrag; // DECIMAL(11,2)

    @Override
    public String tabelle() { return TABELLE; }

    public String getVnr() { return vnr; }
    public void setVnr(String vnr) { this.vnr = vnr; }
    public String getProdukt() { return produkt; }
    public void setProdukt(String produkt) { this.produkt = produkt; }
    public String getTarif() { return tarif; }
    public void setTarif(String tarif) { this.tarif = tarif; }
    public String getZw() { return zw; }
    public void setZw(String zw) { this.zw = zw; }
    public LocalDate getBeginn() { return beginn; }
    public void setBeginn(LocalDate beginn) { this.beginn = beginn; }
    public BigDecimal getJahresbeitrag() { return jahresbeitrag; }
    public void setJahresbeitrag(BigDecimal b) { this.jahresbeitrag = b; }
}
