package de.vers.neu.vertrag.writer;

import de.vers.neu.vertrag.entity.VertragEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VertragRepository extends JpaRepository<VertragEntity, String> {
}
