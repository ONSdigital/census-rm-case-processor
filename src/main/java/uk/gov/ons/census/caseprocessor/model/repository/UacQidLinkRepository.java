package uk.gov.ons.census.caseprocessor.model.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.ons.census.common.model.entity.UacQidLink;

public interface UacQidLinkRepository extends JpaRepository<UacQidLink, UUID> {
  Optional<UacQidLink> findByQid(String qid);

  boolean existsByQid(String qid);
}
