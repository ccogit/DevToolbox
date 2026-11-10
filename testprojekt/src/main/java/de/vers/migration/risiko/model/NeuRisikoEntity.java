package de.vers.migration.risiko.model;

import de.vers.migration.common.Zahlweise;
import de.vers.migration.model.Adresse;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** Risiko im Neusystem (Tabelle RISIKO). */
@Entity
@Table(name = "RISIKO")
public class NeuRisikoEntity {

    @Id
    @Column(name = "RISIKO_ID")
    private String risikoId;

    @Column(name = "VERTRAG_NR", nullable = false)
    private String vertragsnummer;

    @Column(name = "RISIKO_TYP", nullable = false)
    private String risikoTyp;

    @Column(name = "OBERRISIKO_TYP")
    private String oberrisikoTyp;

    @Column(name = "VS", precision = 15, scale = 2)
    private BigDecimal versicherungssumme;

    @Column(name = "RATENBEITRAG", precision = 15, scale = 2)
    private BigDecimal ratenbeitrag;

    @Enumerated(EnumType.STRING)
    @Column(name = "ZAHLWEISE")
    private Zahlweise zahlweise;

    @Embedded
    private Adresse lage;

    public String getRisikoId() { return risikoId; }
    public void setRisikoId(String risikoId) { this.risikoId = risikoId; }
    public String getVertragsnummer() { return vertragsnummer; }
    public void setVertragsnummer(String v) { this.vertragsnummer = v; }
    public String getRisikoTyp() { return risikoTyp; }
    public void setRisikoTyp(String typ) { this.risikoTyp = typ; }
    public String getOberrisikoTyp() { return oberrisikoTyp; }
    public void setOberrisikoTyp(String typ) { this.oberrisikoTyp = typ; }
    public BigDecimal getVersicherungssumme() { return versicherungssumme; }
    public void setVersicherungssumme(BigDecimal vs) { this.versicherungssumme = vs; }
    public BigDecimal getRatenbeitrag() { return ratenbeitrag; }
    public void setRatenbeitrag(BigDecimal b) { this.ratenbeitrag = b; }
    public Zahlweise getZahlweise() { return zahlweise; }
    public void setZahlweise(Zahlweise z) { this.zahlweise = z; }
    public Adresse getLage() { return lage; }
    public void setLage(Adresse lage) { this.lage = lage; }
}
