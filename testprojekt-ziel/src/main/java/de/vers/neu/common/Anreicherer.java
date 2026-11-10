package de.vers.neu.common;

/** Ergänzt ein gelesenes Transferobjekt um Daten aus weiteren Alt-Tabellen. */
public interface Anreicherer<T extends AbstractTO> {

    void reichereAn(T to);
}
