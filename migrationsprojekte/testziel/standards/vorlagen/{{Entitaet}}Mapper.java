package de.vers.neu.{{entitaet}}.mapping;

import de.vers.neu.{{entitaet}}.entity.{{Entitaet}}Entity;
import de.vers.neu.{{entitaet}}.to.{{Entitaet}}TO;

/** Mappt das Transferobjekt auf die Entity des Neusystems (Process-Phase). */
public class {{Entitaet}}Mapper {

    public {{Entitaet}}Entity mappe({{Entitaet}}TO to) {
        {{Entitaet}}Entity entity = new {{Entitaet}}Entity();
        entity.setVertragsnummer(to.getVertragsnummer());
        entity.setProdukt(to.getProdukt());
        entity.setBeginn(to.getBeginn());
        return entity;
    }
}
