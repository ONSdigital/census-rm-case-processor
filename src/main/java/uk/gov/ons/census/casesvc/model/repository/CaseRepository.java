package uk.gov.ons.census.casesvc.model.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uk.gov.ons.census.casesvc.model.entity.Case;

public interface CaseRepository extends JpaRepository<Case, UUID> {
  Optional<Case> findByCaseId(UUID caseId);

  @Modifying
  @Query("update Case c set c.receiptReceived = true where c.caseId = :caseId")
  void setReceiptReceived(@Param("caseId") UUID caseId);
}
