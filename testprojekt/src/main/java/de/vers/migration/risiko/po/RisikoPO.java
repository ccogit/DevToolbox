package de.vers.migration.risiko.po;

import java.math.BigDecimal;

/** Persistenzobjekt der Alt-Tabelle ALT_RISIKO (Altsystem-Framework ohne JPA). */
public class RisikoPO {

    public static final String TABELLE = "ALT_RISIKO";

    private String vnr;
    private String risart;
    private String oberris;
    private BigDecimal vs;

    public String getVnr() { return vnr; }
    public void setVnr(String vnr) { this.vnr = vnr; }
    public String getRisart() { return risart; }
    public void setRisart(String risart) { this.risart = risart; }
    public String getOberris() { return oberris; }
    public void setOberris(String oberris) { this.oberris = oberris; }
    public BigDecimal getVs() { return vs; }
    public void setVs(BigDecimal vs) { this.vs = vs; }
}
