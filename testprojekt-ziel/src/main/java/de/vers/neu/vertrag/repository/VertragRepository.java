package de.vers.neu.vertrag.repository;

import de.vers.neu.vertrag.po.VertragPO;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Liest die Altdaten der Entität Vertrag (ein Repository je Entität). */
public interface VertragRepository extends JpaRepository<VertragPO, String> {

    List<VertragPO> findByProdukt(String produkt);
}
