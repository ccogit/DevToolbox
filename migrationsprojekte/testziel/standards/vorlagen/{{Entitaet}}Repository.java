package de.vers.neu.{{entitaet}}.repository;

import de.vers.neu.{{entitaet}}.po.{{Entitaet}}PO;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Liest die Altdaten der Entität {{Entitaet}} (ein Repository je Entität). */
public interface {{Entitaet}}Repository extends JpaRepository<{{Entitaet}}PO, String> {

    List<{{Entitaet}}PO> findByProdukt(String produkt);
}
