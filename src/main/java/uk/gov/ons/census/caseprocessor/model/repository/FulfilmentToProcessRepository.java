package uk.gov.ons.census.caseprocessor.model.repository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uk.gov.ons.ssdc.common.model.entity.FulfilmentToProcess;

public interface FulfilmentToProcessRepository extends JpaRepository<FulfilmentToProcess, UUID> {

  @Query(
      value =
          "SELECT * FROM casev3.fulfilment_to_process where batch_id is not null and batch_quantity is not null LIMIT :limit FOR UPDATE SKIP LOCKED",
      nativeQuery = true)
  Stream<FulfilmentToProcess> findChunkToProcess(@Param("limit") int limit);

  @Query("SELECT DISTINCT f.exportFileTemplate.packCode FROM FulfilmentToProcess f")
  List<String> findDistinctPackCode();

  boolean existsByMessageId(UUID messageId);
}
