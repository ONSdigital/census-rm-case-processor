package uk.gov.ons.census.caseprocessor.model.repository;

import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uk.gov.ons.ssdc.common.model.entity.MessageToSend;

public interface MessageToSendRepository extends JpaRepository<MessageToSend, UUID> {
  @Query(
      value = "SELECT * FROM casev3.message_to_send LIMIT :limit FOR UPDATE SKIP LOCKED",
      nativeQuery = true)
  Stream<MessageToSend> findMessagesToSend(@Param("limit") int limit);
}
