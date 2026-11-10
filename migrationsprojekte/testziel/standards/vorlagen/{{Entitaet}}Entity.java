package de.vers.neu.{{entitaet}}.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** {{Entitaet}} im Neusystem (Tabelle {{ENTITAET}}). */
@Entity
@Table(name = "{{ENTITAET}}")
public class {{Entitaet}}Entity {

    @Id
    @Column(name = "{{ENTITAET}}_NR", length = 20)
    private String vertragsnummer;

    @Column(name = "PRODUKT", nullable = false)
    private String produkt;

    @Column(name = "BEGINN", nullable = false)
    private LocalDate beginn;

    public String getVertragsnummer() { return vertragsnummer; }
    public void setVertragsnummer(String v) { this.vertragsnummer = v; }
    public String getProdukt() { return produkt; }
    public void setProdukt(String produkt) { this.produkt = produkt; }
    public LocalDate getBeginn() { return beginn; }
    public void setBeginn(LocalDate beginn) { this.beginn = beginn; }
}
