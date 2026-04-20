package uk.gov.ons.census.caseprocessor.schedule;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.caseprocessor.model.repository.FulfilmentToProcessRepository;

@Component
public class FulfilmentProcessor {
  private static final Logger log = LoggerFactory.getLogger(FulfilmentProcessor.class);
  private final JdbcTemplate jdbcTemplate;
  private final FulfilmentToProcessRepository fulfilmentToProcessRepository;

  public FulfilmentProcessor(
      JdbcTemplate jdbcTemplate, FulfilmentToProcessRepository fulfilmentToProcessRepository) {
    this.jdbcTemplate = jdbcTemplate;
    this.fulfilmentToProcessRepository = fulfilmentToProcessRepository;
  }

  @Transactional
  public void addFulfilmentBatchIdAndQuantity() {
    List<String> packCodes = fulfilmentToProcessRepository.findDistinctPackCode();

    packCodes.forEach(
        packCode -> {
          UUID batchId = UUID.randomUUID();
          log.atInfo()
              .setMessage("Fulfilments triggered")
              .addKeyValue("batch_id", batchId)
              .addKeyValue("pack_code", packCode)
              .log();

          jdbcTemplate.update(
              "UPDATE cases.fulfilment_to_process "
                  + "SET batch_quantity = (SELECT COUNT(*) FROM cases.fulfilment_to_process "
                  + "WHERE export_file_template_pack_code = ?), batch_id = ? WHERE export_file_template_pack_code = ?",
              packCode,
              batchId,
              packCode);
        });
  }
}
