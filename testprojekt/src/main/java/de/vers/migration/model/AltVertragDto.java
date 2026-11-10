package de.vers.migration.model;

import java.time.LocalDate;

/** Vertragskopf aus dem Altbestand (Tabelle ALT_VERTRAG). */
public class AltVertragDto {

    private String vertragsnummer;
    private String produktschluessel;
    private String tarif;
    private String zahlweiseCode;
    private LocalDate beginn;
    private String status;

    public String getVertragsnummer() { return vertragsnummer; }
    public void setVertragsnummer(String v) { this.vertragsnummer = v; }
    public String getProduktschluessel() { return produktschluessel; }
    public void setProduktschluessel(String p) { this.produktschluessel = p; }
    public String getTarif() { return tarif; }
    public void setTarif(String tarif) { this.tarif = tarif; }
    public String getZahlweiseCode() { return zahlweiseCode; }
    public void setZahlweiseCode(String z) { this.zahlweiseCode = z; }
    public LocalDate getBeginn() { return beginn; }
    public void setBeginn(LocalDate beginn) { this.beginn = beginn; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
