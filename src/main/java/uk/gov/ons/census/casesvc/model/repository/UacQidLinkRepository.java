package uk.gov.ons.census.casesvc.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;

import java.util.Optional;
import java.util.UUID;

public interface UacQidLinkRepository extends JpaRepository<UacQidLink, UUID> {
  Optional<UacQidLink> findByQid(String qid);
}
