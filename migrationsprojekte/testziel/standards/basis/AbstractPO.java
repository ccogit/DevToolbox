package de.vers.neu.common;

/** Basisklasse aller Persistenzobjekte der Altdatenbank. */
public abstract class AbstractPO {

    /** Name der Alt-Tabelle, aus der das PO gelesen wird. */
    public abstract String tabelle();
}
