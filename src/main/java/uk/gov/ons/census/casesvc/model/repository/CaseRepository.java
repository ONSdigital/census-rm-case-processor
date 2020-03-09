package uk.gov.ons.census.casesvc.model.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uk.gov.ons.census.casesvc.model.entity.Case;

public interface CaseRepository extends JpaRepository<Case, UUID> {
  Optional<Case> findByCaseRef(Long caseRef);

  @Query(
      value = "SELECT * FROM casev2.cases WHERE case_id =  :caseId FOR UPDATE SKIP LOCKED",
      nativeQuery = true)
  Optional<Case> getCaseAndLockByCaseId(@Param("caseId") UUID caseId);
}
