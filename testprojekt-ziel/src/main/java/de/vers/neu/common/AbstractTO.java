package de.vers.neu.common;

import java.io.Serializable;

/** Basisklasse aller Transferobjekte. */
public abstract class AbstractTO implements Serializable {

    private String vertragsnummer;

    public String getVertragsnummer() { return vertragsnummer; }
    public void setVertragsnummer(String vertragsnummer) { this.vertragsnummer = vertragsnummer; }
}
