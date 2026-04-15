package uk.gov.ons.census.caseprocessor.model.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uk.gov.ons.ssdc.common.model.entity.ClusterLeader;

public interface ClusterLeaderRepository extends JpaRepository<ClusterLeader, UUID> {
  @Query(
      value = "SELECT * FROM casev3.cluster_leader WHERE id =  :id FOR UPDATE SKIP LOCKED",
      nativeQuery = true)
  Optional<ClusterLeader> getClusterLeaderAndLockById(@Param("id") UUID id);
}
