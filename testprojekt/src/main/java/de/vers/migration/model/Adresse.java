package de.vers.migration.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Lage des versicherten Gebäudes. */
@Embeddable
public class Adresse {

    @Column(name = "STRASSE")
    private String strasse;

    @Column(name = "PLZ", length = 5)
    private String plz;

    @Column(name = "ORT")
    private String ort;

    public String getStrasse() { return strasse; }
    public void setStrasse(String strasse) { this.strasse = strasse; }
    public String getPlz() { return plz; }
    public void setPlz(String plz) { this.plz = plz; }
    public String getOrt() { return ort; }
    public void setOrt(String ort) { this.ort = ort; }
}
