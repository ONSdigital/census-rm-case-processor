package uk.gov.ons.census.casesvc.model.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.ons.census.casesvc.model.entity.Case;

public interface CaseRepository extends JpaRepository<Case, Integer> {
  Optional<Case> findByCaseId(UUID caseId);
}
