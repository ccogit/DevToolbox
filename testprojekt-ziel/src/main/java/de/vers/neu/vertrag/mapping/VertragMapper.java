package de.vers.neu.vertrag.mapping;

import de.vers.neu.vertrag.entity.VertragEntity;
import de.vers.neu.vertrag.to.VertragTO;

/** Mappt das Transferobjekt auf die Entity des Neusystems (Process-Phase). */
public class VertragMapper {

    public VertragEntity mappe(VertragTO to) {
        VertragEntity entity = new VertragEntity();
        entity.setVertragsnummer(to.getVertragsnummer());
        entity.setProdukt(to.getProdukt());
        entity.setBeginn(to.getBeginn());
        return entity;
    }
}
