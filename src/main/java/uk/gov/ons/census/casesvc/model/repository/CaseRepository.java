package uk.gov.ons.census.casesvc.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.ons.census.casesvc.model.entity.Case;

import java.util.Optional;
import java.util.UUID;

public interface CaseRepository extends JpaRepository<Case, Integer> {
  Optional<Case> findByCaseId(UUID caseId);
}
