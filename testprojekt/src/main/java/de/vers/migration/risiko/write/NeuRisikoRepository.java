package de.vers.migration.risiko.write;

import de.vers.migration.risiko.model.NeuRisikoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NeuRisikoRepository extends JpaRepository<NeuRisikoEntity, String> {
}
